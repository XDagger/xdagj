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
package io.xdag.mine;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.Kernel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerServer {
    protected Kernel kernel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /** 用来接受监听的fuyire */
    private ChannelFuture channelFuture;

    /** 是否正在监听 */
    private boolean isListening = false;

    public MinerServer(Kernel kernel) {
        this.kernel = kernel;
    }

    /** 开启监听的事件 */
    public void start() {
        start(kernel.getConfig().getPoolIp(), kernel.getConfig().getPoolPort());
    }

    public void start(String ip, int port) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.childOption(
                    ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
            bootstrap.childOption(
                    ChannelOption.CONNECT_TIMEOUT_MILLIS, kernel.getConfig().getConnectionTimeout());
            // 这个是这是可以远程主动关闭？
            // bootstrap.childOption(ChannelOption.ALLOW_HALF_CLOSURE,true);
            bootstrap.handler(new LoggingHandler());
            bootstrap.childHandler(new MinerChannelInitializer(kernel, true));
            channelFuture = bootstrap.bind(ip, port).sync();
            isListening = true;
            // channelFuture.channel().closeFuture().sync();
            log.info("start listening the pool,host:[{}:{}]", ip, port);
        } catch (Exception e) {
            log.error("miner server error: {} ({})", e.getMessage(), e.getClass().getName());
            throw new Error("minerServer Disconnected");
        }
    }

    /** 关闭连接 */
    public void close() {
        if (isListening && channelFuture != null && channelFuture.channel().isOpen()) {
            try {
                log.info("Closing MinerServer...");
                channelFuture.channel().close().sync();
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
                isListening = false;
                log.info("MinerServer closed.");
            } catch (Exception e) {
                log.warn("Problems closing server channel", e);
            }
        }
    }
}
