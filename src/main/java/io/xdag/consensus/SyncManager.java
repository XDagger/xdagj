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
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.core.*;
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
import org.hyperledger.besu.crypto.SecureRandomProvider;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;
import static io.xdag.core.ImportResult.*;
import static io.xdag.core.XdagState.*;
import static io.xdag.utils.XdagTime.msToXdagtimestamp;

@Slf4j
@Getter
@Setter
public class SyncManager {
    //sycMap's MAX_SIZE
    public static final int MAX_SIZE = 500000;
    //If syncMap.size() > MAX_SIZE remove number of keys;
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
    private ChannelManager channelMgr;


    // 监听是否需要自己启动
    private StateListener stateListener;
    /**
     * Queue with validated blocks to be added to the blockchain
     */
    private Queue<BlockWrapper> blockQueue = new ConcurrentLinkedQueue<>();
    /**
     * Queue for the link block don't exist
     */
    private ConcurrentHashMap<Bytes32, Queue<BlockWrapper>> syncMap = new ConcurrentHashMap<>();
    /***
     * Queue for poll oldest block
     */
//    private ConcurrentLinkedQueue<Bytes32> syncQueue = new ConcurrentLinkedQueue<>();
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

    public void start() throws InterruptedException {
        log.debug("Download receiveBlock run...");
        new Thread(this.stateListener, "xdag-stateListener").start();
        checkStateFuture = checkStateTask.scheduleAtFixedRate(this::checkState, 64, 5, TimeUnit.SECONDS);
    }

    private void checkState() {
        if (!isUpdateXdagStats.get()) {
            return;
        }
        if (syncDone.get()) {
            stopStateTask();
            return;
        }

        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();
        long lastTime = kernel.getSync().getLastTime();
        long curTime = msToXdagtimestamp(System.currentTimeMillis());
        long curHeight = xdagStats.getNmain();
        long maxHeight = xdagStats.getTotalnmain();
        //Exit the syncOld state based on time and height.
        if (!isSync() && (curHeight >= maxHeight - 512 || lastTime >= curTime - 32*REQUEST_BLOCKS_MAX_TIME)) {
            log.debug("our node height:{} the max height:{}, set sync state", curHeight, maxHeight);
            setSyncState();
        }
        //Confirm whether the synchronization is complete based on time and height.
        if (curHeight >= maxHeight || xdagTopStatus.getTopDiff().compareTo(xdagStats.maxdifficulty) >= 0) {
            log.debug("our node height:{} the max height:{}, our diff:{} max diff:{}, make sync done",
                    curHeight, maxHeight, xdagTopStatus.getTopDiff(), xdagStats.maxdifficulty);
            makeSyncDone();
        }

    }

