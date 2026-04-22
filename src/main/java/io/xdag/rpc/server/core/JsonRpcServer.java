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
package io.xdag.rpc.server.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.config.spec.RPCSpec;
import io.xdag.rpc.api.XdagApi;
import io.xdag.rpc.server.handler.CorsHandler;
import io.xdag.rpc.server.handler.JsonRequestHandler;
import io.xdag.rpc.server.handler.JsonRpcHandler;
import io.xdag.rpc.server.handler.JsonRpcRequestHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JsonRpcServer {
    private final RPCSpec rpcSpec;
    private final XdagApi xdagApi;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;


    public JsonRpcServer(final RPCSpec rpcSpec, final XdagApi xdagApi) {
        this.rpcSpec = rpcSpec;
        this.xdagApi = xdagApi;
    }

    public void start() {
        try {
            // Create request handlers
            List<JsonRpcRequestHandler> handlers = new ArrayList<>();
            handlers.add(new JsonRequestHandler(xdagApi));

            // Create SSL context (if HTTPS is enabled)
//            final SslContext sslCtx;
//            if (rpcSpec.isRpcEnableHttps()) {
//                File certFile = new File(rpcSpec.getRpcHttpsCertFile());
//                File keyFile = new File(rpcSpec.getRpcHttpsKeyFile());
//                if (!certFile.exists() || !keyFile.exists()) {
//                    throw new RuntimeException("SSL certificate or key file not found");
//                }
//                sslCtx = SslContextBuilder.forServer(certFile, keyFile).build();
//            } else {
//                sslCtx = null;
//            }

            // Create event loop groups
            bossGroup = new MultiThreadIoEventLoopGroup(rpcSpec.getRpcHttpBossThreads(),
                NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(rpcSpec.getRpcHttpWorkerThreads(),
                NioIoHandler.newFactory());

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            
                            // SSL
//                            if (sslCtx != null) {
//                                p.addLast(sslCtx.newHandler(ch.alloc()));
//                            }

                            // HTTP codec
                            p.addLast(new HttpServerCodec());
                            // HTTP message aggregator
                            p.addLast(new HttpObjectAggregator(rpcSpec.getRpcHttpMaxContentLength()));
                            // CORS handler
                            p.addLast(new CorsHandler(rpcSpec.getRpcHttpCorsOrigins()));
                            // JSON-RPC handler
                            p.addLast(new JsonRpcHandler(rpcSpec, handlers));
                        }
                    });
            log.info("---------HTTP Host:{}, HTTP Port:{}",rpcSpec.getRpcHttpHost(), rpcSpec.getRpcHttpPort());
            channel = b.bind(InetAddress.getByName(rpcSpec.getRpcHttpHost()), rpcSpec.getRpcHttpPort()).sync().channel();
        } catch (Exception e) {
            stop();
            throw new RuntimeException("Failed to start JSON-RPC server", e);
        }
    }

    public void stop() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }
}
