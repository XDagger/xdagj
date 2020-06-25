package io.xdag.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.xdag.Kernel;
import io.xdag.net.handler.XdagChannelInitializer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagServer {
  protected Kernel kernel;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;

  private ChannelFuture channelFuture;
  private boolean listening;

  public XdagServer(final Kernel kernel) {
    this.kernel = kernel;
  }

  public void start() {
    start(kernel.getConfig().getNodeIp(), kernel.getConfig().getNodePort());
  }

  public void start(String ip, int port) {

    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    try {
      ServerBootstrap b = new ServerBootstrap();

      b.group(bossGroup, workerGroup);
      b.channel(NioServerSocketChannel.class);

      b.childOption(ChannelOption.SO_KEEPALIVE, true);
      b.childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.childOption(
          ChannelOption.CONNECT_TIMEOUT_MILLIS, kernel.getConfig().getConnectionTimeout());

      //            b.handler(new LoggingHandler());
      b.childHandler(new XdagChannelInitializer(kernel, true, null));

      log.debug("Listening for incoming connections, address: {}:{} ", ip, port);

      channelFuture = b.bind(ip, port).sync();

      listening = true;
      log.debug("Connection listen true");
    } catch (Exception e) {
      log.error("Peer server error: {} ({})", e.getMessage(), e.getClass().getName());
      throw new Error("XdagServer Disconnected");
    }
  }

  public void close() {
    if (listening && channelFuture != null && channelFuture.channel().isOpen()) {
      try {
        log.debug("Closing XdagServer...");
        channelFuture.channel().close().sync();

        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();

        workerGroup.terminationFuture().sync();
        bossGroup.terminationFuture().sync();

        log.debug("XdagServer closed.");
      } catch (Exception e) {
        log.warn("Problems closing server channel", e);
      }
    }
  }

  public boolean isListening() {
    return listening;
  }
}
