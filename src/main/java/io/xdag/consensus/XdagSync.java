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
import static io.xdag.config.Constants.REQUEST_WAIT;

import com.google.common.util.concurrent.SettableFuture;
import io.xdag.Kernel;
import io.xdag.db.BlockStore;
import io.xdag.net.Channel;
import io.xdag.net.manager.XdagChannelManager;

import java.nio.ByteOrder;
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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

@Slf4j
public class XdagSync {

    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "sync(request)-" + cnt.getAndIncrement());
        }
    };

    private final XdagChannelManager channelMgr;
    private final BlockStore blockStore;
    private final ScheduledExecutorService sendTask;
    @Getter
    private final ConcurrentHashMap<Long, SettableFuture<Bytes>> sumsRequestMap;
    @Getter
    private final ConcurrentHashMap<Long, SettableFuture<Bytes>> blocksRequestMap;

    @Getter@Setter
    private Status status;
    private ScheduledFuture<?> sendFuture;
    private volatile boolean isRunning;

    public XdagSync(Kernel kernel) {
        this.channelMgr = kernel.getChannelMgr();
        this.blockStore = kernel.getBlockStore();
        sendTask = new ScheduledThreadPoolExecutor(1, factory);
        sumsRequestMap = new ConcurrentHashMap<>();
        blocksRequestMap = new ConcurrentHashMap<>();
    }

    /**
     * 不断发送send request
     */
    public void start() {
        if (status != Status.SYNCING) {
            isRunning = true;
            status = Status.SYNCING;
            // TODO: paulochen 开始同步的时间点/快照时间点
//            startSyncTime = 1588687929343L; // 1716ffdffff 171e52dffff
            sendFuture = sendTask.scheduleAtFixedRate(this::syncLoop, 64, 64, TimeUnit.SECONDS);
        }
    }

    private void syncLoop() {
        log.debug("SyncLoop...");
        try {
            // TODO: paulochen 开始同步的时间点/快照时间点
            log.debug("sync status:{}", status);
            requestBlocks(0, 1L << 48);
        } catch (Throwable e) {
            log.error("error when requestBlocks {}", e.getMessage());
        }
        log.debug("End syncLoop");
    }

    private void requestBlocks(long t, long dt) {
        // 如果当前状态不是sync start
        if (status != Status.SYNCING) {
            return;
        }
        List<Channel> any = getAnyNode();
        long randomSeq;
        SettableFuture<Bytes> sf = SettableFuture.create();
        if (any != null && any.size() != 0) {
            // TODO:随机选一个
            int index = RandomUtils.nextInt() % any.size();
            Channel xc = any.get(index);
            if (dt <= REQUEST_BLOCKS_MAX_TIME) {
                randomSeq = xc.getXdag().sendGetBlocks(t, t + dt);
                blocksRequestMap.put(randomSeq, sf);
                try {
                    sf.get(REQUEST_WAIT, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    blocksRequestMap.remove(randomSeq);
                    log.error(e.getMessage(), e);
                    return;
                }
                blocksRequestMap.remove(randomSeq);
            } else {
                MutableBytes lSums = MutableBytes.create(256);
                Bytes rSums;
                if (blockStore.loadSum(t, t + dt, lSums) <= 0) {
                    return;
                }
                randomSeq = xc.getXdag().sendGetSums(t, t + dt);
                sumsRequestMap.put(randomSeq, sf);
                try {
                    Bytes sums = sf.get(REQUEST_WAIT, TimeUnit.SECONDS);
                    rSums = sums.copy();
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    sumsRequestMap.remove(randomSeq);
                    log.error(e.getMessage(), e);
                    return;
                }
                sumsRequestMap.remove(randomSeq);
                dt >>= 4;
                for (int i = 0; i < 16; i++) {
                    long lSumsSum = lSums.getLong(i * 16, ByteOrder.LITTLE_ENDIAN);
                    long lSumsSize = lSums.getLong(i * 16 + 8, ByteOrder.LITTLE_ENDIAN);
                    long rSumsSum = rSums.getLong(i * 16, ByteOrder.LITTLE_ENDIAN);
                    long rSumsSize = rSums.getLong(i * 16 + 8, ByteOrder.LITTLE_ENDIAN);

                    if (lSumsSize != rSumsSize || lSumsSum != rSumsSum) {
                        requestBlocks(t + i * dt, dt);
                    }
                }
            }
        }
    }

    public List<Channel> getAnyNode() {
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
                log.error(e.getMessage(), e);
            }
            isRunning = false;
            log.debug("Sync Stop");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public enum Status {
        /**
         * syncing
         */
        SYNCING, SYNC_DONE
    }
}
