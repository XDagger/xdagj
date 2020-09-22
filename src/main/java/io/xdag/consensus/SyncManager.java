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
import static io.xdag.utils.FastByteComparisons.equalBytes;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.xdag.core.*;

import org.spongycastle.util.encoders.Hex;

import com.google.common.collect.Queues;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class SyncManager {

    private Kernel kernel;
    private Blockchain blockchain;
    private long importStart;
    private AtomicLong importIdleTime = new AtomicLong();
    private boolean syncDone = false;
    private XdagChannelManager channelMgr;

    private static AtomicInteger cnt = new AtomicInteger(0);
//    private ScheduledFuture<?> timerTask;
//    public static final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(
//            1,
//            new BasicThreadFactory.Builder()
//                    .namingPattern("SyncManageTimer-" + cnt.getAndIncrement())
//                    .build());

    public SyncManager(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.channelMgr = kernel.getChannelMgr();
    }

    /** Queue with validated blocks to be added to the blockchain */
    private Queue<BlockWrapper> blockQueue = new ConcurrentLinkedQueue<>();

    /** Queue for the link block dosn't exist */
    private ConcurrentHashMap<ByteArrayWrapper, Queue<BlockWrapper>> syncMap = new ConcurrentHashMap<>();
    public void start() {
        log.debug("Download receiveBlock run...");
//        timerTask = timer.scheduleAtFixedRate(
//                () -> {
//                    try {
//                        nudgeQueue();
//                    } catch (Throwable t) {
//                        log.error("Unhandled exception:" + t.getMessage(), t);
//                        t.printStackTrace();
//                    }
//                },
//                10,
//                10,
//                TimeUnit.MILLISECONDS);
    }

//    private void nudgeQueue() {
//        // 1000 / 10 * 200 = 20000 messages per second
//        int n = Math.min(200, blockQueue.size());
//        if (n == 0) {
//            return;
//        }
//        // write out n messages
//        for (int i = 0; i < n; i++) {
//            BlockWrapper bw = blockQueue.poll();
//            if(bw!= null) {
//                produceQueue(bw);
//            }
//        }
//
//    }

    /** Processing the queue adding blocks to the chain. */
    public ImportResult produceQueue(BlockWrapper blockWrapper) {
        log.debug("produceQueue:[{}]", BytesUtils.toHexString(blockWrapper.getBlock().getHash()));
        ImportResult importResult = blockchain.tryToConnect(blockWrapper.getBlock());
        DecimalFormat timeFormat = new DecimalFormat("0.000");
        timeFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));

        if (importResult == EXIST) {
            log.error("Block have exist:" + Hex.toHexString(blockWrapper.getBlock().getHash()));
        }

        if (importResult == IMPORTED_BEST || importResult == IMPORTED_NOT_BEST) {
            BigInteger currentDiff = blockchain.getTopDiff();
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
        return  importResult;
    }

    public boolean isSyncDone() {
        return syncDone;
    }

    public boolean validateAndAddNewBlock(BlockWrapper blockWrapper) {
        boolean r = false;
//        if (blockchain.hasBlock(blockWrapper.getBlock().getHashLow())) {
//            log.debug("Block have exist");
//            return true;
//        }
        //TODO limit queue size
        blockWrapper.getBlock().parse();
        //blockQueue.add(blockWrapper);
        ImportResult result = produceQueue(blockWrapper);
        switch (result) {
        case EXIST:
        case IMPORTED_BEST:
        case IMPORTED_NOT_BEST:
            syncPopBlock(blockWrapper);
            r = true;
            break;
        case NO_PARENT: {
            if (syncPushBlock(blockWrapper, result.getHashLow())) {
                log.error("push block:{}, NO_PARENT {}", Hex.toHexString(blockWrapper.getBlock().getHashLow()),
                        Hex.toHexString(result.getHashLow()));
                List<XdagChannel> channels = channelMgr.getActiveChannels();
                for (XdagChannel channel : channels) {
                    channel.getXdag().sendGetblock(result.getHashLow());
                }
            }
            r = true;
            break;
        }
        default:
            r = false;
            break;
        }
        return r;
    }

    /**
     * 同步缺失区块
     *
     * @param blockWrapper
     *            新区块
     * @param hashLow
     *            缺失的parent
     */
    public boolean syncPushBlock(BlockWrapper blockWrapper, byte[] hashLow) {
        AtomicBoolean r = new AtomicBoolean(false);
        long now = System.currentTimeMillis();
        ByteArrayWrapper refKey = new ByteArrayWrapper(hashLow);
        Queue<BlockWrapper> newQueue = Queues.newConcurrentLinkedQueue();
        blockWrapper.setTime(now);
        newQueue.add(blockWrapper);
        syncMap.merge(refKey, newQueue,
                (oldQ, newQ) -> {
                    for(BlockWrapper b : oldQ) {
                        if (equalBytes(b.getBlock().getHashLow(), blockWrapper.getBlock().getHashLow())) {
                            // after 64 sec must resend block request
                            if(now - b.getTime() > 64 * 1000) {
                                b.setTime(now);
                                r.set(true);
                            } else {
                                r.set(false);
                            }
                            return oldQ;
                        }
                    }
                    oldQ.add(blockWrapper);
                    r.set(true);
                    kernel.getNetStatus().incWaitsync();
                    return oldQ;
                });
        return r.get();
    }

    public ImportResult syncPopBlock(BlockWrapper blockWrapper) {
        Block block = blockWrapper.getBlock();
        AtomicReference<ImportResult> result = new AtomicReference<>();
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHashLow());
        // 把所有block为parent的区块重新进行添加
        syncMap.computeIfPresent(key, (k, v)->{
            // TODO this maybe issue
//            v.stream().forEach(bw -> {
//                //blockQueue.add(bw);
//                ImportResult result = produceQueue(bw);
//            });
            BlockWrapper b = v.peek();
            ImportResult r = produceQueue(b);
            if(r == IMPORTED_BEST || r == IMPORTED_NOT_BEST || r == EXIST) {
                v.remove(b);
                kernel.getNetStatus().decWaitsync();
                syncPopBlock(b);
            }
            result.set(r);
            return v;
        });
        return result.get();
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
    }

    public void distributeBlock(BlockWrapper blockWrapper) {
        channelMgr.onNewForeignBlock(blockWrapper);
    }

}
