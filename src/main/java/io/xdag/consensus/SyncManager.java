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

import com.google.common.collect.Queues;
import io.xdag.Kernel;
import io.xdag.config.*;
import io.xdag.core.*;
import io.xdag.crypto.core.CryptoProvider;
import io.xdag.crypto.encoding.Base58;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.net.Peer;
import io.xdag.net.node.Node;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;
import static io.xdag.core.ImportResult.*;
import static io.xdag.core.XdagState.*;
import static io.xdag.utils.BasicUtils.hash2byte;
import static io.xdag.utils.XdagTime.msToXdagtimestamp;

@Slf4j
@Getter
@Setter
public class SyncManager extends AbstractXdagLifecycle {
    // Maximum size of syncMap
    public static final int MAX_SIZE = 500000;
    // Number of keys to remove when syncMap exceeds MAX_SIZE
    public static final int DELETE_NUM = 5000;

    private static final ThreadFactory factory = new BasicThreadFactory.Builder()
            .namingPattern("SyncManager-thread-%d")
            .daemon(true)
            .build();
    private Kernel kernel;
    private Blockchain blockchain;
    private long importStart;
    private AtomicLong importIdleTime = new AtomicLong();
    private AtomicBoolean syncDone = new AtomicBoolean(false);
    private AtomicBoolean isUpdateXdagStats = new AtomicBoolean(false);
    /**
     * When (wall clock, ms) the best chain last advanced through a block that was old when it arrived: the node
     * was catching up with history then. 0: never.
     */
    private volatile long lastCatchUpTime;
    /** A node is not called synchronised while it was catching up less than this long ago. */
    private long quietMs = 30_000;
    /** When the sync state is looked at for the first time after the start, and how often from then on. */
    private long checkStateDelayMs = 64_000;
    private long checkStatePeriodMs = 5_000;
    /** What the peers {@link XdagSync} is holding a round with are sending, by peer id. */
    private final Map<String, PeerEvidence> watched = new ConcurrentHashMap<>();
    private ChannelManager channelMgr;

    // Monitor whether to start itself
    private StateListener stateListener;
    /**
     * Queue with validated blocks to be added to the blockchain
     */
    private Queue<BlockWrapper> blockQueue = new ConcurrentLinkedQueue<>();
    /**
     * Queue for blocks with missing links
     */
    private ConcurrentHashMap<Bytes32, Queue<BlockWrapper>> syncMap = new ConcurrentHashMap<>();
    /**
     * Queue for polling oldest blocks
     */
    private ConcurrentLinkedQueue<Bytes32> syncQueue = new ConcurrentLinkedQueue<>();

    private ScheduledExecutorService checkStateTask;

    private ScheduledFuture<?> checkStateFuture;
    private final TransactionHistoryStore txHistoryStore;

    public SyncManager(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelMgr();
        this.stateListener = new StateListener();
        checkStateTask = new ScheduledThreadPoolExecutor(1, factory);
        this.txHistoryStore = kernel.getTxHistoryStore();
    }

