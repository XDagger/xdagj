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
package io.xdag.pool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.xdag.Kernel;
import io.xdag.core.AbstractXdagLifecycle;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Getter
@Slf4j
public class WebSocketServer extends AbstractXdagLifecycle {

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ChannelFuture webSocketChannel;
    private final PoolHandShakeHandler poolHandShakeHandler;

    public WebSocketServer(Kernel kernel, List<String> poolWhiteIPList, int port) {
        this.bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        this.poolHandShakeHandler = new PoolHandShakeHandler(kernel, poolWhiteIPList, port);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast("http-codec", new HttpServerCodec());// http decoder
                        ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
                        ch.pipeline().addLast("handler", poolHandShakeHandler);// pool handler write by ourselves
                    }
                });
        this.webSocketChannel = b.bind(port);
    }

    @Override
    protected void doStart() {
        log.info("Pool WebSocket enabled");
        try {
            ChannelFuture future = null;
            if (webSocketChannel != null) {
                future = webSocketChannel.sync();
            }
            if (future != null) {
                future.addListener((ChannelFutureListener) cf -> {
                    if (cf.isSuccess()) {
                        log.info("Pool WebSocket server started successfully");
                    } else {
                        log.error("Failed to start the Pool WebSocket server", cf.cause());
                    }
                });
            }
        } catch (InterruptedException e) {
            log.error("The Pool WebSocket server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void doStop() {
        try {
            ChannelFuture webSocketChannelFuture = this.webSocketChannel;
            if (webSocketChannelFuture != null) {
                webSocketChannelFuture.channel().close().sync();
            }

            this.bossGroup.shutdownGracefully().sync();
            this.workerGroup.shutdownGracefully().sync();

            log.info("Pool WebSocket server stopped successfully.");
        } catch (InterruptedException e) {
            log.error("Couldn't stop the Pool WebSocket server", e);
            Thread.currentThread().interrupt();
        }
    }

}

