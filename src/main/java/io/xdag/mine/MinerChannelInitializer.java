package io.xdag.mine;

import io.xdag.config.Config;
import java.net.InetSocketAddress;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;

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
    AtomicInteger channelsAccount = kernel.getChannelsAccount();
    if (channelsAccount.get() >= kernel.getConfig().getGlobalMinerChannelLimit()) {
      ch.close();
      System.out.println("too many channels in this pool");
      return;
    }

    logger.debug("init a new MinerChannel......" + "是否是客户端：" + isServer);
    // 如果是服务器 就会获取到的是外部的地址 否则获取到自己本地的地址
    channelsAccount.getAndIncrement();
    InetSocketAddress channelAddress =
        isServer ? ch.remoteAddress() : new InetSocketAddress(kernel.getConfig().getPoolIp(), kernel.getConfig().getPoolPort());
    MinerChannel minerChannel = new MinerChannel(kernel, ch, isServer);
    minerChannel.init(ch.pipeline(), channelAddress);

    ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
    ch.config().setOption(ChannelOption.TCP_NODELAY, true);
    ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
    ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
  }
}
