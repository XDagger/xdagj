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
package io.xdag.net.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@ChannelHandler.Sharable
public class ChannelSupervise {// supervise channel
    private static final ChannelGroup GlobalGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private static final ConcurrentMap<ChannelId, String> ChannelMap = new ConcurrentHashMap<>();

    public static void addChannel(Channel channel) {
        GlobalGroup.add(channel);
        ChannelMap.put(channel.id(), channel.remoteAddress().toString());
    }

    public static void removeChannel(Channel channel) {
        GlobalGroup.remove(channel);
        ChannelMap.remove(channel.id());
    }

    public static String showChannel() {
        StringBuilder sb = new StringBuilder();
        // Loop through the key-value pairs in the ChannelMap and add them to the StringBuilder
        for (ConcurrentMap.Entry<ChannelId, String> entry : ChannelMap.entrySet()) {
            ChannelId key = entry.getKey();
            String value = entry.getValue();
            sb.append("PoolIP: ").append(value).append(", ChannelId: ").append(key).append("\n");
        }
        return sb.toString();
    }

    public static String findChannel(ChannelId id) {
        return GlobalGroup.find(id).toString();
    }

    public static void send2Pools(String info) {
        if (!ChannelMap.isEmpty()) {
            log.debug("There are active mining pools: {}", showChannel());
            GlobalGroup.writeAndFlush(new TextWebSocketFrame(info));
            log.debug("Send info to pools successfully. Info: {}", info);
        } else {
            log.debug("No active pools.");
        }
    }
}