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

import static io.xdag.utils.FastByteComparisons.compareTo;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.spongycastle.util.encoders.Hex;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import io.xdag.Kernel;
import io.xdag.db.SimpleFileStore;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.impl.SumReplyMessage;
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

    public XdagSync(Kernel kernel) {
        this.channelMgr = kernel.getChannelManager();
        this.simpleFileStore = kernel.getBlockStore().getSimpleFileStore();
        sendTask = new ScheduledThreadPoolExecutor(1, factory);
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

    private void requesetBlocks(long start, long timeInterval) {
        // 如果当前状态不是sync start
        if (status != Status.SYNCING) {
            return;
        }
        List<XdagChannel> any = getAnyNode();
        if (any != null && any.size() != 0) {
            for (XdagChannel channel : any) {
                log.debug("Send Request to channel");
                ListenableFuture<SumReplyMessage> futureSum = channel.getXdag().sendGetsums(start,
                        start + timeInterval);
                if (futureSum != null) {
                    Futures.addCallback(
                            futureSum,
                            new SumCallback(channel, start, start + timeInterval),
                            MoreExecutors.directExecutor());
                }
            }
        }
    }

    /**
     * 处理响应 在响应的时间范围在1<<20的时候就可以发送请求区块了，其他时间慢慢缩短所需要的区块范围
     *
     * @param reply
     * @param channel
     */
    public void processSumsReply(
            SumReplyMessage reply, XdagChannel channel, long starttime, long endtime) {
        log.debug("processSumsReply");
        log.debug("响应endtime " + reply.getEndtime() + "请求endtime " + endtime);
        if (endtime != reply.getEndtime()) {
            log.debug("此响应与请求不匹配" + Hex.toHexString(reply.getEncoded()));
            return;
        }
        long dt = endtime - starttime;
        // 如果请求时间区域过大
        dt >>= 4;
        byte[] lsums = simpleFileStore.loadSum(starttime, endtime);
        byte[] rsums = reply.getSum();
        log.debug("lsum is " + Hex.toHexString(lsums));
        log.debug("rsum is " + Hex.toHexString(rsums));
        for (int i = 0; i < 16; i++) {
            if ((compareTo(lsums, i * 16, 8, rsums, i * 16, 8) != 0)
                    || (compareTo(lsums, i * 16 + 8, 8, rsums, i * 16 + 8, 8) != 0)) {
                log.debug("第" + i + "次请求");
                // 请求request
                ListenableFuture<SumReplyMessage> future = channel.getXdag().sendGetsums(starttime + i * (dt),
                        starttime + i * (dt) + dt);
                if (future != null) {
                    Futures.addCallback(
                            future,
                            new SumCallback(channel, starttime + i * (dt), starttime + i * (dt) + dt),
                            MoreExecutors.directExecutor());
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

    class SumCallback implements FutureCallback<SumReplyMessage> {
        private XdagChannel channel;
        private long starttime;
        private long endtime;

        public SumCallback(XdagChannel channel, long starttime, long endtime) {
            this.channel = channel;
            this.starttime = starttime;
            this.endtime = endtime;
        }

        @Override
        public void onSuccess(SumReplyMessage reply) {
            processSumsReply(reply, channel, starttime, endtime);
        }

        @Override
        public void onFailure(@Nonnull Throwable t) {
            log.debug("{}: Error receiving Sums. Dropping the peer.", "Sync", t);
            channel.getXdag().dropConnection();
        }
    }
}
