package io.xdag.utils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.lang3.SystemUtils;

public final class NettyUtils {

    public static ServerBootstrap nativeEventLoopGroup(EventLoopGroup bossGroup, EventLoopGroup workerGroup, int workerThreadPoolSize) {
        ServerBootstrap bootstrap = new ServerBootstrap();

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

        bootstrap.group(bossGroup, workerGroup);

        if(SystemUtils.IS_OS_LINUX) {
            bootstrap.channel(EpollServerSocketChannel.class);
        } else if(SystemUtils.IS_OS_MAC) {
            bootstrap.channel(KQueueServerSocketChannel.class);
        } else {
            bootstrap.channel(NioServerSocketChannel.class);
        }

        return bootstrap;
    }
}
