/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.consensus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;

import io.xdag.DagKernel;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.core.BlockHeader;
import io.xdag.core.Dagchain;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.PowManager;
import io.xdag.core.SyncManager;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionExecutor;
import io.xdag.core.TransactionResult;
import io.xdag.core.XAmount;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.RandomX;
import io.xdag.crypto.RandomXMemory;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.net.message.Message;
import io.xdag.net.message.consensus.EpochMessage;
import io.xdag.utils.ArrayUtils;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements Xdagj POW engine based on single-thread event model. States are
 * maintained in the engine and are updated only by the event loop.
 * <p>
 * Asides the main event hub, there are complementary threads:
 * <code>timer</code> and <code>broadcaster</code>. The <code>timer</code>
 * thread emits a TIMEOUT event when the internal timer times out. The
 * <code>broadcaster</code> thread is responsible for relaying POW messages to
 * peers.
 * <p>
 * The POW engine may be one of the following status:
 * <ul>
 * <li><code>STOPPED</code>: not started</li>
 * <li><code>SYNCING</code>: waiting for syncing</li>
 * <li><code>RUNNING</code>: working</li>
 * </ul>
 * <p>
 * It is also a state machine; the possible states include:
 * <ul>
 * <li><code>TIMEOUT</code>: epoch time out</li>
 * <li><code>EPOCH</code>: epoch</li>
 * </ul>
 */
@Slf4j
@Getter
@Setter
public class XdagPow implements PowManager {

    protected DagKernel kernel;
    protected Config config;

    protected Dagchain dagchain;

    protected ChannelManager channelManager;
    protected PendingManager pendingManager;
    protected SyncManager syncManager;

    protected KeyPair coinbase;

    protected Timer timer;
    protected Broadcaster broadcaster;
    protected BlockingQueue<Event> events = new LinkedBlockingQueue<>();

    protected Status status;
    protected State state;

    private final Object epochLock = new Object();

    protected RandomX randomx;

    protected List<Channel> activeChannels;
    protected volatile long lastUpdate;
    protected volatile long lastEpochEndtime;

    public XdagPow(DagKernel kernel) {
        this.kernel = kernel;
        this.config = kernel.getConfig();

        this.dagchain = kernel.getDagchain();
        this.channelManager = kernel.getChannelManager();
        this.pendingManager = kernel.getPendingManager();
        this.syncManager = kernel.getXdagSync();
        this.coinbase = kernel.getCoinbase();
        this.randomx = kernel.getRandomx();

        this.timer = new Timer();
        this.broadcaster = new Broadcaster();

        this.status = Status.STOPPED;
        this.state = State.EPOCH;
        this.lastEpochEndtime = 0L;
    }

    /**
     * Pause the pow manager, and do synchronization.
     */
    protected void sync(long begin, long current, long target, Channel channel) {
        if (status == Status.RUNNING) {
            // change status
            status = Status.SYNCING;

            clearTimerAndEvents();

            // start syncing
            syncManager.start(begin, current, target, channel);

            // restore status if not stopped
            if (status != Status.STOPPED) {
                status = Status.RUNNING;

                clearTimerAndEvents();
                lastEpochEndtime = dagchain.getLatestMainBlock().getTimestamp();
                enterEpoch();
            }
        }
    }

    /**
     * Main loop that processes all the POW events.
     */
    protected void eventLoop() {
        while (!Thread.currentThread().isInterrupted() && status != Status.STOPPED) {
            try {
                Event ev = events.take();
                if (status != Status.RUNNING) {
                    continue;
                }

                switch (ev.getType()) {
                case STOP:
                    return;
                case TIMEOUT:
                    onTimeout();
                    break;
                case EPOCH_BLOCK:
                    onEpoch(ev);
                    break;
                default:
                    break;
                }
            } catch (InterruptedException e) {
                log.info("PowManager got interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Unexpected exception in event loop", e);
            }
        }
    }

    @Override
    public void start() {
        if (status == Status.STOPPED) {
            status = Status.RUNNING;
            timer.start();
            broadcaster.start();
            log.info("Xdagj POW manager started");

            enterEpoch();
            eventLoop();

            log.info("Xdagj POW manager stopped");
        }
    }

