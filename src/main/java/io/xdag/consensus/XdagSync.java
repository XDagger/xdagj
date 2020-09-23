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

import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.db.SimpleFileStore;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
    private SimpleFileStore simpleFileStore;
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
        this.simpleFileStore = kernel.getBlockStore().getSimpleFileStore();
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
        log.debug("Synchronization");
        requesetBlocks(0, 1L << 48);
    }

    private void requesetBlocks(long t, long dt) {
        // 如果当前状态不是sync start
        if (status != Status.SYNCING) {
            return;
        }
        List<XdagChannel> any = getAnyNode();
        long randomSeq = 0;
        SettableFuture<byte[]> sf = SettableFuture.create();
        if (any != null && any.size() != 0) {
            XdagChannel xc = any.get(0);
            if (dt <= REQUEST_BLOCKS_MAX_TIME) {
                randomSeq =  xc.getXdag().sendGetblocks(t, t + dt);
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
                byte[] lsums = new byte[256];
                byte[] rsums = null;
                if(simpleFileStore.loadSum(t, t + dt, lsums) <= 0) {
                    return;
                }
                log.debug("lsum is " + Hex.toHexString(lsums));
                randomSeq = xc.getXdag().sendGetsums(t, t + dt);
                sumsRequestMap.put(randomSeq, sf);
                log.debug("sendGetsums seq:{}.", randomSeq);
                try {
                    byte[] sums = sf.get(128, TimeUnit.SECONDS);
                    rsums = Arrays.copyOf(sums, 256);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    sumsRequestMap.remove(randomSeq);
                    log.error(e.getMessage(), e);
                    return;
                }
                sumsRequestMap.remove(randomSeq);
                log.debug("rsum is " + Hex.toHexString(rsums));
                dt >>= 4;
                for (int i = 0; i < 16; i++) {
                    long lsumsSum = BytesUtils.bytesToLong(lsums, i * 16, true);
                    long lsumsSize = BytesUtils.bytesToLong(lsums, i * 16 + 8, true);
                    long rsumsSum = BytesUtils.bytesToLong(rsums, i * 16, true);
                    long rsumsSize = BytesUtils.bytesToLong(rsums, i * 16 + 8, true);

                    if (lsumsSize != rsumsSize || lsumsSum != rsumsSum) {
                        requesetBlocks(t + i * dt, dt);
                    }
                }
            }
        }
    }

    public List<XdagChannel> getAnyNode() {
        return channelMgr.getActiveChannels();
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
        SYNCING, SYNCDONE
    }
}
