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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.net.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageQueue {
    public static final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            new BasicThreadFactory.Builder()
                    .namingPattern("MessageQueueTimer-thread-%d")
                    .daemon(true)
                    .build());
    private final Queue<Message> requestQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Message> respondQueue = new ConcurrentLinkedQueue<>();
    private final Channel channel;
    boolean isRunning = false;
    private ChannelHandlerContext ctx = null;
    private ScheduledFuture<?> timerTask;

    public MessageQueue(Channel channel) {
        this.channel = channel;
    }

    public void activate(ChannelHandlerContext ctx) {
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
                SEND_PERIOD,
                // 10 MILLISECONDS
                TimeUnit.MILLISECONDS);
    }


    private void nudgeQueue() {
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
            if (respondMsg != null) {
                ctx.write(respondMsg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
            if (requestMsg != null) {
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

    public int size() {
        return requestQueue.size() + respondQueue.size();
    }
}