    @Override
    public void stop() {
        if (status != Status.STOPPED) {
            // interrupt sync
            if (status == Status.SYNCING) {
                syncManager.stop();
            }

            timer.stop();

            status = Status.STOPPED;
            Event ev = new Event(Event.Type.STOP);
            if (!events.offer(ev)) {
                log.error("Failed to add an event to message queue: ev = {}", ev);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public void onEpoch(Event event) {
        EpochMessage epochMessage = event.getData();
        Channel channel = event.getChannel();

        synchronized (epochLock) {
            MainBlock remoteMainBlock = epochMessage.getMainBlock();
            long localNumber = dagchain.getLatestMainBlockNumber();
            long remoteNumber = remoteMainBlock.getNumber();

            long diff = remoteNumber - localNumber;

            if (diff > 1) { // for sync
                log.trace("onEpoch diff:{}, remote:{}, local:{}, start sync:{}, peer:{}", diff, remoteNumber,
                        localNumber, remoteNumber, channel.getRemotePeer());
                long syncBegin = dagchain.getLatestMainBlockNumber();
                long syncCurrent = syncBegin;
                long syncTarget = remoteNumber;
                sync(syncBegin, syncCurrent, syncTarget, channel);
            } else if (diff >= 0) { // reorg main block in 1 epoch
                log.trace("onEpoch diff:{}, remote:{}, local:{}, check and import {}, peer:{}", diff, remoteNumber,
                        localNumber, remoteNumber, channel.getRemotePeer());
                if (remoteMainBlock.getHeader().validate() && remoteMainBlock.getHeader().checkProofOfWork()) {
                    // find remote main block parent state
                    AccountState parentAccountState = dagchain.getAccountState(remoteMainBlock.getParentHash(),
                            remoteMainBlock.getNumber() - 1);
                    BlockState parentBlockState = dagchain.getBlockState(remoteMainBlock.getParentHash(),
                            remoteMainBlock.getNumber() - 1);

                    if (parentBlockState == null || parentAccountState == null
                            || parentBlockState.getMainBlockByHash(remoteMainBlock.getParentHash()) == null) {
                        // if no parent main block, sync parent
                        long syncBegin = remoteMainBlock.getNumber() - 1;
                        long syncCurrent = syncBegin;
                        long syncTarget = remoteMainBlock.getNumber();
                        sync(syncBegin, syncCurrent, syncTarget, channel);
                    } else {
                        MainBlock mb = dagchain.getMainBlockByNumber(remoteNumber);
                        if (mb != null) {
                            BigInteger localHash = new BigInteger(1, mb.getHash());
                            BigInteger remoteHash = new BigInteger(1, remoteMainBlock.getHash());
                            if (localHash.compareTo(remoteHash) < 0) {
                                log.warn("onEpoch reject remote:{}, hash:{}, local:{}, hash:{}, peer:{}.",
                                        remoteNumber, Bytes.wrap(remoteMainBlock.getHash()).toHexString(),
                                        localNumber, Bytes.wrap(mb.getHash()).toHexString(),
                                        channel.getRemotePeer());
                                return;
                            }
                            if(dagchain.getLatestMainBlockNumber() > remoteNumber) {
                                log.warn("onEpoch reorg remote:{}, hash:{}, local:{}, hash:{}, peer:{}.",
                                        remoteNumber, Bytes.wrap(remoteMainBlock.getHash()).toHexString(),
                                        localNumber, Bytes.wrap(mb.getHash()).toHexString(),
                                        channel.getRemotePeer());
                            }
                        }
                        dagchain.importBlock(remoteMainBlock, parentAccountState.clone(), parentBlockState.clone());
                    }
                }
            } else { // reject main block number before current 1 epoch
                log.warn("onEpoch reject remote:{}, hash:{}, local:{}, peer:{}.",
                        remoteNumber, Bytes.wrap(remoteMainBlock.getHash()).toHexString(),
                        localNumber,
                        channel.getRemotePeer());
            }
        }
    }

    @Override
    public void onMessage(Channel channel, Message msg) {
        switch (msg.getCode()) {
            case EPOCH_BLOCK: {
                EpochMessage epochMessage = (EpochMessage) msg;
                MainBlock remoteMainBlock = epochMessage.getMainBlock();
                MainBlock currentRemoteMainBlock = channel.getRemotePeer().getLatestMainBlock();
                if(currentRemoteMainBlock != null && remoteMainBlock.getNumber() > currentRemoteMainBlock.getNumber()) {
                    // update peer latest main block
                    channel.getRemotePeer().setLatestMainBlock(remoteMainBlock);
                }
                events.add(new Event(Event.Type.EPOCH_BLOCK, new EpochMessage(Arrays.copyOf(epochMessage.getBody(), epochMessage.getBody().length)), channel));
                break;
            }
            default: {
                break;
            }
        }
    }

    public void newBlock() {
        log.debug("Start new block generate....");
        long sendTime = XdagTime.getMainTime();
        //resetTimeout(sendTime);

        if (randomx != null && randomx.isRandomxFork(XdagTime.getEpoch(sendTime))) {
            if (randomx.getRandomXPoolMemIndex() == 0) {
                randomx.setRandomXPoolMemIndex((randomx.getRandomXHashEpochIndex() - 1) & 1);
            }

            if (randomx.getRandomXPoolMemIndex() == -1) {

                long switchTime0 = randomx.getGlobalMemory()[0] == null ? 0 : randomx.getGlobalMemory()[0].getSwitchTime();
                long switchTime1 = randomx.getGlobalMemory()[1] == null ? 0 : randomx.getGlobalMemory()[1].getSwitchTime();

                if (switchTime0 > switchTime1) {
                    if (XdagTime.getEpoch(sendTime) > switchTime0) {
                        randomx.setRandomXPoolMemIndex(2);
                    } else {
                        randomx.setRandomXPoolMemIndex(1);
                    }
                } else {
                    if (XdagTime.getEpoch(sendTime) > switchTime1) {
                        randomx.setRandomXPoolMemIndex(1);
                    } else {
                        randomx.setRandomXPoolMemIndex(2);
                    }
                }
            }

            long randomXMemIndex = randomx.getRandomXPoolMemIndex() + 1;
            RandomXMemory memory = randomx.getGlobalMemory()[(int) (randomXMemIndex) & 1];

            if ((XdagTime.getEpoch(XdagTime.getMainTime()) >= memory.getSwitchTime()) && (memory.getIsSwitched() == 0)) {
                randomx.setRandomXPoolMemIndex(randomx.getRandomXPoolMemIndex() + 1);
                memory.setIsSwitched(1);
            }
            //generateBlock.set(prepareMainBlock());
        }
    }

    /**
     * Enter the EPOCH state
     */
    protected void enterEpoch() {
        state = State.EPOCH;

        // update channels
        updateChannels();

        clearTimerAndEvents();

        long currentEpochEndTime = resetEpoch();
        if(this.lastEpochEndtime == currentEpochEndTime) {
            log.warn("jump epoch lastEpochEndtime={}, currentEpochEndTime={}.", lastEpochEndtime, currentEpochEndTime);
            return;
        }

        if(CollectionUtils.isEmpty(activeChannels) && dagchain.getLatestMainBlockNumber() == dagchain.getGenesis().getNumber()) {
            log.warn("Entered Epoch: local number:{}, active channels is empty, can not create main block.", dagchain.getLatestMainBlockNumber());
            return;
        }

        if(CollectionUtils.isNotEmpty(activeChannels)) {
            long[] remoteNumbers = activeChannels.stream()
                    .mapToLong(c -> c.getRemotePeer().getLatestMainBlock().getNumber())
                    .sorted()
                    .toArray();
            long remoteMaxNumber = NumberUtils.max(remoteNumbers);
            if(remoteMaxNumber > dagchain.getLatestMainBlockNumber() + 1) {
                log.warn("Entered Epoch: local number:{}, remote number:{}, wait for sync data.", dagchain.getLatestMainBlockNumber(), remoteMaxNumber);
                return;
            }
        }

        // update previous block
        long number = dagchain.getLatestMainBlockNumber() + 1;
        MainBlock block = prepareMainBlock(number, currentEpochEndTime, randomx);
        log.info("Entered Epoch: {}, # active channels = {}", block, activeChannels.size());

        synchronized (epochLock) {
            // if other node epoch befor local and hash less than local
            MainBlock otherMainBlock = dagchain.getMainBlockByNumber(block.getNumber());
            if(otherMainBlock != null) {
                BigInteger latestHash = new BigInteger(1, otherMainBlock.getHash());
                BigInteger blockHash = new BigInteger(1, block.getHash());
                if(latestHash.compareTo(blockHash) < 0) {
                    log.warn("local create epoch:{}, hash:{} less than other node epoch:{}, hash:{}.",
                            block.getNumber(),
                            Bytes.wrap(block.getHash()).toHexString(),
                            otherMainBlock.getNumber(),
                            Bytes.wrap(otherMainBlock.getHash()).toHexString());
                    return;
                }
            }

            AccountState as = dagchain.getAccountState(block.getParentHash(), block.getNumber() - 1);
            BlockState bs = dagchain.getBlockState(block.getParentHash(), block.getNumber() - 1);

            // [1] update nonce
            //block.setNonce(nonce);

            // [2] add the block to chain
            boolean importResult = dagchain.importBlock(block, as.clone(), bs.clone());
            lastEpochEndtime = currentEpochEndTime;
            log.info("import {}, result = {}.", block, importResult);
            if(importResult) {
                broadcaster.broadcast(new EpochMessage(block));
            }
        }
    }

    private long resetEpoch() {
        long currentTime = System.currentTimeMillis();
        long currentXTime = XdagTime.msToXdagtimestamp(currentTime);
        long epochEndTime = XdagTime.xdagTimestampToMs(XdagTime.getEndOfEpoch(currentXTime));
        long delay = epochEndTime - currentTime;
        resetTimeout(delay);
        return epochEndTime;
    }

    public static void main(String[] args) throws InterruptedException {
        FastDateFormat df = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
        while (true) {
            long currentTime = System.currentTimeMillis();
            long currentXTime = XdagTime.msToXdagtimestamp(currentTime);
            long epochEndTime = XdagTime.xdagTimestampToMs(XdagTime.getEndOfEpoch(currentXTime));
            long delay = epochEndTime - currentTime;
            System.out.println("epoch:" + df.format(epochEndTime) + ", delay:" + delay);
            Thread.sleep(1000);
        }
    }

    protected void resetTimeout(long timeout) {
        timer.timeout(timeout);

        events.removeIf(e -> e.type == Event.Type.TIMEOUT);
    }

    /**
     * Timeout handler
     */
    protected void onTimeout() {
        switch (state) {
        case EPOCH:
            enterEpoch();
            break;
        default:
            break;
        }
    }

    protected void updateChannels() {
        activeChannels = channelManager.getActiveChannels();
        lastUpdate = TimeUtils.currentTimeMillis();
    }

    /**
     * Reset timer and events.
     */
    protected void clearTimerAndEvents() {
        timer.clear();
        events.clear();
    }

    /**
     * Create a block for POW main block.
     */
    protected MainBlock prepareMainBlock(long number, long sandTime, RandomX randomX) {
        MainBlock latestMainBlock = dagchain.getLatestMainBlock();
        AccountState asTrack = dagchain.getAccountState(latestMainBlock.getHash(), latestMainBlock.getNumber()).clone();

        long t1 = TimeUtils.currentTimeMillis();

        // construct block template
        BlockHeader parent = dagchain.getBlockHeader(number - 1);
        byte[] prevHash = parent.getHash();
        long timestamp = sandTime;
        timestamp = timestamp > parent.getTimestamp() ? timestamp : parent.getTimestamp() + 1;
        byte[] data = dagchain.constructBlockHeaderDataField();

        XAmount maxMainBLockFee = config.getDagSpec().getMaxMainBlockTransactionFee();
        XAmount minTxFee = config.getDagSpec().getMinTransactionFee();
        // fetch pending transactions
        final List<PendingManager.PendingTransaction> pendingTxs = pendingManager.getPendingTransactions(maxMainBLockFee);
        final List<Transaction> includedTxs = new ArrayList<>();
        final List<TransactionResult> includedResults = new ArrayList<>();

        TransactionExecutor exec = new TransactionExecutor(config);

        // only propose gas used up to configured block gas limit
        long remainingBlockFee = maxMainBLockFee.toLong();
//        long feeUsedInBlock = 0;
        for (PendingManager.PendingTransaction pendingTx : pendingTxs) {
            Transaction tx = pendingTx.transaction;

            // check if the remaining gas covers the declared gas limit
            long fee = minTxFee.toLong();
            if (fee > remainingBlockFee) {
                break;
            }

            // re-evaluate the transaction
            TransactionResult result = exec.execute(tx, asTrack);
            if (result.getCode().isAcceptable()) {
                includedTxs.add(tx);
                includedResults.add(result);

                // update counter
                long feeUsed = minTxFee.toLong();
                remainingBlockFee -= feeUsed;
//                feeUsedInBlock += feeUsed;
            }
        }

        // compute roots
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(includedTxs);
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(includedResults);

        BlockHeader header = BlockUtils.createProofOfWorkHeader(prevHash, number, Keys.toBytesAddress(coinbase), timestamp, transactionsRoot, resultsRoot, 0L, data);

        MainBlock block = new MainBlock(header, includedTxs, includedResults);

        long t2 = TimeUtils.currentTimeMillis();
        log.debug("Block creation: # txs = {}, time = {} ms", includedTxs.size(), t2 - t1);

        return block;
    }

    /**
     * Filter transactions to find ones that have not already been validated via the
     * pending manager.
     */
    protected List<Transaction> getUnvalidatedTransactions(List<Transaction> transactions) {

        Set<Transaction> pendingValidatedTransactions = pendingManager.getPendingTransactions()
                .stream()
                .map(pendingTx -> pendingTx.transaction)
                .collect(Collectors.toSet());

        return transactions
                .stream()
                .filter(it -> !pendingValidatedTransactions.contains(it))
                .collect(Collectors.toList());
    }

    public enum State {
        EPOCH, TIMEOUT
    }

    /**
     * Timer used by consensus. It's designed to be single timeout; previous timeout
     * get cleared when new one being added.
     * <p>
     * NOTE: it's possible that a Timeout event has been emitted when setting a new
     * timeout.
     */
    public class Timer implements Runnable {
        private long timeout;

        private Thread t;

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (this) {
                    if (timeout != -1 && timeout < TimeUtils.currentTimeMillis()) {
                        timeout = -1;
                        events.add(new Event(Event.Type.TIMEOUT));
                        continue;
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        public synchronized void start() {
            if (t == null) {
                t = new Thread(this, "pow-timer");
                t.start();
            }
        }

        public synchronized void stop() {
            if (t != null) {
                try {
                    t.interrupt();
                    t.join(10000);
                } catch (InterruptedException e) {
                    log.warn("Failed to stop consensus timer");
                    Thread.currentThread().interrupt();
                }
                t = null;
            }
        }

        public synchronized void timeout(long milliseconds) {
            if (milliseconds < 0) {
                throw new IllegalArgumentException("Timeout can not be negative");
            }
            timeout = TimeUtils.currentTimeMillis() + milliseconds;
        }

        public synchronized void clear() {
            timeout = -1;
        }
    }

    public class Broadcaster implements Runnable {
        private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

        private Thread t;

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message msg = queue.take();

                    // thread-safety via volatile
                    List<Channel> channels = activeChannels;
                    if (channels != null) {
                        int[] indices = ArrayUtils.permutation(channels.size());
                        for (int i = 0; i < indices.length && i < config.getNodeSpec().getNetRelayRedundancy(); i++) {
                            Channel c = channels.get(indices[i]);
                            if (c.isActive()) {
                                c.getMsgQueue().sendMessage(msg);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        public synchronized void start() {
            if (t == null) {
                t = new Thread(this, "pow-relay");
                t.start();
            }
        }

        public synchronized void stop() {
            if (t != null) {
                try {
                    t.interrupt();
                    t.join();
                } catch (InterruptedException e) {
                    log.error("Failed to stop consensus broadcaster");
                    Thread.currentThread().interrupt();
                }
                t = null;
            }
        }

        public void broadcast(Message msg) {
            if (!queue.offer(msg)) {
                log.error("Failed to add a message to the broadcast queue: msg = {}", msg);
            }
        }
    }

    public static class Event {
        public enum Type {
            /**
             * Stop signal
             */
            STOP,

            /**
             * Received a timeout signal.
             */
            TIMEOUT,

            EPOCH_BLOCK,
        }

        @Getter
        private final Type type;
        private final Object data;

        @Getter
        private final Channel channel;

        public Event(Type type) {
            this(type, null, null);
        }

        public Event(Type type, Object data, Channel channel) {
            this.type = type;
            this.data = data;
            this.channel = channel;
        }

        @SuppressWarnings("unchecked")
        public <T> T getData() {
            return (T) data;
        }

        @Override
        public String toString() {
            return "Event [type=" + type + ", data=" + data + "]";
        }
    }

    public enum Status {
        STOPPED, RUNNING, SYNCING
    }
}
