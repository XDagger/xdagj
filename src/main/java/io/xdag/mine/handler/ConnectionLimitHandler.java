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

package io.xdag.mine.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Map<String, AtomicInteger> connectionCount = new ConcurrentHashMap<>();

    private final int maxInboundConnectionsPerIp;

    /**
     * @param maxConnectionsPerIp Maximum allowed connections of each unique IP address.
     */
    public ConnectionLimitHandler(int maxConnectionsPerIp) {
        this.maxInboundConnectionsPerIp = maxConnectionsPerIp;
    }

    /**
     * Get the connection count of an address
     *
     * @param address an IP address
     * @return current connection count
     */
    public static int getConnectionsCount(InetAddress address) {
        AtomicInteger cnt = connectionCount.get(address.getHostAddress());
        return cnt == null ? 0 : cnt.get();
    }

    /**
     * Check whether there is a counter of the provided address.
     *
     * @param address an IP address
     * @return whether there is a counter of the address.
     */
    public static boolean containsAddress(InetAddress address) {
        return connectionCount.get(address.getHostAddress()) != null;
    }

    /**
     * Reset connection count
     */
    public static void reset() {
        connectionCount.clear();
    }

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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        AtomicInteger cnt = connectionCount.computeIfAbsent(address.getHostAddress(), k -> new AtomicInteger(0));
        if (cnt.decrementAndGet() <= 0) {
            connectionCount.remove(address.getHostAddress());
        }
        log.debug("Inactive channel with Address:{}",address.getHostAddress());
        super.channelInactive(ctx);
    }
}
