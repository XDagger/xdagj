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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.net.XdagChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j
public class MessageQueue {

    private static AtomicInteger cnt = new AtomicInteger(0);
    public static final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(
            4,
            new BasicThreadFactory.Builder()
                    .namingPattern("MessageQueueTimer-" + cnt.getAndIncrement())
                    .daemon(true)
                    .build());
    boolean isRunning = false;
    private Queue<MessageRoundtrip> requestQueue = new ConcurrentLinkedQueue<>();
    private Queue<MessageRoundtrip> respondQueue = new ConcurrentLinkedQueue<>();
    private ChannelHandlerContext ctx = null;
    private ScheduledFuture<?> timerTask;
    private XdagChannel channel;

    public MessageQueue(XdagChannel channel) {
        this.channel = channel;
    }

    public void receivedMessage(Message msg) { // 负责打印记录信息 实际接收信息的业务操作在xdaghandler
        if (requestQueue.peek() != null) {
            MessageRoundtrip messageRoundtrip = requestQueue.peek();
            Message waitingMessage = messageRoundtrip.getMsg();

            if (waitingMessage.getAnswerMessage() != null
                    && msg.getClass() == waitingMessage.getAnswerMessage()) {
                messageRoundtrip.answer();
                log.trace("Message round trip covered: [{}] ", messageRoundtrip.getMsg().getClass());
            }
        }
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
                10,
                // 10毫秒执行一次
                TimeUnit.MILLISECONDS);
    }

    /** 每十毫秒执行一次 */
    private void nudgeQueue() {
        removeAnsweredMessage(requestQueue.peek());
        // Now send the next message
        sendToWire(respondQueue.poll());
        sendToWire(requestQueue.peek());
    }

    public void sendMessage(Message msg) {
        if (channel.isDisconnected()) {
            log.warn(
                    "{}: attempt to send [{}] message after disconnect", channel, msg.getCommand().name());
            return;
        }

        if (msg.getAnswerMessage() != null) {

            requestQueue.add(new MessageRoundtrip(msg));
            // log.debug("add new Request message current requestQueue size
            // is:"+requestQueue.size());
        } else {
            respondQueue.add(new MessageRoundtrip(msg));
            // log.debug("add new Response message current responseQueue size
            // is:"+respondQueue.size());

        }
    }

    private void sendToWire(MessageRoundtrip messageRoundtrip) {
        if (messageRoundtrip != null && messageRoundtrip.getRetryTimes() == 0) {
            // TODO: retry logic || messageRoundtrip.hasToRetry()){
            Message msg = messageRoundtrip.getMsg();
            // log.debug("Sent to Wire with the message,msg:"+msg.getCommand());
            ctx.writeAndFlush(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            if (msg.getAnswerMessage() != null) {
                messageRoundtrip.incRetryTimes();
                messageRoundtrip.saveTime();
            }
        }
    }

    private void removeAnsweredMessage(MessageRoundtrip messageRoundtrip) {
        if (messageRoundtrip != null && messageRoundtrip.isAnswered()) {
            requestQueue.remove();
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
