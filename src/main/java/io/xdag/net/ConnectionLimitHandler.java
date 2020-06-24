/**
 * Copyright (c) 2017-2020 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package io.xdag.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionLimitHandler.class);

    private static final Map<InetAddress, AtomicInteger> connectionCount = new ConcurrentHashMap<>();

    private final int maxInboundConnectionsPerIp;

    /**
     * The constructor of ConnectionLimitHandler.
     *
     * @param maxConnectionsPerIp
     *            Maximum allowed connections of each unique IP address.
     */
    public ConnectionLimitHandler(int maxConnectionsPerIp) {
        this.maxInboundConnectionsPerIp = maxConnectionsPerIp;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        AtomicInteger cnt = connectionCount.computeIfAbsent(address, k -> new AtomicInteger(0));
        if (cnt.incrementAndGet() > maxInboundConnectionsPerIp) {
            logger.debug("Too many connections from {}", address.getHostAddress());
            ctx.close();
        } else {
            super.channelActive(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        AtomicInteger cnt = connectionCount.computeIfAbsent(address, k -> new AtomicInteger(0));
        if (cnt.decrementAndGet() <= 0) {
            connectionCount.remove(address);
        }

        super.channelInactive(ctx);
    }

    /**
     * Get the connection count of an address
     *
     * @param address
     *            an IP address
     * @return current connection count
     */
    public static int getConnectionsCount(InetAddress address) {
        AtomicInteger cnt = connectionCount.get(address);
        return cnt == null ? 0 : cnt.get();
    }

    /**
     * Check whether there is a counter of the provided address.
     *
     * @param address
     *            an IP address
     * @return whether there is a counter of the address.
     */
    public static boolean containsAddress(InetAddress address) {
        return connectionCount.get(address) != null;
    }

    /**
     * Reset connection count
     */
    public static void reset() {
        connectionCount.clear();
    }
}
