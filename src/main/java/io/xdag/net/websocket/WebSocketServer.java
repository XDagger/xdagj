package io.xdag.net.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.xdag.Kernel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Objects;

@Slf4j
public class WebSocketServer {

    private final int ServerPort;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private @Nullable ChannelFuture webSocketChannel;
    @Getter
    private final PoolHandShakeHandler poolHandShakeHandler;


    public WebSocketServer(Kernel kernel, String clientHost, String tag, int port) {
        this.ServerPort = port;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.poolHandShakeHandler = new PoolHandShakeHandler(kernel, clientHost, tag, ServerPort);
    }


    public void start() throws InterruptedException {
        log.info("Pool WebSocket enabled");
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
        webSocketChannel = b.bind("localhost", ServerPort);
        try {
            webSocketChannel.sync();
        } catch (InterruptedException e) {
            log.error("The Pool WebSocket server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        try {
            Objects.requireNonNull(webSocketChannel).channel().close().sync();
        } catch (InterruptedException e) {
            log.error("Couldn't stop the Pool WebSocket server", e);
            Thread.currentThread().interrupt();
        }
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
