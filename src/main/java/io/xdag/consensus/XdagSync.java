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

import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import com.google.common.util.concurrent.*;

import io.xdag.libp2p.Libp2pChannel;
import io.xdag.libp2p.manager.ChannelManager;

import io.xdag.Kernel;
import io.xdag.db.store.BlockStore;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class XdagSync {

    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "sync(request)-" + cnt.getAndIncrement());
        }
    };

    private XdagChannelManager channelMgr;
    private ChannelManager channelManager;
    private BlockStore blockStore;
    private Status status;
    private ScheduledExecutorService sendTask;
    private ScheduledFuture<?> sendFuture;
    private volatile boolean isRunning;

    @Getter
    private ConcurrentHashMap<Long, SettableFuture<byte[]>> sumsRequestMap;

    @Getter
    private ConcurrentHashMap<Long, SettableFuture<byte[]>> blocksRequestMap;


    public XdagSync(Kernel kernel) {
        this.channelMgr = kernel.getChannelMgr();
        this.channelManager = kernel.getChannelManager();
        this.blockStore = kernel.getBlockStore();
        sendTask = new ScheduledThreadPoolExecutor(1, factory);
        sumsRequestMap = new ConcurrentHashMap<>();
        blocksRequestMap = new ConcurrentHashMap<>();
    }

    /** 不断发送send request */
    public void start() {
        if (status != Status.SYNCING) {
            isRunning = true;
            status = Status.SYNCING;
            sendFuture = sendTask.scheduleAtFixedRate(this::syncLoop, 64, 64, TimeUnit.SECONDS);
        }
    }

    private void syncLoop() {
        log.info("start syncLoop");
        try {
            requestBlocks(0, 1L << 48);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
        log.info("end syncLoop");
    }

    private void requestBlocks(long t, long dt) {
        // 如果当前状态不是sync start
        if (status != Status.SYNCING) {
            return;
        }
        List<XdagChannel> any = getAnyNode();
        long randomSeq;
        SettableFuture<byte[]> sf = SettableFuture.create();
        if (any != null && any.size() != 0) {
            XdagChannel xc = any.get(0);
            if (dt <= REQUEST_BLOCKS_MAX_TIME) {
                randomSeq =  xc.getXdag().sendGetBlocks(t, t + dt);
                blocksRequestMap.put(randomSeq, sf);
                try {
                    sf.get(128, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    blocksRequestMap.remove(randomSeq);
                    log.error(e.getMessage(), e);
                    return;
                }
                blocksRequestMap.remove(randomSeq);
                return;
            } else {
                byte[] lSums = new byte[256];
                byte[] rSums;
                if(blockStore.loadSum(t, t + dt, lSums) <= 0) {
                    return;
                }
                log.debug("lSum is " + Hex.toHexString(lSums));
                randomSeq = xc.getXdag().sendGetSums(t, t + dt);
                sumsRequestMap.put(randomSeq, sf);
                log.debug("sendGetSums seq:{}.", randomSeq);
                try {
                    byte[] sums = sf.get(128, TimeUnit.SECONDS);
                    rSums = Arrays.copyOf(sums, 256);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    sumsRequestMap.remove(randomSeq);
                    log.error(e.getMessage(), e);
                    return;
                }
                sumsRequestMap.remove(randomSeq);
                log.debug("rSum is " + Hex.toHexString(rSums));
                dt >>= 4;
                for (int i = 0; i < 16; i++) {
                    long lSumsSum = BytesUtils.bytesToLong(lSums, i * 16, true);
                    long lSumsSize = BytesUtils.bytesToLong(lSums, i * 16 + 8, true);
                    long rSumsSum = BytesUtils.bytesToLong(rSums, i * 16, true);
                    long rSumsSize = BytesUtils.bytesToLong(rSums, i * 16 + 8, true);

                    if (lSumsSize != rSumsSize || lSumsSum != rSumsSum) {
                        requestBlocks(t + i * dt, dt);
                    }
                }
            }
        }
//        List<Libp2pChannel> any0 = getLibp2pAnyNode();
//        SettableFuture<byte[]> sf0 = SettableFuture.create();
//        long randomSeq0;
//        if (any0 != null && any0.size() != 0) {
//            Libp2pChannel channel = any0.get(0);
//            if (dt <= REQUEST_BLOCKS_MAX_TIME) {
//                randomSeq0 = channel.getHandler().getController().sendGetBlocks(t, t + dt);
//                blocksRequestMap.put(randomSeq0, sf0);
//                try {
//                    sf0.get(128, TimeUnit.SECONDS);
//                } catch (InterruptedException | ExecutionException | TimeoutException e) {
//                    blocksRequestMap.remove(randomSeq0);
//                    log.error(e.getMessage(), e);
//                    return;
//                }
//                blocksRequestMap.remove(randomSeq0);
//                return;
//            } else {
//                byte[] lSums0 = new byte[256];
//                byte[] rSums0;
//                if (blockStore.loadSum(t, t + dt, lSums0) <= 0) {
//                    return;
//                }
//                randomSeq0 = channel.getHandler().getController().sendGetSums(t , t + dt );
//                sumsRequestMap.put(randomSeq0 , sf0);
//                try{
//                    byte[] sums0 = sf0.get(128, TimeUnit.SECONDS);
//                    rSums0 = Arrays.copyOf(sums0, 256);
//                }catch (InterruptedException | ExecutionException | TimeoutException e) {
//                    sumsRequestMap.remove(randomSeq0);
//                    log.error(e.getMessage(), e);
//                    return;
//                }
//                sumsRequestMap.remove(randomSeq0);
//                dt >>= 4;
//                for (int i = 0; i < 16; i++) {
//                    long lSumsSum = BytesUtils.bytesToLong(lSums0, i * 16, true);
//                    long lSumsSize = BytesUtils.bytesToLong(lSums0, i * 16 + 8, true);
//                    long rSumsSum = BytesUtils.bytesToLong(rSums0, i * 16, true);
//                    long rSumsSize = BytesUtils.bytesToLong(rSums0, i * 16 + 8, true);
//
//                    if (lSumsSize != rSumsSize || lSumsSum != rSumsSum) {
//                        requestBlocks(t + i * dt, dt);
//                    }
//                }
//            }
//        }
    }

    public List<XdagChannel> getAnyNode() {
        return channelMgr.getActiveChannels();
    }

    public List<Libp2pChannel> getLibp2pAnyNode(){
        return channelManager.getactiveChannel();
    }


    public void stop() {
        log.debug("stop sync");
        if (isRunning) {
            try {

                if (sendFuture != null) {
                    sendFuture.cancel(true);
                }
                // 关闭线程池
                sendTask.shutdownNow();
                sendTask.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isRunning = false;
            log.debug("Sync Stop");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public enum Status {
        /** syncing */
        SYNCING, SYNC_DONE
    }
}
