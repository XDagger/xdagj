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

package io.xdag.net.message;

import static io.xdag.config.Constants.SEND_PERIOD;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.config.Config;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import io.xdag.net.message.p2p.DisconnectMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j
public class MessageQueue {
    public static final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            new BasicThreadFactory.Builder()
                    .namingPattern("MessageQueueTimer-thread-%d")
                    .daemon(true)
                    .build());
    private final Config config;
    //'8192' is a value obtained from testing experience, not a standard value.Looking forward to optimization.
    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>(8192);
    private final Queue<Message> prioritized = new ConcurrentLinkedQueue<>();
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> timerTask;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public MessageQueue(Config config) {
        this.config = config;
    }

    public synchronized void activate(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        timerTask = timer.scheduleAtFixedRate(
                () -> {
                    try {
                        nudgeQueue();
                    } catch (Throwable t) {
                        log.error("Unhandled exception", t);
                    }
                },
                10,
                SEND_PERIOD,
                // 10 MILLISECONDS
                TimeUnit.MILLISECONDS);
    }

    public synchronized void deactivate() {
        this.timerTask.cancel(false);
    }

    public boolean isIdle() {
        return size() == 0;
    }

    public void disconnect(ReasonCode code) {
        log.debug("Actively closing the connection: reason = {}", code);

        // avoid repeating close requests
        if (isClosed.compareAndSet(false, true)) {
            ctx.writeAndFlush(new DisconnectMessage(code)).addListener((ChannelFutureListener) future -> ctx.close());
        }
    }

    public void sendMessage(Message msg) {
    //when full message queue, whitelist don't need to disconnect.
        if (config.getNodeSpec().getNetPrioritizedMessages().contains(msg.getCode())) {
            prioritized.add(msg);
        } else {
            try {
                //update to BlockingQueue, capacity 8192
                queue.put(msg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int size() {
        return queue.size() + prioritized.size();
    }

    private void nudgeQueue() {
        //Increase bandwidth consumption of a full used single sync thread to 3 Mbps.
        int n = Math.min(8, size());
        if (n == 0) {
            return;
        }
        // write out n messages
        for (int i = 0; i < n; i++) {
            Message msg = !prioritized.isEmpty() ? prioritized.poll() : queue.poll();

            log.trace("Wiring message: {}", msg);
            ctx.write(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
        ctx.flush();
    }
}
