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