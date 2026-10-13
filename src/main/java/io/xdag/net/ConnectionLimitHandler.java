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

package io.xdag.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler to limit the number of concurrent connections from a single IP address
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    // Map to track connection count per IP address
    private static final Map<String, AtomicInteger> connectionCount = new ConcurrentHashMap<>();

    // Maximum allowed inbound connections per IP
    private final int maxInboundConnectionsPerIp;

    /**
     * Creates a new connection limit handler
     * 
     * @param maxConnectionsPerIp Maximum allowed connections from each unique IP address
     */
    public ConnectionLimitHandler(int maxConnectionsPerIp) {
        this.maxInboundConnectionsPerIp = maxConnectionsPerIp;
    }

    /**
     * Gets the current connection count for an IP address
     *
     * @param address The IP address to check
     * @return Current number of connections from this IP
     */
    public static int getConnectionsCount(InetAddress address) {
        AtomicInteger cnt = connectionCount.get(address.getHostAddress());
        return cnt == null ? 0 : cnt.get();
    }

    /**
     * Checks if an IP address has any active connections
     *
     * @param address The IP address to check
     * @return true if the IP has active connections, false otherwise
     */
    public static boolean containsAddress(InetAddress address) {
        return connectionCount.get(address.getHostAddress()) != null;
    }

    /**
     * Resets all connection counters
     */
    public static void reset() {
        connectionCount.clear();
    }

    /**
     * Handles new channel connections
     * Increments connection counter and closes connection if limit exceeded
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        AtomicInteger cnt = connectionCount.computeIfAbsent(address.getHostAddress(), k -> new AtomicInteger(0));
        if (cnt.incrementAndGet() > maxInboundConnectionsPerIp) {
            log.debug("Too many connections from: {}", address.getHostAddress());
            ctx.close();
        } else {
            super.channelActive(ctx);
        }
    }

    /**
     * Handles channel disconnections
     * Decrements connection counter and removes IP from tracking if count reaches 0
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        AtomicInteger cnt = connectionCount.computeIfAbsent(address.getHostAddress(), k -> new AtomicInteger(0));
        if (cnt.decrementAndGet() <= 0) {
            connectionCount.remove(address.getHostAddress());
        }
        log.debug("Inactive channel with {}", address.getHostAddress());
        super.channelInactive(ctx);
    }
}
