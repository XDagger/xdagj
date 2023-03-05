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

import static io.xdag.core.ImportResult.EXIST;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;

import com.google.common.collect.Queues;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagState;
import io.xdag.net.Channel;
import io.xdag.net.libp2p.discovery.DiscoveryPeer;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.utils.XdagTime;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;

@Slf4j
@Getter
@Setter
public class SyncManager {
    public static final int MAX_SIZE = 100000;
    private Kernel kernel;
    private Blockchain blockchain;
    private long importStart;
    private AtomicLong importIdleTime = new AtomicLong();
    private AtomicBoolean syncDone = new AtomicBoolean(false);
    private XdagChannelManager channelMgr;
    private ScheduledFuture<?> connectlibp2pFuture;
    private Set<DiscoveryPeer> hadConnectnode = new HashSet<>();


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
    private ConcurrentLinkedQueue<Bytes32> syncQueue = new ConcurrentLinkedQueue<>();
    public SyncManager(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelMgr();
        this.stateListener = new StateListener();
    }

    public void start() {
        log.debug("Download receiveBlock run...");
        new Thread(this.stateListener, "xdag-stateListener").start();
    }

    /**
     * 监听kernel状态 判断是否该自启
     */
    public boolean isTimeToStart() {
        boolean res = false;
        Config config = kernel.getConfig();
        if (config instanceof MainnetConfig) {
            if (kernel.getXdagState() != XdagState.CONN && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch() + config
                    .getPoolSpec().getWaitEpoch())) {
                res = true;
            }
        } else if (config instanceof TestnetConfig){
            if (kernel.getXdagState() != XdagState.CTST && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch() + config
                    .getPoolSpec().getWaitEpoch())) {
                res = true;
            }
        } else if (config instanceof DevnetConfig){
            if (kernel.getXdagState() != XdagState.CDST && (XdagTime.getCurrentEpoch() > kernel.getStartEpoch() + config
                    .getPoolSpec().getWaitEpoch())) {
                res = true;
            }
        } else {
            throw new IllegalStateException("error xdag network type." + config.toString());
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

        if (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST) {
            BigInteger currentDiff = blockchain.getXdagTopStatus().getTopDiff();

            Config config = kernel.getConfig();
            // 状态设置为正在同步
            if (!syncDone.get()) {
                if (config instanceof MainnetConfig) {
                    kernel.setXdagState(XdagState.CONN);
                } else if (config instanceof TestnetConfig) {
                    kernel.setXdagState(XdagState.CTST);
                } else if (config instanceof DevnetConfig) {
                    kernel.setXdagState(XdagState.CDST);
                }

                if((blockchain.getXdagStats().getMaxdifficulty().compareTo(BigInteger.ZERO) > 0) &&
                        (currentDiff.compareTo(blockchain.getXdagStats().getMaxdifficulty()) >= 0)) {
                    log.debug("StatusDiff:{},StatsDiff:{},StatusDiff >= StatsDiff starting pow.",currentDiff,blockchain.getXdagStats().getMaxdifficulty());
                    makeSyncDone();
                }
            }

            // TODO:extra区块不广播
//            if (importResult != IMPORTED_EXTRA) {
                if (blockWrapper.getRemoteNode() == null
                        || !blockWrapper.getRemoteNode().equals(kernel.getClient().getNode())) {
                    if (blockWrapper.getTtl() > 0) {
                        distributeBlock(blockWrapper);
                    }
                }
//            }
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
                        if (channel.getNode().equals(blockWrapper.getRemoteNode())) {
                            channel.getXdag().sendGetBlock(result.getHashlow());

                        }
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
            for (int i = 0; i < 200; i++) {
                Bytes32 last = syncQueue.poll();
                assert last != null;
                if(syncMap.remove(last) != null) blockchain.getXdagStats().nwaitsync--;
            }
        }
        AtomicBoolean r = new AtomicBoolean(true);
        long now = System.currentTimeMillis();
//        ByteArrayWrapper refKey = new ByteArrayWrapper(hashLow);
        Queue<BlockWrapper> newQueue = Queues.newConcurrentLinkedQueue();
        blockWrapper.setTime(now);
        newQueue.add(blockWrapper);
        blockchain.getXdagStats().nwaitsync++;
        if(!syncMap.containsKey(hashLow)){
            syncQueue.offer(hashLow);
        }
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
     *
     * @param blockWrapper 接收到的区块
     */
    public void syncPopBlock(BlockWrapper blockWrapper) {
        Block block = blockWrapper.getBlock();
//        ByteArrayWrapper key = new ByteArrayWrapper(block.getHashLow().toArray());
        Queue<BlockWrapper> queue = syncMap.getOrDefault(block.getHashLow(), null);
        if (queue != null) {
            syncMap.remove(block.getHashLow());
            syncQueue.remove(block.getHashLow());
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
                            if (channel.getNode().equals(bw.getRemoteNode())) {
                                channel.getXdag().sendGetBlock(importResult.getHashlow());
                            }
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
            log.info("start pow at:" + FastDateFormat.getInstance("yyyy-MM-dd 'at' HH:mm:ss z").format(new Date()));

            // 检查主块链
            kernel.getMinerServer().start();
            kernel.getPow().start();
        }
    }

    public void stop() {
        log.debug("sync manager stop");
        if (this.stateListener.isRunning) {
            this.stateListener.isRunning = false;
        }
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
