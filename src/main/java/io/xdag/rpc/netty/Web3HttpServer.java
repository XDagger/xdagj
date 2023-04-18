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

package io.xdag.rpc.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.rpc.cors.CorsConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class Web3HttpServer {

    private final InetAddress bindAddress;
    private final int port;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final int socketLinger;
    private final boolean reuseAddress;
    private final CorsConfiguration corsConfiguration;
    private final JsonRpcWeb3FilterHandler jsonRpcWeb3FilterHandler;
    private final JsonRpcWeb3ServerHandler jsonRpcWeb3ServerHandler;

    public Web3HttpServer(InetAddress bindAddress,
            int port,
            int socketLinger,
            boolean reuseAddress,
            CorsConfiguration corsConfiguration,
            JsonRpcWeb3FilterHandler jsonRpcWeb3FilterHandler,
            JsonRpcWeb3ServerHandler jsonRpcWeb3ServerHandler) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.socketLinger = socketLinger;
        this.reuseAddress = reuseAddress;
        this.corsConfiguration = corsConfiguration;
        this.jsonRpcWeb3FilterHandler = jsonRpcWeb3FilterHandler;
        this.jsonRpcWeb3ServerHandler = jsonRpcWeb3ServerHandler;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    public void start() {
        log.info("RPC HTTP enabled");

        ServerBootstrap b = new ServerBootstrap();
        b.option(ChannelOption.SO_REUSEADDR, reuseAddress);
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpRequestDecoder());
                        p.addLast(new HttpResponseEncoder());
                        p.addLast(new HttpObjectAggregator(1024 * 1024 * 5));
                        p.addLast(new HttpContentCompressor());
                        if (corsConfiguration.hasHeader()) {
                            p.addLast(new CorsHandler(
                                    CorsConfigBuilder.forOrigin(corsConfiguration.getHeader())
                                            .allowedRequestHeaders(HttpHeaderNames.CONTENT_TYPE)
                                            .allowedRequestMethods(HttpMethod.POST)
                                            .build())
                            );
                        }
                        p.addLast(jsonRpcWeb3FilterHandler);
                        p.addLast(new Web3HttpMethodFilterHandler());
                        p.addLast(jsonRpcWeb3ServerHandler);
                        p.addLast(new Web3ResultHttpResponseHandler());
                    }
                });
        try {
            b.bind(bindAddress, port).sync();
        } catch (InterruptedException e) {
            log.error("The RPC HTTP server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
