package io.xdag.net;

import java.net.InetSocketAddress;

import org.spongycastle.util.encoders.Hex;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.BlockWrapper;
import io.xdag.net.handler.MessageCodes;
import io.xdag.net.handler.Xdag;
import io.xdag.net.handler.XdagAdapter;
import io.xdag.net.handler.XdagBlockHandler;
import io.xdag.net.handler.XdagHandler;
import io.xdag.net.handler.XdagHandlerFactory;
import io.xdag.net.handler.XdagHandlerFactoryImpl;
import io.xdag.net.handler.XdagHandshakeHandler;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.impl.Xdag03MessageFactory;
import io.xdag.net.node.Node;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class XdagChannel {
  private InetSocketAddress inetSocketAddress;
  private final NioSocketChannel socket;

  private boolean isActive;
  private boolean isDisconnected = false;

  private Config config;

  private XdagHandshakeHandler handshakeHandler; // 握手 密钥
  private MessageCodes messageCodec; // 信息编码处理
  private XdagBlockHandler blockHandler; // 处理区块
  private Xdag xdag = new XdagAdapter(); // 获取xdag03handler
  private XdagHandlerFactory xdagHandlerFactory; // 用来创建xdag03handler处理message 实际的逻辑操作

  private MessageQueue msgQueue; // 发送message的线程 针对每个channel

  private Node node; // 该channel对应的节点

  public XdagChannel(NioSocketChannel socketChannel) {
    this.socket = socketChannel;
  }

  public void init(
      ChannelPipeline pipeline,
      Kernel kernel,
      boolean isServer,
      InetSocketAddress inetSocketAddress) {

    this.config = kernel.getConfig();
    this.inetSocketAddress = inetSocketAddress;

    this.handshakeHandler = new XdagHandshakeHandler(kernel, config, this);
    handshakeHandler.setServer(isServer);

    pipeline.addLast("handshakeHandler", handshakeHandler);

    this.msgQueue = new MessageQueue(this);
    this.messageCodec = new MessageCodes(this);
    this.blockHandler = new XdagBlockHandler(this);

    this.xdagHandlerFactory = new XdagHandlerFactoryImpl(kernel, this);
  }

  public void initWithNode(final String host, final int port) {
    node = new Node(host, port);
    log.debug("Initwith Node host:" + host + " port:" + port + " node:" + node.getHexId());
  }

  public void notifyDisconnect(XdagChannel channel) {
    log.debug("Node {}: notifies about disconnect", channel);
    channel.onDisconnect();
  }

  public void onSyncDone(boolean done) {

    if (done) {
      xdag.enableBlocks();
    } else {
      xdag.disableBlocks();
    }

    xdag.onSyncDone(done);
  }

  public String getIp() {
    return inetSocketAddress.getAddress().getHostAddress();
  }

  public int getPort() {
    return inetSocketAddress.getPort();
  }

  public void onDisconnect() {
    isDisconnected = true;
  }

  public boolean isDisconnected() {
    return isDisconnected;
  }

  public void sendPubkey(ChannelHandlerContext ctx) throws Exception {
    ByteBuf buffer = ctx.alloc().buffer(1024);
    buffer.writeBytes(config.getXKeys().pub);
    ctx.writeAndFlush(buffer).sync();
    node.getStat().Outbound.add(2);
  }

  public void sendPassword(ChannelHandlerContext ctx) throws Exception {
    ByteBuf buffer = ctx.alloc().buffer(512);
    buffer.writeBytes(config.getXKeys().sect0_encoded);
    ctx.writeAndFlush(buffer).sync();
    node.getStat().Outbound.add(1);
  }

  public void sendNewBlock(BlockWrapper blockWrapper) {
    log.debug("send a block hash is:+" + Hex.toHexString(blockWrapper.getBlock().getHashLow()));
    log.debug("ttl:" + blockWrapper.getTtl());
    xdag.sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
  }

  // 激活xdaghandler
  public void activateXdag(ChannelHandlerContext ctx, XdagVersion version) {

    XdagHandler handler = xdagHandlerFactory.create(version);
    MessageFactory messageFactory = createXdagMessageFactory(version);
    blockHandler.setMessageFactory(messageFactory);
    ctx.pipeline().addLast("blockHandler", blockHandler);
    ctx.pipeline().addLast("messageCodec", messageCodec);
    handler.setMsgQueue(msgQueue); // 注册进消息队列 用来收发消息
    ctx.pipeline().addLast("xdag", handler);
    handler.setChannel(this);
    xdag = handler;

    handler.activate();
  }

  private MessageFactory createXdagMessageFactory(XdagVersion version) {
    switch (version) {
      case V03:
        return new Xdag03MessageFactory();

      default:
        throw new IllegalArgumentException("Xdag" + version + " is not supported");
    }
  }

  @Override
  public String toString() {
    String format = "new channel";
    if (node != null) {
      format = String.format("%s:%s", node.getHost(), node.getPort());
    }
    return format;
  }

  public Xdag getXdag() {
    return xdag;
  }

  public void dropConnection() {
    xdag.dropConnection();
  }
}
