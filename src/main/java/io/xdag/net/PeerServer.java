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

import org.apache.commons.lang3.SystemUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.NettyRuntime;
import io.xdag.DagKernel;
import io.xdag.utils.NettyUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PeerServer {
    private final DagKernel kernel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private final int workerThreadPoolSize = NettyRuntime.availableProcessors() * 2;

    public PeerServer(DagKernel kernel) {
        this.kernel = kernel;
    }

    public void start() {
        start(kernel.getConfig().getNodeSpec().getNodeIp(), kernel.getConfig().getNodeSpec().getNodePort());
    }

    public void start(String ip, int port) {
        try {
            if(SystemUtils.IS_OS_LINUX) {
                bossGroup = new EpollEventLoopGroup();
                workerGroup = new EpollEventLoopGroup(workerThreadPoolSize);
            } else if(SystemUtils.IS_OS_MAC) {
                bossGroup = new KQueueEventLoopGroup();
                workerGroup = new KQueueEventLoopGroup(workerThreadPoolSize);

            } else {
                bossGroup = new NioEventLoopGroup();
                workerGroup = new NioEventLoopGroup(workerThreadPoolSize);
            }
            ServerBootstrap b = NettyUtils.nativeEventLoopGroup(bossGroup, workerGroup);
            b.childOption(ChannelOption.TCP_NODELAY, true);
            b.childOption(ChannelOption.SO_KEEPALIVE, true);
            b.childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
            b.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, kernel.getConfig().getNodeSpec().getConnectionTimeout());
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
