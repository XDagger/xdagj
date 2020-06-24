package io.xdag.mine;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;


/**
 * @Classname MinerChannelInitializer
 * @Description TODO
 * @Date 2020/5/10 19:50
 * @Created by Myron
 */
public class MinerChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger(MinerChannelInitializer.class);

    private Kernel kernel;

    private boolean isServer;

    public MinerChannelInitializer(Kernel kernel, boolean isServer) {
        logger.debug("初始化");

        this.kernel = kernel;
        this.isServer = isServer;
    }


    @Override
    protected void initChannel(NioSocketChannel ch) {
        logger.debug("init a new MinerChannel......" + "是否是客户端：" + isServer);
        //如果是服务器 就会获取到的是外部的地址 否则获取到自己本地的地址
        InetSocketAddress channelAddress = isServer ? ch.remoteAddress() :
                new InetSocketAddress(kernel.getConfig().getPoolIp(),kernel.getConfig().getPoolPort());
        MinerChannel minerChannel = new MinerChannel(kernel,ch,isServer);
        minerChannel.init(ch.pipeline(),channelAddress);

        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
        ch.config().setOption(ChannelOption.TCP_NODELAY,true);
        ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
        ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
    }
}
