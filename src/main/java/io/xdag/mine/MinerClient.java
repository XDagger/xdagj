package io.xdag.mine;

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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerClient {
    private static final ThreadFactory FACTORY = new ThreadFactory() {
        AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "XdagJMinerWorker-" + cnt.getAndIncrement());
        }
    };
    private final EventLoopGroup workerGroup;
    private Config config;

    public MinerClient(Config config) {
        this.config = config;
        this.workerGroup = new NioEventLoopGroup(0, FACTORY);
    }

    public void connect(String host, int port, MinerChannelInitializer minerChannelInitializer) {
        ChannelFuture channelFuture;
        try {
            channelFuture = connectAsync(host, port, minerChannelInitializer);
            channelFuture.sync();
        } catch (Exception e) {
            if (e instanceof IOException) {
                log.warn(
                        "MinerClient: Can't connect to " + host + ":" + port + " (" + e.getMessage() + ")");
                log.warn("MinerClient.connect(" + host + ":" + port + ") exception:", e);
            } else {
                log.warn("Exception:", e);
            }
        }
    }

    public ChannelFuture connectAsync(
            String host, int port, MinerChannelInitializer minerChannelInitializer) {
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        // b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout());
        b.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
        b.remoteAddress(host, port);
        b.handler(minerChannelInitializer);
        return b.connect();
    }

    public void close() {
        log.info("Shutdown XdagClient");
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }
}
