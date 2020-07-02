package io.xdag.net.manager;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.XdagChannel;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagChannelManager {
  private Kernel kernel;

  protected ConcurrentHashMap<InetSocketAddress, XdagChannel> channels = new ConcurrentHashMap<>();
  protected ConcurrentHashMap<String, XdagChannel> activeChannels = new ConcurrentHashMap<>();

  /** Queue with new blocks from other peers */
  private BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
  // 广播区块
  private Thread blockDistributeThread;

  public XdagChannelManager(Kernel kernel) {
    this.kernel = kernel;

    // Resending new blocks to network in loop
    this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
    blockDistributeThread.start();
  }

  public void add(XdagChannel ch) {
    log.debug(
        "xdag channel manager->Channel added: remoteAddress = {}:{}", ch.getIp(), ch.getPort());
    channels.put(ch.getInetSocketAddress(), ch);
  }

  public void notifyDisconnect(XdagChannel channel) {
    log.debug("xdag channel manager-> node {}: notifies about disconnect", channel);
    remove(channel);
    channel.onDisconnect();
  }

  public Set<InetSocketAddress> getActiveAddresses() {
    Set<InetSocketAddress> set = new HashSet<>();

    for (XdagChannel c : activeChannels.values()) {
      Node p = c.getNode();
      set.add(new InetSocketAddress(p.getHost(), p.getPort()));
    }
    return set;
  }

  public List<XdagChannel> getActiveChannels() {
    return new ArrayList<>(activeChannels.values());
  }

  /** Processing new blocks received from other peers from queue */
  private void newBlocksDistributeLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      BlockWrapper wrapper = null;
      try {
        wrapper = newForeignBlocks.take();
        log.debug("no problem..");
        sendNewBlock(wrapper);
      } catch (InterruptedException e) {
        break;
      } catch (Throwable e) {
        if (wrapper != null) {
          log.error("Block dump: {}", wrapper.getBlock());
        } else {
          log.error("Error broadcasting unknown block", e);
        }
      }
    }
  }

  // TODO:怎么发送 目前是发给除receive的节点
  public void sendNewBlock(BlockWrapper blockWrapper) {

    Node receive = null;
    // 说明是自己产生的
    if (blockWrapper.getRemoteNode() == null
        || blockWrapper.getRemoteNode().equals(kernel.getClient().getNode())) {
      receive = kernel.getClient().getNode();
    } else {
      XdagChannel receiveChannel = activeChannels.get(blockWrapper.getRemoteNode().getHexId());
      receive = receiveChannel != null ? receiveChannel.getNode() : null;
    }
    for (XdagChannel channel : activeChannels.values()) {
      if (receive != null && channel.getNode().getHexId().equals(receive.getHexId())) {
        log.debug("不发送给他");
        continue;
      }
      log.debug("发送给除receive的节点");
      channel.sendNewBlock(blockWrapper);
    }
  }

  /**
   * When a channel becomes active.
   *
   * @param channel
   * @param node
   */
  public void onChannelActive(XdagChannel channel, Node node) {
    channel.setActive(true);
    activeChannels.put(node.getHexId(), channel);
    log.debug("activeChannel size:" + activeChannels.size());
  }

  public void onNewForeignBlock(BlockWrapper blockWrapper) {

    newForeignBlocks.add(blockWrapper);
  }

  public boolean containsNode(Node node) {
    return activeChannels.containsKey(node.getHexId());
  }

  public int size() {
    return channels.size();
  }

  public List<XdagChannel> getIdleChannels() {
    List<XdagChannel> list = new ArrayList<>();

    for (XdagChannel c : activeChannels.values()) {
      if (c.getMsgQueue().isIdle()) {
        list.add(c);
      }
    }
    return list;
  }

  public void remove(XdagChannel ch) {
    log.debug("Channel removed: remoteAddress = {}:{}", ch.getIp(), ch.getPort());
    channels.remove(ch.getInetSocketAddress());
    if (ch.isActive()) {
      activeChannels.remove(ch.getNode().getHexId());
      ch.setActive(false);
    }
  }

  public boolean isConnected(InetSocketAddress address) {
    return channels.containsKey(address);
  }

  public boolean isAcceptable(InetSocketAddress address) {
    // todo:
    //        boolean res = netDBManager.canAccept(address);
    return true;
  }

  public void stop() {
    log.debug("Channel Manager stop...");
    if (blockDistributeThread != null) {
      // 中断
      blockDistributeThread.interrupt();
    }
    // 关闭所有连接
    for (XdagChannel channel : activeChannels.values()) {
      channel.dropConnection();
    }
  }
}
