package io.xdag.db.store;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.db.KVSource;
import io.xdag.db.SimpleFileStore;
import io.xdag.utils.BytesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BlockStore {

  private static final Logger logger = LoggerFactory.getLogger(BlockStore.class);

  private KVSource<byte[], byte[]> indexSource; // <prefix-hash,value> eg:<diff-hash,blockdiff>
  private KVSource<byte[], byte[]> blockSource; // <hash,rawdata>
  private KVSource<byte[], byte[]> timeSource; // <time-hash,hash>
  private SimpleFileStore simpleFileStore;

  private static final byte[] BLOCK_SIZE = Hex.decode("FFFFFFFFFFFFFFFF"); // block size key
  private static final byte[] MAIN_SIZE = Hex.decode("EEEEEEEEEEEEEEEE"); // main size key
  private static final byte[] PRETOP = Hex.decode("DDDDDDDDDDDDDDDD"); // pretop
  private static final byte[] PRETOPDIFF = Hex.decode("CCCCCCCCCCCCCCCC"); // pretop diff
  private static final byte[] ORIGINPRETOP = Hex.decode("FFFFFFFFFFFFFFFE"); // origin pretop diff
  private static final byte[] ORIGINPRETOPDIFF =
      Hex.decode("FFFFFFFFFFFFFFEF"); // origin pretop diff
  private static final byte[] GLOBAL_ADDRESS = Hex.decode("FFFFFFFFFFFFFEFF");
  public static final byte BLOCK_MAXDIFF = 0x00;
  public static final byte BLOCK_MAXDIFFLINK = 0x01;
  public static final byte BLOCK_AMOUNT = 0x02;
  public static final byte BLOCK_REF = 0x03;
  public static final byte BLOCK_FLAG = 0x04;
  public static final byte BLOCK_TIME = 0x05;
  public static final byte BLOCK_FEE = 0x06;
  public static final byte BLOCK_KEY_INDEX = 0x07;
  public static final byte BLOCK_HASH = 0x08;

  // 存sums
  private BlockingQueue<Block> blockQueue = new LinkedBlockingQueue<>();
  private ExecutorService executorService = Executors.newSingleThreadExecutor();
  private Future<?> sumFuture;

  public BlockStore(
      KVSource<byte[], byte[]> index,
      KVSource<byte[], byte[]> block,
      KVSource<byte[], byte[]> time,
      SimpleFileStore simpleFileStore) {
    this.indexSource = index;
    this.blockSource = block;
    this.timeSource = time;
    this.simpleFileStore = simpleFileStore;
  }

  public void init() {
    indexSource.init();
    blockSource.init();
    timeSource.init();
    if (indexSource.get(BLOCK_SIZE) == null) {
      indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(0, false));
    }
    if (indexSource.get(MAIN_SIZE) == null) {
      indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(0, false));
    }
    Runnable queueProducer = this::processQueue;
    sumFuture = executorService.submit(queueProducer);
  }

  public void reset() {
    indexSource.reset();
    blockSource.reset();
    timeSource.reset();
    indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(0, false));
    indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(0, false));
    simpleFileStore.reset();
    Runnable queueProducer = this::processQueue;
    sumFuture = executorService.submit(queueProducer);
  }

  public void processQueue() {
    logger.debug("Sum save thread run...");
    while (!Thread.currentThread().isInterrupted()) {
      if (!blockQueue.isEmpty()) {
        try {
          Block block = blockQueue.take();
          simpleFileStore.saveBlockSums(block);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  // 存储block的过程
  public synchronized void saveBlock(Block block) {
    logger.debug("Save Block:" + block);
    blockQueue.add(block);
    long timeIndex = block.getTimestamp();
    timeSource.put(getTimeKey(timeIndex, block.getHashLow()), block.getHashLow());
    blockSource.put(block.getHashLow(), block.getXdagBlock().getData());
    if (indexSource.get(BLOCK_SIZE) != null
        && BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false) != 0) {
      long blocksize = BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false) + 1;
      indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(blocksize, false));
    } else {
      indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(1, false));
      // 赋值localaddress
      indexSource.put(GLOBAL_ADDRESS, block.getHashLow());
    }

    saveBlockInfo(block);
  }

  private void saveBlockInfo(Block block) {
    indexSource.put(
        BytesUtils.merge(BLOCK_MAXDIFF, block.getHashLow()),
        BytesUtils.bigIntegerToBytes(block.getDifficulty(), 16, false));
    if (block.getMaxDifflink() != null) {
      indexSource.put(
          BytesUtils.merge(BLOCK_MAXDIFFLINK, block.getHashLow()),
          block.getMaxDifflink().getHashLow());
    }
    if (block.getRef() != null) {
      indexSource.put(BytesUtils.merge(BLOCK_REF, block.getHashLow()), block.getRef().getHashLow());
    }
    indexSource.put(
        BytesUtils.merge(BLOCK_AMOUNT, block.getHashLow()),
        BytesUtils.longToBytes(block.getAmount(), false));
    indexSource.put(
        BytesUtils.merge(BLOCK_FLAG, block.getHashLow()),
        BytesUtils.intToBytes(block.getFlags(), false));
    indexSource.put(
        BytesUtils.merge(BLOCK_FEE, block.getHashLow()),
        BytesUtils.longToBytes(block.getFee(), false));
    indexSource.put(
        BytesUtils.merge(BLOCK_TIME, block.getHashLow()),
        BytesUtils.longToBytes(block.getTimestamp(), false));
    indexSource.put(BytesUtils.merge(BLOCK_HASH, block.getHashLow()), block.getHash());
  }

  public boolean hasBlock(byte[] hashlow) {
    return blockSource.get(hashlow) != null;
  }

  /*
   * @Author punk
   * @Description 获取时间索引键
   * @Date 2020/4/22
   * @Param [timestamp]
   * @return byte[]
   **/
  private byte[] getTimeKey(long timestamp, byte[] hashlow) {
    long time1Prefix = timestamp >> 16;
    return BytesUtils.merge(BytesUtils.longToBytes(time1Prefix, false), hashlow);
  }

  public List<Block> getBlocksUsedTime(long starttime, long endtime) {
    List<Block> res = new ArrayList<>();
    while (starttime < endtime) {
      List<Block> blocks = getBlocksByTime(starttime);
      starttime += 0x10000;
      if (blocks == null || blocks.size() == 0) {
        continue;
      }
      res.addAll(blocks);
    }
    return res;
  }

  public List<Block> getBlocksByTime(long starttime) {
    List<Block> blocks = new ArrayList<>();
    long key = starttime >> 16;
    byte[] keyPrefix = BytesUtils.longToBytes(key, false);
    List<byte[]> keys = timeSource.prefixValueLookup(keyPrefix, keyPrefix.length);
    for (int i = 0; i < keys.size(); i++) {
      Block block = getBlockByHash(keys.get(i), true);
      if (block != null) {
        blocks.add(block);
      }
    }
    return blocks;
  }

  public Block getBlockByHash(byte[] hashlow, boolean isRaw) {
    if (isRaw) {
      return getRawBlockByHash(hashlow);
    }
    return getBlockInfoByHash(hashlow);
  }

  public Block getRawBlockByHash(byte[] hashlow) {
    Block block = getBlockInfoByHash(hashlow);
    if (block == null) {
      return null;
    }
    block.setXdagBlock(new XdagBlock(blockSource.get(hashlow)));
    block.setParsed(false);
    block.parse();
    return block;
  }

  public Block getBlockInfoByHash(byte[] hashlow) {

    if (!hasBlock(hashlow)) {
      return null;
    }
    long timestamp =
        BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_TIME, hashlow)), 0, false);
    long amount =
        BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_AMOUNT, hashlow)), 0, false);
    BigInteger diff;
    if (indexSource.get(BytesUtils.merge(BLOCK_MAXDIFF, hashlow)) == null) {
      diff = BigInteger.ZERO;
    } else {
      diff =
          BytesUtils.bytesToBigInteger(
              indexSource.get(BytesUtils.merge(BLOCK_MAXDIFF, hashlow)), 0, false);
    }
    long fee =
        BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_FEE, hashlow)), 0, false);
    byte[] ref = indexSource.get(BytesUtils.merge(BLOCK_REF, hashlow));
    byte[] maxdiffLink = indexSource.get(BytesUtils.merge(BLOCK_MAXDIFFLINK, hashlow));
    int flags =
        BytesUtils.bytesToInt(indexSource.get(BytesUtils.merge(BLOCK_FLAG, hashlow)), 0, false);
    Block block = new Block(timestamp, amount, diff, fee, ref, maxdiffLink, flags);
    block.setHashLow(hashlow);
    block.setHash(indexSource.get(BytesUtils.merge(BLOCK_HASH, hashlow)));
    return block;
  }

  public synchronized void updateBlockInfo(byte TypePrefix, Block block) {
    byte[] hashlow = block.getHashLow();
    byte[] value;
    switch (TypePrefix) {
      case BLOCK_MAXDIFF:
        value = BytesUtils.bigIntegerToBytes(block.getDifficulty(), 16, false);
        break;
      case BLOCK_MAXDIFFLINK:
        value = block.getMaxDifflink().getHashLow();
        break;
      case BLOCK_AMOUNT:
        value = BytesUtils.longToBytes(block.getAmount(), false);
        break;
      case BLOCK_REF:
        if (block.getRef() == null) {
          value = null;
        } else {
          value = block.getRef().getHashLow();
        }
        break;
      case BLOCK_FLAG:
        value = BytesUtils.intToBytes(block.getFlags(), false);
        break;
      case BLOCK_TIME:
        value = BytesUtils.longToBytes(block.getTimestamp(), false);
        break;
      case BLOCK_FEE:
        value = BytesUtils.longToBytes(block.getFee(), false);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + TypePrefix);
    }
    if (value == null) {
      indexSource.delete(BytesUtils.merge(TypePrefix, hashlow));
    } else {
      indexSource.put(BytesUtils.merge(TypePrefix, hashlow), value);
    }
  }

  public synchronized void updateBlockKeyIndex(byte[] hashlow, int keyindex) {
    indexSource.put(
        BytesUtils.merge(BLOCK_KEY_INDEX, hashlow), BytesUtils.intToBytes(keyindex, false));
  }

  public synchronized void deleteBlockKeyIndex(byte[] hashlow) {
    if (indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)) == null) {
      return;
    }
    indexSource.delete(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow));
  }

  public int getBlockKeyIndex(byte[] hashlow) {
    if (indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)) != null) {
      return BytesUtils.bytesToInt(
          indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)), 0, false);
    } else {
      return -2; // 不存在
    }
  }

  public synchronized void mainNumberInc() {
    long currentsize = BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
    indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
  }

  public synchronized void mainNumberDec() {
    long currentsize = BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
    indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
  }

  public long getMainNumber() {
    return BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
  }

  public long getBlockNumber() {
    return BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false);
  }

  public synchronized void setPretop(Block block) {
    indexSource.put(PRETOP, block.getHashLow());
  }

  public synchronized void setOriginpretop(Block block) {
    indexSource.put(ORIGINPRETOP, block.getHashLow());
  }

  public synchronized void setPretopDiff(BigInteger pretopDiff) {
    indexSource.put(PRETOPDIFF, BytesUtils.bigIntegerToBytes(pretopDiff, 16, false));
  }

  public synchronized void setOriginpretopdiff(BigInteger pretopDiff) {
    indexSource.put(ORIGINPRETOPDIFF, BytesUtils.bigIntegerToBytes(pretopDiff, 16, false));
  }

  public BigInteger getPretopDiff() {
    if (indexSource.get(PRETOPDIFF) != null) {
      return BytesUtils.bytesToBigInteger(indexSource.get(PRETOPDIFF), 0, false);
    }
    return BigInteger.ZERO;
  }

  public byte[] getPretop() {
    return indexSource.get(PRETOP);
  }

  public BigInteger getOriginpretopDiff() {
    if (indexSource.get(ORIGINPRETOPDIFF) != null) {
      return BytesUtils.bytesToBigInteger(indexSource.get(ORIGINPRETOPDIFF), 0, false);
    }
    return BigInteger.ZERO;
  }

  public byte[] getOriginpretop() {
    return indexSource.get(ORIGINPRETOP);
  }

  public SimpleFileStore getSimpleFileStore() {
    return simpleFileStore;
  }

  public void closeSum() {
    logger.debug("Sums service close...");
    if (executorService != null) {
      try {
        if (sumFuture != null) {
          sumFuture.cancel(true);
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public byte[] getGlobalAddress() {
    if (indexSource.get(GLOBAL_ADDRESS) != null) {
      return indexSource.get(GLOBAL_ADDRESS);
    } else {
      return null;
    }
  }
}
