package io.xdag.mine;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    private Kernel kernel;
    private boolean isServer;

    public MinerChannelInitializer(Kernel kernel, boolean isServer) {
        log.debug("init a minerchannelInitilizer");
        this.kernel = kernel;
        this.isServer = isServer;
    }

    @Override
    protected void initChannel(NioSocketChannel ch) {
        AtomicInteger channelsAccount = kernel.getChannelsAccount();
        if (channelsAccount.get() >= kernel.getConfig().getGlobalMinerChannelLimit()) {
            ch.close();
            System.out.println("too many channels in this pool");
            return;
        }
        log.debug("init a new MinerChannel......" + "是否是客户端：" + isServer);
        // 如果是服务器 就会获取到的是外部的地址 否则获取到自己本地的地址
        channelsAccount.getAndIncrement();
        InetSocketAddress channelAddress = isServer
                ? ch.remoteAddress()
                : new InetSocketAddress(
                        kernel.getConfig().getPoolIp(), kernel.getConfig().getPoolPort());
        MinerChannel minerChannel = new MinerChannel(kernel, ch, isServer);
        minerChannel.init(ch.pipeline(), channelAddress);
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
        ch.config().setOption(ChannelOption.TCP_NODELAY, true);
        ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
        ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
    }
}
