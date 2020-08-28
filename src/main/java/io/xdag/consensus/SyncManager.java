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
import static io.xdag.core.ImportResult.NO_PARENT;
import static io.xdag.utils.FastByteComparisons.equalBytes;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagState;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.ExecutorPipeline;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncManager {

    private Kernel kernel;
    private Blockchain blockchain;
    private long importStart;
    private AtomicLong importIdleTime = new AtomicLong();
    private AtomicInteger blocksInMem = new AtomicInteger(0);
    private boolean syncDone = false;
    private XdagChannelManager channelMgr;
    private Thread syncQueueThread;

    private ExecutorPipeline<BlockWrapper, BlockWrapper> exec1 = new ExecutorPipeline<>(
            4,
            1000,
            true,
            blockWrapper -> {
                blockWrapper.getBlock().parse();
                return blockWrapper;
            },
            throwable -> log.error("Unexpected exception: ", throwable));

    private ExecutorPipeline<BlockWrapper, Void> exec2 = exec1.add(
            1,
            1,
            new Consumer<BlockWrapper>() {
                @Override
                public void accept(BlockWrapper block) {
                    log.debug("Accept a blockWrapper");
                    blockQueue.add(block);
                    // estimateBlockSize(blockWrapper);
                }
            });

    public SyncManager(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelManager();
    }

    /** Queue with validated blocks to be added to the blockchain */
    private BlockingQueue<BlockWrapper> blockQueue = new LinkedBlockingQueue<>();

    /** Queue for the link block dosn't exist */
    private ConcurrentHashMap<ByteArrayWrapper, List<BlockWrapper>> waitingblockQueue = new ConcurrentHashMap<>();

    public void start() {
        log.debug("Download receiveBlock run...");

        syncQueueThread = new Thread(this::produceQueue, "SyncThread");
        syncQueueThread.start();
    }

    /** Processing the queue adding blocks to the chain. */
    private void produceQueue() {
        log.debug("produceQueue");

        DecimalFormat timeFormat = new DecimalFormat("0.000");
        timeFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));

        while (!Thread.currentThread().isInterrupted()) {
            BlockWrapper blockWrapper = null;
            try {
                long stale = !isSyncDone() && importStart > 0 && blockQueue.isEmpty() ? System.nanoTime() : 0;

                blockWrapper = blockQueue.take();
                blocksInMem.decrementAndGet();
                if (stale > 0) {
                    importIdleTime.addAndGet((System.nanoTime() - stale) / 1_000_000);
                }
                if (importStart == 0) {
                    importStart = System.currentTimeMillis();
                }

                long s = System.nanoTime();
                long sl;
                ImportResult importResult;

                synchronized (blockchain) {
                    sl = System.nanoTime();
                    importResult = blockchain.tryToConnect(blockWrapper.getBlock());
                }

                log.debug("impore result:" + String.valueOf(importResult));

                long f = System.nanoTime();
                long t = (f - s) / 1_000_000;
                String ts = timeFormat.format(t / 1000d) + "s";
                t = (sl - s) / 1_000_000;
                ts += t < 10 ? "" : " (lock: " + timeFormat.format(t / 1000d) + "s)";

                if (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST) {
                    syncPopBlock(blockWrapper);
                    // 获取到整条链的当前难度
                    BigInteger currentDiff = blockchain.getTopDiff();
                    // 如果当前难度大于或等于其他节点发送的最大难度则视为同步完成
                    // if(blockchain.getMainBlockSize()>= XdagTime.getCurrentEpoch()-
                    // XdagTime.getEpoch(XDAG_MAIN_ERA)){
                    // makeSyncDone();
                    // }

                    if (!syncDone && currentDiff.compareTo(kernel.getNetStatus().getMaxdifficulty()) >= 0) {
                        log.info("Current Maxdiff:" + kernel.getNetStatus().getMaxdifficulty().toString(16));
                        // 只有同步完成的时候 才能开始线程 再一次
                        if (!syncDone) {
                            if (Config.MAINNET) {
                                kernel.getXdagState().setState(XdagState.CONN);
                            } else {
                                kernel.getXdagState().setState(XdagState.CTST);
                            }
                        }
                        makeSyncDone();
                    }

                    if (importResult == IMPORTED_BEST) {
                        log.debug(
                                "Success importing : block.hash: {},, time: {}",
                                Hex.toHexString(blockWrapper.getBlock().getHash()),
                                ts);
                    }

                    if (importResult == IMPORTED_NOT_BEST) {
                        log.debug(
                                "Success importing NOT_BEST: block.hash: {}, time: {}",
                                Hex.toHexString(blockWrapper.getBlock().getHash()),
                                ts);
                    }
                }

                if (syncDone && (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST)) {
                    // 如果是自己产生的区块则在pow的时候已经广播 这里不需要重复
                    if (blockWrapper.getRemoteNode() == null
                            || !blockWrapper.getRemoteNode().equals(kernel.getClient().getNode())) {
                        if (blockWrapper.getTtl() > 0) {
                            distributeBlock(blockWrapper);
                        }
                    }
                }

                if (importResult == NO_PARENT) {
                    log.debug(
                            "No parent on the chain for block.hash: {}",
                            Hex.toHexString(blockWrapper.getBlock().getHash()));
                    // TODO:添加进sync队列 后续请求区块
                    syncPushBlock(blockWrapper, importResult.getHashLow());
                    // TODO:向谁请求
                    List<XdagChannel> channels = channelMgr.getActiveChannels();
                    for (XdagChannel channel : channels) {
                        channel.getXdag().sendGetblock(blockWrapper.getBlock().getHash());
                    }
                }
                if (importResult == EXIST) {
                    log.debug("Block have exist:" + Hex.toHexString(blockWrapper.getBlock().getHash()));
                }

            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (blockWrapper != null) {
                    log.error(
                            "Error processing block {}: ",
                            Hex.toHexString(blockWrapper.getBlock().getHashLow()),
                            e);
                    log.error(
                            "Block dump1: {}", Hex.toHexString(blockWrapper.getBlock().getXdagBlock().getData()));
                } else {
                    log.error("Error processing unknown block", e);
                }
            }
        }
    }

    public boolean isSyncDone() {
        return syncDone;
    }

    public boolean validateAndAddNewBlock(BlockWrapper blockWrapper) {
        if (blockchain.hasBlock(blockWrapper.getBlock().getHashLow())) {
             log.debug("Block have exist");
            return true;
        }
        log.debug("Adding new block to sync queue:" + Hex.toHexString(blockWrapper.getBlock().getHash()));

        pushBlocks(Collections.singletonList(blockWrapper));
        log.debug("Blocks waiting to be proceed:  queue.size: [{}] ", blockQueue.size());
        return true;
    }

    protected void pushBlocks(List<BlockWrapper> blockWrappers) {
        if (!exec1.isShutdown()) {
            exec1.pushAll(blockWrappers);
            log.debug("push blocks ....");
            blocksInMem.addAndGet(blockWrappers.size());
        }
    }

    /**
     * 同步缺失区块
     *
     * @param blockWrapper
     *            新区块
     * @param hashLow
     *            缺失的parent
     */
    public void syncPushBlock(BlockWrapper blockWrapper, byte[] hashLow) {
        log.debug("Add a new block without parent:" + Hex.toHexString(hashLow));
        ByteArrayWrapper key = new ByteArrayWrapper(hashLow);
        // 获取所有缺少hashlow的区块
        List<BlockWrapper> list = waitingblockQueue.get(key);
        if (list == null) {
            list = new ArrayList<>();
            list.add(blockWrapper);
            waitingblockQueue.put(key, list);
        } else {
            for (BlockWrapper blockInList : list) {
                if (equalBytes(blockInList.getBlock().getHashLow(), blockWrapper.getBlock().getHashLow())) {
                    return;
                }
            }
            list.add(blockWrapper);
            waitingblockQueue.put(key, list);
        }
    }

    public void syncPopBlock(BlockWrapper blockWrapper) {
        Block block = blockWrapper.getBlock();
        log.debug("A block as parent connect:" + Hex.toHexString(block.getHashLow()));
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHashLow());
        // 把所有block为parent的区块重新进行添加
        List<BlockWrapper> list = waitingblockQueue.get(key);
        if (list != null) {
            pushBlocks(list);
        }
    }

    public void makeSyncDone() {
        log.debug("Sync Done");
        if (syncDone) {
            return;
        }
        syncDone = true;

        if (Config.MAINNET) {
            kernel.getXdagState().setState(XdagState.SYNC);
        } else {
            kernel.getXdagState().setState(XdagState.STST);
        }

        log.info("sync finish! tha last mainblocsk number = {}", kernel.getNetStatus().getNmain());
        log.info("Start PoW");

        kernel.getMinerServer().start();
        kernel.getPow().onStart();
    }

    public void stop() {
        log.debug("sync manager stop");
        // if(isRunning.compareAndSet(true,false)){
        if (exec1 != null) {
            try {
                exec1.shutdown();
                exec1.join();
                if (syncQueueThread != null) {
                    syncQueueThread.interrupt();
                    syncQueueThread.join(10 * 1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void distributeBlock(BlockWrapper blockWrapper) {
        channelMgr.onNewForeignBlock(blockWrapper);
    }
}
