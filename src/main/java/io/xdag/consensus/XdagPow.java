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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.hyperledger.besu.crypto.KeyPair;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.xdag.DagKernel;
import io.xdag.config.Config;
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
import io.xdag.core.state.ByteArray;
import io.xdag.crypto.Keys;
import io.xdag.crypto.RandomX;
import io.xdag.crypto.RandomXMemory;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.net.message.Message;
import io.xdag.net.message.consensus.MainBlockMessage;
import io.xdag.utils.ArrayUtils;
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
 * <li><code>EPOCH_START</code>: epoch start</li>
 * <li><code>EPOCH_END</code>: epoch end</li>
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

    protected MainBlock mainBlock;
    protected RandomX randomx;

    protected long height;

    protected Cache<ByteArray, MainBlock> validBlocks = Caffeine.newBuilder().maximumSize(8).build();

    protected List<Channel> activeChannels;

    protected long lastUpdate;


    public XdagPow(DagKernel kernel) {
        this.kernel = kernel;
        this.config = kernel.getConfig();

        this.dagchain = kernel.getDagchain();
        this.channelManager = kernel.getChannelManager();
        this.pendingManager = kernel.getPendingManager();
        this.syncManager = kernel.getSyncManager();
        this.coinbase = kernel.getCoinbase();
        this.randomx = kernel.getRandomx();

        this.timer = new Timer();
        this.broadcaster = new Broadcaster();

        this.status = Status.STOPPED;
        this.state = State.EPOCH_START;
    }

    /**
     * Pause the pow manager, and do synchronization.
     */
    protected void sync(long target) {
        if (status == Status.RUNNING) {
            // change status
            status = Status.SYNCING;

            clearTimerAndEvents();

            // start syncing
            syncManager.start(target);

            // restore status if not stopped
            if (status != Status.STOPPED) {
                status = Status.RUNNING;

                // enter epoch start
                enterEpochStart();
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

                // in case we get stuck at one height for too long
                if (lastUpdate + 2 * 60 * 1000L < TimeUtils.currentTimeMillis()) {
                    updateChannels();
                }

                switch (ev.getType()) {
                case STOP:
                    return;
                case TIMEOUT:
                    onTimeout();
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
            log.info("Xdagj POW manager started");

            enterEpochStart();
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
            generateBlock.set(prepareMainBlock());
        }
    }

    /**
     * Enter the NEW_EPOCH_START state
     */
    protected void enterEpochStart() {
        state = State.EPOCH_START;

        // update channels
        updateChannels();

        // reset events
        clearTimerAndEvents();

        // update previous block
        height = dagchain.getLatestMainBlockNumber() + 1;

        log.info("Entered Epoch Start: height = {}", height);
        long currentTime = TimeUtils.currentTimeMillis();
        long delay = XdagTime.getEndOfEpoch(currentTime) - currentTime;
        long time = delay + config.getDagSpec().getPowEpochTimeout();
        resetTimeout(time);
    }

    /**
     * Enter the EPOCH_END state
     */
    protected void enterEpochEnd() {
        // make sure we only enter FINALIZE state once per height
        if (state == State.EPOCH_END) {
            return;
        }

        state = State.EPOCH_END;
        MainBlock block = prepareMainBlock();
        log.info("Entered Epoch End: height = {}, mainblock = {}, # connected channels = {}", height, block, activeChannels.size());
        resetTimeout(0);
        // validate block nonce
        boolean valid = (block != null && validateMainBlock(block.getHeader(), block.getTransactions()));
        if (valid) {
            // [1] update nonce
            //block.setNonce(nonce);

            // [2] add the block to chain
            boolean importResult = dagchain.importBlock(block);

            if(importResult) {
                broadcaster.broadcast(new MainBlockMessage(block));
                mainBlock = block;
            }
            log.info("import mainblock = {}, result = {}.", block, importResult);
        } else {
            sync(height + 1);
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
        case EPOCH_START:
            enterEpochEnd();
            break;
        case EPOCH_END:
            enterEpochStart();
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
    protected MainBlock prepareMainBlock(RandomX randomX) {
        AccountState asTrack = dagchain.getAccountState().track();

        long t1 = TimeUtils.currentTimeMillis();

        // construct block template
        BlockHeader parent = dagchain.getBlockHeader(height - 1);
        long number = height;
        byte[] prevHash = parent.getHash();
        long timestamp = TimeUtils.currentTimeMillis();
        timestamp = timestamp > parent.getTimestamp() ? timestamp : parent.getTimestamp() + 1;
        byte[] data = dagchain.constructBlockHeaderDataField();
//        BlockHeader tempHeader = new BlockHeader(height, Keys.toBytesAddress(coinbase), prevHash, timestamp,
//                new byte[0], new byte[0], data);

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

        BlockHeader header = new BlockHeader(number, Keys.toBytesAddress(coinbase), prevHash, timestamp, transactionsRoot,
                resultsRoot, data);
        MainBlock block = new MainBlock(header, includedTxs, includedResults);

        long t2 = TimeUtils.currentTimeMillis();
        log.debug("Block creation: # txs = {}, time = {} ms", includedTxs.size(), t2 - t1);

        return block;
    }

    /**
     * Check if a main block is valid.
     */
    protected boolean validateMainBlock(BlockHeader header, List<Transaction> transactions) {
        try {
            AccountState asTrack = dagchain.getAccountState().track();

//            List< Bytes32> txhashs = Lists.newArrayList();
//            transactions.forEach(tx -> {
//                txhashs.add(Bytes32.wrap(tx.getHash()));
//            });

            MainBlock block = new MainBlock(header, transactions);
            long t1 = TimeUtils.currentTimeMillis();

            // [1] check block header
            MainBlock latest = dagchain.getLatestMainBlock();
            if (!block.validateHeader(header, latest.getHeader())) {
                log.warn("Invalid block header");
                return false;
            }

            // [?] additional checks by consensus
            // - disallow block time drifting;
            // - restrict the coinbase to be the proposer
            if (header.getTimestamp() - TimeUtils.currentTimeMillis() > config.getDagSpec().getPowEpochTimeout()) {
                log.warn("A block in the future is not allowed");
                return false;
            }

            // [2] check transactions
            List<Transaction> unvalidatedTransactions = getUnvalidatedTransactions(transactions);
            if (!block.validateTransactions(header, unvalidatedTransactions, transactions, config.getNodeSpec().getNetwork())) {
                log.warn("Invalid transactions");
                return false;
            }
            if (transactions.stream().anyMatch(tx -> dagchain.hasTransaction(tx.getHash()))) {
                log.warn("Duplicated transaction hash is not allowed");
                return false;
            }

            // [3] evaluate transactions
            TransactionExecutor transactionExecutor = new TransactionExecutor(config);
            List<TransactionResult> results = transactionExecutor.execute(transactions, asTrack);
            if (!block.validateResults(header, results)) {
                log.error("Invalid transaction results");
                return false;
            }
            block.setResults(results); // overwrite the results

            long t2 = TimeUtils.currentTimeMillis();
            log.debug("Block validation: # txs = {}, time = {} ms", transactions.size(), t2 - t1);

            validBlocks.put(ByteArray.of(block.getHash()), block);
            return true;
        } catch (Exception e) {
            log.error("Unexpected exception during main block validation", e);
            return false;
        }
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
        EPOCH_START, EPOCH_END, TIMEOUT
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
                        events.add(new Event(Event.Type.TIMEOUT));
                        timeout = -1;
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
                                c.getMessageQueue().sendMessage(msg);
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

            /**
             * Received an epoch start message.
             */
            EPOCH_START,

            /**
             * Received an epoch end message.
             */
            EPOCH_END
        }

        @Getter
        private final Type type;
        private final Object data;

        public Event(Type type) {
            this(type, null);
        }

        public Event(Type type, Object data) {
            this.type = type;
            this.data = data;
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
