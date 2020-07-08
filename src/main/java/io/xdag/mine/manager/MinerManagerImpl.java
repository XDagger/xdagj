package io.xdag.mine.manager;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.xdag.Kernel;
import io.xdag.consensus.PoW;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.message.Message;
import io.xdag.utils.ByteArrayWrapper;
import lombok.Setter;

public class MinerManagerImpl implements MinerManager {

  private static final Logger logger = LoggerFactory.getLogger(MinerManager.class);

  /** 保存活跃的channel */
  protected Map<InetSocketAddress, MinerChannel> activateMinerChannels = new ConcurrentHashMap<>();

  /** 根据miner的地址保存的数组 activate 代表的是一个已经注册的矿工 */
  protected Map<ByteArrayWrapper, Miner> activateMiners = new ConcurrentHashMap<>(200);

  private Task currentTask = null;

  @Setter private PoW poW;
  private Kernel kernel;

  private ScheduledExecutorService server =
      new ScheduledThreadPoolExecutor(
          3,
          new BasicThreadFactory.Builder()
              .namingPattern("MinerManagerThread")
              .daemon(true)
              .build());

  private ScheduledFuture<?> updateFuture;
  private ScheduledFuture<?> cleanChannelFuture;
  private ScheduledFuture<?> cleanMinerFuture;

  public MinerManagerImpl(Kernel kernel) {
    this.kernel = kernel;
  }

  /** 启动 函数 开启遍历和server */
  @Override
  public void start() {
    updateFuture = server.scheduleAtFixedRate(this::updataBalance, 10, 10, TimeUnit.SECONDS);
    cleanChannelFuture =
        server.scheduleAtFixedRate(this::cleanUnactivateChannel, 64, 32, TimeUnit.SECONDS);
    cleanMinerFuture =
        server.scheduleAtFixedRate(this::cleanUnactivateMiner, 64, 32, TimeUnit.SECONDS);
  }

  private void updataBalance() {
    try {
      for (MinerChannel channel : activateMinerChannels.values()) {
        if (channel.isActive()) {
          // logger.debug("给channel发送余额");
          channel.sendBalance();
        }
      }
    } catch (Exception e) {
      logger.warn("update balance error");
      e.printStackTrace();
    }
  }

  @Override
  public void addActivateChannel(MinerChannel channel) {
    logger.debug("add a new active channel");
    // 一般来讲 地址可能相同 但是端口不同
    activateMinerChannels.put(channel.getInetAddress(), channel);
  }

  @Override
  public void close() {
    if (updateFuture != null) {
      updateFuture.cancel(true);
    }
    if (cleanChannelFuture != null) {
      cleanChannelFuture.cancel(true);
    }
    if (cleanMinerFuture != null) {
      cleanMinerFuture.cancel(true);
    }
    if (server != null) {
      server.shutdown();
    }
    closeMiners();
  }

  private void closeMiners() {
    // 关闭所有连接
    for (MinerChannel channel : activateMinerChannels.values()) {
      channel.dropConnection();
    }
  }

  @Override
  public void removeUnactivateChannel(MinerChannel channel) {
    if (!channel.isActive()) {
      logger.debug("移除了一个channel");
      activateMinerChannels.remove(channel.getInetAddress(), channel);
      Miner miner = activateMiners.get(new ByteArrayWrapper(channel.getAccountAddressHash()));
      miner.removeChannel(channel.getInetAddress());
      miner.subChannelCounts();
      kernel.getChannelsAccount().getAndDecrement();
      if (miner.getConnChannelCounts() == 0) {
        miner.setMinerStates(MinerStates.MINER_ARCHIVE);
      }
    }
  }

  /** 清除当前所有不活跃的channel */
  public void cleanUnactivateChannel() {
    for (MinerChannel channel : activateMinerChannels.values()) {
      removeUnactivateChannel(channel);
    }
  }

  /** 清理minger */
  public void cleanUnactivateMiner() {
    for (Miner miner : activateMiners.values()) {
      if (miner.canRemove()) {
        logger.debug("移除了一个无效的矿工，");
        activateMiners.remove(new ByteArrayWrapper(miner.getAddressHash()));
      }
    }
  }

  /** 每一轮任务刚发出去的时候 会用这个跟新所有miner的额情况 */
  @Override
  public void updateNewTaskandBroadcast(Task task) {
    currentTask = task;
    for (MinerChannel channel : activateMinerChannels.values()) {
      if (channel.isActive()) {

        channel.setTaskIndex(currentTask.getTaskIndex());
        if (channel.getMiner().getTaskTime() < currentTask.getTaskTime()) {
          channel.getMiner().setTaskTime(currentTask.getTaskTime());
        }
        channel.sendTaskToMiner(currentTask.getTask());
        channel.setSharesCounts(0);
      }
    }
  }

  @Override
  public Map<ByteArrayWrapper, Miner> getActivateMiners() {
    return activateMiners;
  }

  @Override
  public void onNewShare(MinerChannel channel, Message msg) {
    if (currentTask.getTaskIndex() == channel.getTaskIndex()) {
      poW.receiveNewShare(channel, msg);
    }
  }

  @Override
  public MinerChannel getChannelByHost(InetSocketAddress host) {
    return this.activateMinerChannels.get(host);
  }

  @Override
  public Map<InetSocketAddress, MinerChannel> getActivateMinerChannels() {
    return this.activateMinerChannels;
  }
}
