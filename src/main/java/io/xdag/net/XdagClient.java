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

import java.io.IOException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.config.Config;
import io.xdag.net.handler.XdagChannelInitializer;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagClient {
    private static final ThreadFactory factory = new ThreadFactory() {
        final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "client-" + cnt.getAndIncrement());
        }
    };

    private final EventLoopGroup workerGroup;
    private final int port;
    private final Config config;
    private ChannelFuture f;
    private final String ip;
    private Node node;

    public XdagClient(Config config) {
        this.config = config;
        this.ip = config.getNodeIp();
        this.port = config.getNodePort();
        this.workerGroup = new NioEventLoopGroup(0, factory);
//        log.debug("XdagClient nodeId {}", getNode().getHexId());
    }

    /** Connects to the node and returns only upon connection close */
    public void connect(String host, int port, XdagChannelInitializer xdagChannelInitializer) {
        try {
            f = connectAsync(host, port, xdagChannelInitializer);
            f.sync();
        } catch (Exception e) {
            if (e instanceof IOException) {
                log.debug("XdagClient: Can't connect to " + host + ":" + port + " (" + e.getMessage() + ")");
            } else {
                log.error("message:" + e.getMessage(), e);
            }
        }
    }

    public ChannelFuture connectAsync(
            String host, int port, XdagChannelInitializer xdagChannelInitializer) {
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout());
        b.remoteAddress(host, port);
        b.handler(xdagChannelInitializer);
        return b.connect();
    }

    public void close() {
        log.debug("Shutdown XdagClient");
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }

    public Node getNode() {
        if (node == null) {
            node = new Node(ip, port);
        }
        return node;
    }
}
