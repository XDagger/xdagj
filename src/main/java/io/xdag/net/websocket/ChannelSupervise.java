package io.xdag.net.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ChannelSupervise {// supervise channel
    private static final ChannelGroup GlobalGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private static final ConcurrentMap<String, ChannelId> ChannelMap = new ConcurrentHashMap<>();

    public static void addChannel(Channel channel, String tag) {
        GlobalGroup.add(channel);
        ChannelMap.put(tag, channel.id());
    }

    public static void removeChannel(Channel channel, String tag) {
        GlobalGroup.remove(channel);
        ChannelMap.remove(tag);
    }

    public static Channel findChannel(String id) {
        return GlobalGroup.find(ChannelMap.get(id));
    }

    public static String showChannel() {
        StringBuilder sb = new StringBuilder();
        // 遍历 ChannelMap 中的键值对并将它们添加到 StringBuilder
        for (ConcurrentMap.Entry<String, ChannelId> entry : ChannelMap.entrySet()) {
            String key = entry.getKey();
            ChannelId value = entry.getValue();
            String host = findChannel(key).remoteAddress().toString();
            sb.append("PoolTag: ").append(key).append(", PoolIP: ").append(host).append(", ChannelId: ").append(value).append("\n");
        }
        return sb.toString();
    }

    public static void send2Pools(TextWebSocketFrame tws) {
        if (!ChannelMap.isEmpty()) {
            log.debug("There are active mining pools:" + showChannel());
            GlobalGroup.writeAndFlush(tws);
        } else {
            log.debug("No active pools.");
        }
    }
}