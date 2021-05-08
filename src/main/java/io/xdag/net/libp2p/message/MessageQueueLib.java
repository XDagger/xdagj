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
package io.xdag.net.libp2p.message;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.net.libp2p.Libp2pChannel;
import io.xdag.net.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MessageQueueLib {
    private static AtomicInteger cnt = new AtomicInteger(0);
    boolean isRunning = false;
    public static final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(
            4,
            new BasicThreadFactory.Builder()
                    .namingPattern("MessageQueueTimer-" + cnt.getAndIncrement())
                    .daemon(true)
                    .build());
    private ChannelHandlerContext ctx = null;
    private final Queue<Message> requestQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Message> respondQueue = new ConcurrentLinkedQueue<>();
    private ScheduledFuture<?> timerTask;
    private Libp2pChannel channel;

    public MessageQueueLib(Libp2pChannel channel) {
        this.channel = channel;
    }
    public void activate(ChannelHandlerContext ctx) {
        /**处理发送的区块*/
        this.ctx = ctx;
        isRunning = true;
        timerTask = timer.scheduleAtFixedRate(
                () -> {
                    try {
                        nudgeQueue();
                    } catch (Throwable t) {
                        log.error("Unhandled exception", t);
                    }
                },
                10,
                10,
                // 10毫秒执行一次
                TimeUnit.MILLISECONDS);
    }
    /**发送区块*/
    private void nudgeQueue() {
        // 1000 / 10 * 5 * 2 = 1000 messages per second
        int n = Math.min(5, size());
        if (n == 0) {
            return;
        }
        // write out n messages
        for (int i = 0; i < n; i++) {
            // Now send the next message
            // log.debug("Sent to Wire with the message,msg:"+msg.getCommand());
            Message respondMsg = respondQueue.poll();
            Message requestMsg = requestQueue.poll();
            if(respondMsg != null) {
                ctx.write(respondMsg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
            if(requestMsg != null) {
                ctx.write(requestMsg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }

        }
        ctx.flush();
    }

    public void sendMessage(Message msg) {
        if (channel.isDisconnected()) {
            log.warn("{}: attempt to send [{}] message after disconnect", channel, msg.getCommand().name());
            return;
        }

        if (msg.getAnswerMessage() != null) {
            requestQueue.add(msg);
        } else {
            respondQueue.add(msg);
        }
    }




    public void disconnect() {
        ctx.close();
    }

    public void close() {
        isRunning = false;
        if (timerTask != null) {
            timerTask.cancel(false);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isIdle() {
        return size() == 0;
    }

    public int size() {
        return requestQueue.size() + respondQueue.size();
    }
}
