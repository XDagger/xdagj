package io.xdag.net.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.xdag.Kernel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketServer {

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final @Nullable ChannelFuture webSocketChannel;
    @Getter
    private final PoolHandShakeHandler poolHandShakeHandler;

    public WebSocketServer(Kernel kernel, String clientHost, String tag, int port) {
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.poolHandShakeHandler = new PoolHandShakeHandler(kernel, clientHost, tag, port);
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

    public void start() throws InterruptedException {
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

    public void stop() {
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

