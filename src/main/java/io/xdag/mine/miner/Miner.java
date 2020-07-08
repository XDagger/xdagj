package io.xdag.mine.miner;

import java.net.InetSocketAddress;
import java.sql.Time;
import java.util.Calendar;
import java.util.Date;

import io.xdag.core.XdagField;
import io.xdag.utils.BytesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.xdag.mine.MinerChannel;
import lombok.Data;

@Data
public class Miner {

  private static final Logger logger = LoggerFactory.getLogger(Miner.class);

  /** 保存这个矿工的地址 */
  private byte[] addressHash;

  /** 这个保存的是前8位为0 的地址 主要用于查询 */
  private byte[] addressHashLow;

  /** 相同账户地址的channel数量 */
  private AtomicInteger connChannelCounts = new AtomicInteger(0);

  /** 保存的时该矿工每一次进行任务计算的nonce + 低192bites的hash */
  private XdagField id = new XdagField();

  /** 记录收到任务的时间 */
  private long taskTime;

  /** 记录任务索引 * */
  private long taskIndex;

  /** 记录的是当前任务所有难度之和，每当接收到一个新的nonce 会更新这个 */
  private double prevDiff;

  /** 记录prevDiff的次数 实际上类似于进行了多少次计算 */
  private int prevDiffCounts;

  /** 存放的是连续16个任务本地计算的最大难度 每一轮放的都是最小hash 计算出来的diffs */
  private List<Double> maxDiffs = new CopyOnWriteArrayList<>();

  /** 记录这个矿工的状态 */
  private MinerStates minerStates;

  /** 类似于id 也是保存的nonce +hasholow的值 */
  private byte[] nonce = new byte[32];

  /** 记录上一轮任务中最小的hash */
  private byte[] lastMinHash = new byte[32];

  /** 将hash转换后的难度 可以认为是算力 */
  private double meanLogDiff;

  private Date registeredTime;

  protected int boundedTaskCounter;

  /** 保存的是这个矿工对应的channel */
  private Map<InetSocketAddress, MinerChannel> channels = new ConcurrentHashMap<>();

  /** 分别存放的是本轮中 的难度 以及前面所有计算的难度 */
  private Map<Long, Double> diffSum = new ConcurrentHashMap<>();

  private Map<Long, Double> prevDiffSum = new ConcurrentHashMap<>();

  public Miner(byte[] addressHash) {

    logger.debug("init a new miner {}", Hex.toHexString(addressHash));
    this.addressHash = addressHash;
    this.addressHashLow = BytesUtils.fixBytes(addressHash, 8, 24);
    this.minerStates = MinerStates.MINER_UNKNOWN;
    this.taskTime = 0;
    this.meanLogDiff = 0.0;
    this.registeredTime = Calendar.getInstance().getTime();
    boundedTaskCounter = 0;

    // 容器的初始化
    for (int i = 0; i < 16; i++) {
      maxDiffs.add(0.0);
    }
  }

  public byte[] getAddressHash() {
    return this.addressHash;
  }

  public int getConnChannelCounts() {
    return connChannelCounts.get();
  }

  public void addChannelCounts(int num) {
    connChannelCounts.addAndGet(num);
  }

  /** 自减1 */
  public void subChannelCounts() {
    connChannelCounts.getAndDecrement();
  }

  public MinerStates getMinerStates() {
    return this.minerStates;
  }

  public void setMinerStates(MinerStates states) {
    this.minerStates = states;
  }

  /** 判断这个miner 是不是可以被移除 */
  public boolean canRemove() {
    if (minerStates == MinerStates.MINER_ARCHIVE && connChannelCounts.get() == 0) {
      for (Double maxDiff : maxDiffs) {
        if (maxDiff != 0.0) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  public void setTaskTime(long time) {
    this.taskTime = time;
  }

  public long getTaskTime() {
    return this.taskTime;
  }

  public double getMaxDiffs(int index) {
    return maxDiffs.get(index);
  }

  public void addPrevDiff(double i) {
    prevDiff = +i;
  }

  public void addPrevDiffCounts() {
    this.prevDiffCounts++;
  }

  public void setMaxDiffs(int index, double diff) {
    maxDiffs.set(index, diff);
  }

  public void setPrevDiffCounts(int i) {
    this.prevDiffCounts = i;
  }

  public void setPrevDiff(double i) {
    this.prevDiff = i;
  }

  public double getPrevDiff() {
    return prevDiff;
  }

  public int getPrevDiffCounts() {
    return prevDiffCounts;
  }

  public Date getRegTime() {
    return registeredTime;
  }

  public void setRegisteredTime(Time registeredTime) {
    this.registeredTime = registeredTime;
  }

  public void setDiffSum(long key, double value) {
    double temp = 0.0;
    if (diffSum.get(key) != null && (temp = diffSum.get(key)) == 0.0) {
      diffSum.put(key, value);
    }
    temp += value;
    diffSum.put(key, temp);
  }

  public void setPrevDiffSum(long key, double value) {
    double temp = 0.0;
    if (prevDiffSum.get(key) != null && (temp = prevDiffSum.get(key)) == 0.0) {
      prevDiffSum.put(key, value);
    }
    temp += value;
    prevDiffSum.put(key, temp);
  }

  public double getDiffSum(long key) {
    return diffSum.get(key);
  }

  public double getPrevDiffSum(long key) {
    return prevDiffSum.get(key);
  }

  public byte[] getAddressHaashLow() {
    return this.addressHashLow;
  }

  public Map<InetSocketAddress, MinerChannel> getChannels() {
    return channels;
  }

  public double getMeanLogDiff() {
    return this.meanLogDiff;
  }

  public void setMeanLogDiff(double meanLogDiff) {
    this.meanLogDiff = meanLogDiff;
  }

  public byte[] getLastMinHash() {
    return lastMinHash;
  }

  public void setLastMinHash(byte[] lastMinHash) {
    this.lastMinHash = lastMinHash;
  }

  public int getBoundedTaskCounter() {
    return boundedTaskCounter;
  }

  public void setBoundedTaskCounter(int boundedTaskCounter) {
    this.boundedTaskCounter = boundedTaskCounter;
  }

  public void addBoundedTaskCounter() {
    this.boundedTaskCounter++;
  }

  public void putChannel(InetSocketAddress inetSocketAddress, MinerChannel channel) {
    this.channels.put(inetSocketAddress, channel);
  }

  /**Todo:后续改为atomic*/
  public void increaseTaskIndex() {
    taskIndex++;
  }

  public void removeChannel(InetSocketAddress address) {
    this.channels.remove(address);
  }
}