    /**
     * 监听kernel状态 判断是否该自启
     */
    public boolean isTimeToStart() {
        boolean res = false;
        Config config = kernel.getConfig();
        int waitEpoch = config.getNodeSpec().getWaitEpoch();
        if (!isSync() && !isSyncOld() && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch() + waitEpoch)) {
            res = true;
        }
        if(res){
            log.debug("Waiting time exceeded,starting pow");
        }
        return res;
    }

    /**
     * Processing the queue adding blocks to the chain.
     */
    //todo:修改共识
    public ImportResult importBlock(BlockWrapper blockWrapper) {
        log.debug("importBlock:{}", blockWrapper.getBlock().getHashLow());
        ImportResult importResult = blockchain
                .tryToConnect(new Block(new XdagBlock(blockWrapper.getBlock().getXdagBlock().getData().toArray())));

        if (importResult == EXIST) {
            log.debug("Block have exist:" + blockWrapper.getBlock().getHashLow());
        }

        if (!blockWrapper.isOld() && (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST)) {
            Peer blockPeer = blockWrapper.getRemotePeer();
            Node node = kernel.getClient().getNode();
            if (blockPeer == null || !StringUtils.equals(blockPeer.getIp(), node.getIp()) || blockPeer.getPort() != node.getPort()) {
                if (blockWrapper.getTtl() > 0) {
                    distributeBlock(blockWrapper);
                }
            }
        }
        return importResult;
    }

    public synchronized ImportResult validateAndAddNewBlock(BlockWrapper blockWrapper) {
        blockWrapper.getBlock().parse();
        ImportResult result = importBlock(blockWrapper);
        log.debug("validateAndAddNewBlock:{}, {}", blockWrapper.getBlock().getHashLow(), result);
        switch (result) {
            case EXIST, IMPORTED_BEST, IMPORTED_NOT_BEST, IN_MEM -> syncPopBlock(blockWrapper);
            case NO_PARENT -> {
                if (syncPushBlock(blockWrapper, result.getHashlow())) {
                    log.debug("push block:{}, NO_PARENT {}", blockWrapper.getBlock().getHashLow(), result);
                    List<Channel> channels = channelMgr.getActiveChannels();
                    for (Channel channel : channels) {
                        //if (channel.getRemotePeer().equals(blockWrapper.getRemotePeer())) {
                            channel.getP2pHandler().sendGetBlock(result.getHashlow(), blockWrapper.isOld());
                        //}
                    }

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
     * 同步缺失区块
     *
     * @param blockWrapper 新区块
     * @param hashLow 缺失的parent哈希
     */
    public boolean syncPushBlock(BlockWrapper blockWrapper, Bytes32 hashLow) {
        if(syncMap.size() >= MAX_SIZE){
            for (int j = 0; j < DELETE_NUM; j++) {
                List<Bytes32> keyList = new ArrayList<>(syncMap.keySet());
                Bytes32 key = keyList.get(SecureRandomProvider.publicSecureRandom().nextInt(keyList.size()));
                assert key != null;
                if(syncMap.remove(key) != null) blockchain.getXdagStats().nwaitsync--;
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
                                //TODO should be consider timeout not received request block
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
     * 根据接收到的区块，将子区块释放
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
                    // TODO import成功后都需要移除
                    syncPopBlock(bw);
                    queue.remove(bw);
                }
                case NO_PARENT -> {
                    if (syncPushBlock(bw, importResult.getHashlow())) {
                        log.debug("push block:{}, NO_PARENT {}", bw.getBlock().getHashLow(),
                                importResult.getHashlow().toHexString());
                        List<Channel> channels = channelMgr.getActiveChannels();
                        for (Channel channel : channels) {
//                            Peer remotePeer = channel.getRemotePeer();
//                            Peer blockPeer = bw.getRemotePeer();
                            //if (StringUtils.equals(remotePeer.getIp(), blockPeer.getIp()) && remotePeer.getPort() == blockPeer.getPort() ) {
                                channel.getP2pHandler().sendGetBlock(importResult.getHashlow(), blockWrapper.isOld());
                            //}
                        }
                    }
                }
                default -> {
                }
                }
            });
        }
    }

    // TODO：目前默认是一直保持同步，不负责出块
    public void makeSyncDone() {
        if (syncDone.compareAndSet(false, true)) {
            // 关闭状态检测进程
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
            if(config.getEnableTxHistory() && txHistoryStore != null) {
                // sync done, the remaining history is batch written.
                txHistoryStore.batchSaveTxHistory(null);
            }

            if (config.getEnableGenerateBlock()) {
                log.info("start pow at:" + FastDateFormat.getInstance("yyyy-MM-dd 'at' HH:mm:ss z").format(new Date()));
                // check main chain
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

    public void stop() {
        log.debug("sync manager stop");
        if (this.stateListener.isRunning) {
            this.stateListener.isRunning = false;
        }
        stopStateTask();
    }

    private void stopStateTask() {
        if (checkStateFuture != null) {
            checkStateFuture.cancel(true);
        }
        // 关闭线程池
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
