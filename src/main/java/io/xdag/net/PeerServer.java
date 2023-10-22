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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.DagKernel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PeerServer {

    private static final ThreadFactory factory = new ThreadFactory() {
        final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "server-" + cnt.getAndIncrement());
        }
    };
    private final DagKernel kernel;
    private Channel channel;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public PeerServer(DagKernel kernel) {
        this.kernel = kernel;
    }

    public void start() {
        start(kernel.getConfig().getNodeSpec().getNodeIp(), kernel.getConfig().getNodeSpec().getNodePort());
    }

    public void start(String ip, int port) {
        try {
            bossGroup = new NioEventLoopGroup(1, factory);
            workerGroup = new NioEventLoopGroup(4, factory);

            ServerBootstrap b = new ServerBootstrap();

            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);

            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
            b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, kernel.getConfig().getNodeSpec().getConnectionTimeout());

            b.handler(new LoggingHandler());
            b.childHandler(new XdagChannelInitializer(kernel, null));

            log.debug("Xdag Node start host:[{}:{}].", ip, port);
            channel = b.bind(ip, port).sync().channel();
        } catch (Exception e) {
            log.error("Xdag Node start error:{}.", e.getMessage(), e);
        }
    }

    public void stop() {
        if (isRunning() && channel.isOpen()) {
            try {
                channel.close().sync();

                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();

                ConnectionLimitHandler.reset();

                channel = null;
            } catch (Exception e) {
                log.error("Failed to close channel", e);
            }
            log.info("PeerServer shut down");
        }
    }

    public boolean isRunning() {
        return channel != null;
    }

}