    @Override
    protected void doStart() {
        log.debug("Download receiveBlock run...");
        new Thread(this.stateListener, "xdag-stateListener").start();
        checkStateFuture = checkStateTask.scheduleAtFixedRate(this::checkState, checkStateDelayMs, checkStatePeriodMs,
                TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() {
        log.debug("sync manager stop");
        if (this.stateListener.isRunning) {
            this.stateListener.isRunning = false;
        }
        stopStateTask();
    }

    /** A block that is this much older than now when it arrives is history, not news (one request span). */
    static final long OLD_BLOCK_AGE = REQUEST_BLOCKS_MAX_TIME;

    void checkState() {
        try {
            if (blockchain.isOpenNetLatched()) {
                checkStateOpen();
            } else {
                checkStateClosed();
            }
        } catch (RuntimeException e) {
            // an exception that escapes would silently cancel the periodic task
            log.warn("checking the sync state failed: {}", e.toString());
        }
    }

    /**
     * Closed network (the open-network fork is not in force): the peers are the ones the operator configured,
     * and what they report about the network decides, as in 0.8.x.
     */
    private void checkStateClosed() {
        if (!isUpdateXdagStats.get()) {
            return;
        }
        if (syncDone.get()) {
            return;
        }

        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();
        long lastTime = kernel.getSync().getLastTime();
        long curTime = msToXdagtimestamp(System.currentTimeMillis());
        long curHeight = xdagStats.getNmain();
        long maxHeight = xdagStats.getTotalnmain();
        // Exit the syncOld state based on time and height.
        if (!isSync() && (curHeight >= maxHeight - 512 || lastTime >= curTime - 32 * REQUEST_BLOCKS_MAX_TIME)) {
            log.debug("our node height:{} the max height:{}, set sync state", curHeight, maxHeight);
            setSyncState();
        }
        // Confirm whether the synchronization is complete based on time and height.
        if (curHeight >= maxHeight || xdagTopStatus.getTopDiff().compareTo(xdagStats.maxdifficulty) >= 0) {
            log.debug("our node height:{} the max height:{}, our diff:{} max diff:{}, make sync done",
                    curHeight, maxHeight, xdagTopStatus.getTopDiff(), xdagStats.maxdifficulty);
            makeSyncDone();
        }
    }

    /**
     * Open network: anybody can be a peer, so nothing a peer <em>says</em> decides (a peer that claims a huge
     * height would keep the node "synchronising" forever), nothing about the node's own tip decides (a node that
     * is behind can be handed blocks with current timestamps and no work, and they become its latest main
     * blocks), and the sums of the comparison do not decide either (they are checksums; blocks made for the
     * purpose make them equal). What decides is made of blocks, see {@link XdagSync}:
     * <ul>
     * <li>a cycle of rounds - one with every peer that was connected when it began, and they are at least half
     *     of the peers the node has now - was held since the node last caught up with history, and</li>
     * <li>of the peers that showed blocks from the present, most have none that this node cannot attach to its
     *     own - which it can only do if it has the whole history behind them, and</li>
     * <li>the best chain has not been advancing through old blocks lately: that is what catching up looks like,
     *     and only a chain with more work than the one the node has can cause it.</li>
     * </ul>
     * The rounds go on afterwards. If history turns up after all ({@link #onCatchUp}), the node goes back to
     * "synchronising" until it has caught up.
     */
    private void checkStateOpen() {
        if (syncDone.get()) {
            return;
        }
        long lastTime = kernel.getSync().getLastTime();
        long curTime = msToXdagtimestamp(System.currentTimeMillis());
        if (!isSync() && isSyncOld() && lastTime >= curTime - 32 * REQUEST_BLOCKS_MAX_TIME) {
            // within reach of the present: news is worth processing now
            setSyncState();
        }

        int peers = channelMgr == null ? 0 : channelMgr.getActiveChannels().size();
        if (peers == 0) {
            // nobody to compare with (a node that is alone starts by itself, see isTimeToStart)
            return;
        }
        long since = lastCatchUpTime;
        if (System.currentTimeMillis() - since < quietMs) {
            return;
        }
        XdagSync.CycleResult cycle = kernel.getSync().getLastCycle();
        if (cycle == null || cycle.startedAt() <= since) {
            // not every peer has had its turn since the node last caught up
            return;
        }
        if (cycle.attempted() * 2 < peers) {
            // what was found is about a peer set that is not the one the node has now
            return;
        }
        // Most of the peers that had something to say must have nothing this node lacks. A peer that did not
        // answer counts against it (it may be the one that knows better). Peers that answered and showed
        // nothing from the present - they are behind themselves, or there is no present: nobody produces
        // blocks - say nothing either way; if that is all there is, having heard most peers is enough.
        boolean agreed = cycle.inSync() + cycle.behind() > 0
                ? cycle.inSync() * 2 > cycle.inSync() + cycle.behind() + cycle.failed()
                : (cycle.attempted() - cycle.failed()) * 2 > cycle.attempted();
        if (agreed) {
            log.debug("rounds with {} peers since the last catch-up: {} have nothing we lack, {} have: sync done",
                    cycle.attempted(), cycle.inSync(), cycle.behind());
            makeSyncDone();
        }
    }

    /**
     * The best chain advanced through a block that was already old when it arrived. A node that is up to date
     * only sees that if somebody has more work than the whole chain it follows; a node that is behind sees it
     * all the time.
     */
    private void onCatchUp() {
        lastCatchUpTime = System.currentTimeMillis();
        if (syncDone.get() && blockchain.isOpenNetLatched()) {
            revokeSyncDone();
        }
    }

    /** The node called itself synchronised and was not: back to synchronising (no blocks are produced meanwhile). */
    private void revokeSyncDone() {
        if (syncDone.compareAndSet(true, false)) {
            log.warn("Older blocks with more work than our chain are arriving: this node was not synchronised. "
                    + "Back to synchronising, last main block number = {}", blockchain.getXdagStats().nmain);
            setSyncState();
            kernel.getSync().setStatus(XdagSync.Status.SYNCING);
        }
    }

    /** Starts noting what a peer sends ({@link PeerEvidence}), until {@link #unwatch}. */
    public PeerEvidence watch(String peerId) {
        PeerEvidence evidence = new PeerEvidence();
        watched.put(peerId, evidence);
        return evidence;
    }

    public void unwatch(String peerId) {
        watched.remove(peerId);
    }

    private PeerEvidence evidenceFor(BlockWrapper blockWrapper) {
        if (watched.isEmpty()) {
            return null;
        }
        Peer from = blockWrapper.getRemotePeer();
        return from == null || from.getPeerId() == null ? null : watched.get(from.getPeerId());
    }

    /**
     * Monitor kernel state to determine if it's time to start
     */
    public boolean isTimeToStart() {
        if (blockchain.isOpenNetLatched() && channelMgr != null && !channelMgr.getActiveChannels().isEmpty()) {
            // An open node that has peers decides by what it found out with them (checkStateOpen). Having waited
            // long enough is a reason to start only for a node that is alone.
            return false;
        }
        boolean res = false;
        Config config = kernel.getConfig();
        int waitEpoch = config.getNodeSpec().getWaitEpoch();
        if (!isSync() && !isSyncOld() && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch() + waitEpoch)) {
            res = true;
        }
        if (res) {
            log.debug("Waiting time exceeded,starting pow");
        }
        return res;
    }

    /**
     * Process blocks in queue and add them to the chain
     */
    // TODO: Modify consensus
    public ImportResult importBlock(BlockWrapper blockWrapper) {
        log.debug("importBlock:{}", blockWrapper.getBlock().getHashLow());
        ImportResult importResult = blockchain
                .tryToConnect(new Block(new XdagBlock(blockWrapper.getBlock().getXdagBlock().getData().toArray())));

        if (importResult == EXIST) {
            log.debug("Block have exist:{}", blockWrapper.getBlock().getHashLow());
        }
        if (importResult == IMPORTED_BEST
                && blockWrapper.getBlock().getTimestamp() < XdagTime.getCurrentTimestamp() - OLD_BLOCK_AGE) {
            onCatchUp();
        }

        if (!blockWrapper.isOld() && (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST)) {
            Peer blockPeer = blockWrapper.getRemotePeer();
            Node node = kernel.getChannelMgr().getSelfNode();
            if (blockPeer == null || !StringUtils.equals(blockPeer.getIp(), node.getIp()) || blockPeer.getPort() != node.getPort()) {
                if (blockWrapper.getTtl() > 0) {
                    distributeBlock(blockWrapper);
                }
            }
        }
        return importResult;
    }

    /**
     * @return the result of the import; null if the block was only looked at, which happens to history blocks of
     *         a peer while {@link XdagSync} is searching for where this node's history ends ({@link PeerEvidence})
     */
    public synchronized ImportResult validateAndAddNewBlock(BlockWrapper blockWrapper) {
        blockWrapper.getBlock().parse();
        PeerEvidence evidence = evidenceFor(blockWrapper);
        if (evidence != null && evidence.peek(blockWrapper, blockchain)) {
            return null;
        }
        ImportResult result = importBlock(blockWrapper);
        if (evidence != null) {
            evidence.saw(blockWrapper.getBlock(), result);
        }
        log.debug("validateAndAddNewBlock:{}, {}", blockWrapper.getBlock().getHashLow(), result);
        switch (result) {
            case EXIST, IMPORTED_BEST, IMPORTED_NOT_BEST, IN_MEM -> syncPopBlock(blockWrapper);
            case NO_PARENT -> {
                if (syncPushBlock(blockWrapper, result.getHashlow())) {//Return true to indicate that it has been more than 60 seconds since the last time it was placed here due to the lack of a parent reference, and request to inquire about the parent block from other nodes again
                    log.debug("push block:{}, NO_PARENT {}", blockWrapper.getBlock().getHashLow(), result);
                    askForBlock(result.getHashlow(), blockWrapper);
                }
            }
            case INVALID_BLOCK -> {
//                log.error("invalid block:{}", Hex.toHexString(blockWrapper.getBlock().getHashLow()));
            }
            default -> {
            }
        }
        return result;
    }

    /**
     * Asks for a block this node is missing: the peer that sent the block that needs it, if it is still
     * connected, otherwise one peer chosen at random. (Every peer used to be asked, so somebody sending a
     * stream of blocks with unknown parents made this node pester all of its peers - and get itself banned.)
     */
    private void askForBlock(MutableBytes32 hashLow, BlockWrapper needing) {
        if (channelMgr == null) {
            return;
        }
        List<Channel> channels = channelMgr.getActiveChannels();
        if (channels.isEmpty()) {
            return;
        }
        Channel chosen = null;
        Peer from = needing.getRemotePeer();
        if (from != null && from.getPeerId() != null) {
            for (Channel c : channels) {
                if (c.isActive() && from.getPeerId().equals(c.getRemotePeer().getPeerId())) {
                    chosen = c;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = channels.get(CryptoProvider.nextInt(0, channels.size()));
        }
        chosen.getP2pHandler().sendGetBlock(hashLow, needing.isOld());
    }

    /**
     * Synchronize missing blocks
     *
     * @param blockWrapper New block
     * @param hashLow Hash of missing parent block
     */
    public boolean syncPushBlock(BlockWrapper blockWrapper, Bytes32 hashLow) {
        if (syncMap.size() >= MAX_SIZE) {
            for (int j = 0; j < DELETE_NUM; j++) {
                List<Bytes32> keyList = new ArrayList<>(syncMap.keySet());

                Bytes32 key = keyList.get(CryptoProvider.nextInt(0, keyList.size()));
                assert key != null;
                if (syncMap.remove(key) != null) blockchain.getXdagStats().nwaitsync--;
            }
        }
        AtomicBoolean r = new AtomicBoolean(true);
        long now = System.currentTimeMillis();

        Queue<BlockWrapper> newQueue = Queues.newConcurrentLinkedQueue();
        blockWrapper.setTime(now);
        newQueue.add(blockWrapper);
        blockchain.getXdagStats().nwaitsync++;

        syncMap.merge(hashLow, newQueue,
                (oldQ, newQ) -> {
                    blockchain.getXdagStats().nwaitsync--;
                    for (BlockWrapper b : oldQ) {
                        if (b.getBlock().getHashLow().equals(blockWrapper.getBlock().getHashLow())) {
                            // after 64 sec must resend block request
                            if (now - b.getTime() > 64 * 1000) {
                                b.setTime(now);
                                r.set(true);
                            } else {
                                // TODO: Consider timeout for unreceived request block
                                r.set(false);
                            }
                            return oldQ;
                        }
                    }
                    oldQ.add(blockWrapper);
                    r.set(true);
                    return oldQ;
                });
        return r.get();
    }

    /**
     * Release child blocks based on received block
     */
    public void syncPopBlock(BlockWrapper blockWrapper) {
        Block block = blockWrapper.getBlock();

        Queue<BlockWrapper> queue = syncMap.getOrDefault(block.getHashLow(), null);
        if (queue != null) {
            syncMap.remove(block.getHashLow());
            blockchain.getXdagStats().nwaitsync--;
            queue.forEach(bw -> {
                ImportResult importResult = importBlock(bw);
                switch (importResult) {
                    case EXIST, IN_MEM, IMPORTED_BEST, IMPORTED_NOT_BEST -> {
                        // TODO: Need to remove after successful import
                        syncPopBlock(bw);
                        queue.remove(bw);
                    }
                    case NO_PARENT -> {
                        if (syncPushBlock(bw, importResult.getHashlow())) {
                            log.debug("push block:{}, NO_PARENT {}", bw.getBlock().getHashLow(),
                                    importResult.getHashlow().toHexString());
                            askForBlock(importResult.getHashlow(), bw);
                        }
                    }
                    default -> {
                    }
                }
            });
        }
    }

    // TODO: Currently stays in sync by default, not responsible for block generation
    public void makeSyncDone() {
        if (syncDone.compareAndSet(false, true)) {
            // Stop state check process
            this.stateListener.isRunning = false;
            Config config = kernel.getConfig();
            if (config instanceof MainnetConfig) {
                if (kernel.getXdagState() != XdagState.SYNC) {
                    kernel.setXdagState(XdagState.SYNC);
                }
            } else if (config instanceof TestnetConfig) {
                if (kernel.getXdagState() != XdagState.STST) {
                    kernel.setXdagState(XdagState.STST);
                }
            } else if (config instanceof DevnetConfig) {
                if (kernel.getXdagState() != XdagState.SDST) {
                    kernel.setXdagState(XdagState.SDST);
                }
            }

            log.info("sync done, the last main block number = {}", blockchain.getXdagStats().nmain);
            kernel.getSync().setStatus(XdagSync.Status.SYNC_DONE);
            if (config.getEnableTxHistory() && txHistoryStore != null) {
                // Sync done, batch write remaining history
                txHistoryStore.batchSaveTxHistory(null);
            }

            if (config.getEnableGenerateBlock()) {
                log.info("start pow at:{}",
                        FastDateFormat.getInstance("yyyy-MM-dd 'at' HH:mm:ss z").format(new Date()));
                // Check main chain
//                kernel.getMinerServer().start();
                kernel.getPow().start();
            } else {
                log.info("A non-mining node, will not generate blocks.");
            }
        }
    }

    public void setSyncState() {
        Config config = kernel.getConfig();
        if (config instanceof MainnetConfig) {
            kernel.setXdagState(CONN);
        } else if (config instanceof TestnetConfig) {
            kernel.setXdagState(CTST);
        } else if (config instanceof DevnetConfig) {
            kernel.setXdagState(CDST);
        }
    }

    public boolean isSync() {
        return kernel.getXdagState() == CONN || kernel.getXdagState() == CTST
                || kernel.getXdagState() == CDST;
    }

    public boolean isSyncOld() {
        return kernel.getXdagState() == CONNP || kernel.getXdagState() == CTSTP
                || kernel.getXdagState() == CDSTP;
    }

    private void stopStateTask() {
        if (checkStateFuture != null) {
            checkStateFuture.cancel(true);
        }
        // Shutdown thread pool
        checkStateTask.shutdownNow();
    }

    public void distributeBlock(BlockWrapper blockWrapper) {
        channelMgr.onNewForeignBlock(blockWrapper);
    }

    private class StateListener implements Runnable {

        boolean isRunning = false;

        @Override
        public void run() {
            this.isRunning = true;
            try {
                Thread.sleep(100000);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            while (this.isRunning) {
                if (isTimeToStart()) {
                    makeSyncDone();
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

}
