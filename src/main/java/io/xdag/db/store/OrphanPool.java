package io.xdag.db.store;

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XdagField;
import io.xdag.db.KVSource;
import io.xdag.utils.BytesUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

public class OrphanPool {

  public static final byte ORPHAN_PREFEX = 0x00;
  private static final Logger logger = LoggerFactory.getLogger(OrphanPool.class);
  /** size key */
  private static final byte[] ORPHAN_SIZE = Hex.decode("FFFFFFFFFFFFFFFF");
  // <hash,nexthash>
  private KVSource<byte[], byte[]> orphanSource;

  public OrphanPool(KVSource<byte[], byte[]> orphan) {
    this.orphanSource = orphan;
  }

  public void init() {
    this.orphanSource.init();
    if (orphanSource.get(ORPHAN_SIZE) == null) {
      this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
    }
  }

  public void reset() {
    this.orphanSource.reset();
    this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
  }

  public List<Address> getOrphan(long num) {
    List<Address> res = new ArrayList<>();
    if (orphanSource.get(ORPHAN_SIZE) == null || getOrphanSize() == 0) {
      return null;
    } else {
      long orphanSize = getOrphanSize();
      long addNum = Math.min(orphanSize, num);
      byte[] key = BytesUtils.of(ORPHAN_PREFEX);
      List<byte[]> ans = orphanSource.prefixKeyLookup(key, key.length);
      for (byte[] an : ans) {
        if (addNum == 0) {
          break;
        }
        // TODO:判断时间
        addNum--;
        res.add(new Address(BytesUtils.subArray(an, 1, 32), XdagField.FieldType.XDAG_FIELD_OUT));
      }
      return res;
    }
  }

  public synchronized void deleteByHash(byte[] hashlow) {
    logger.debug("deleteByhash");
    orphanSource.delete(BytesUtils.merge(ORPHAN_PREFEX, hashlow));
    long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
    orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
  }

  public synchronized void addOrphan(Block block) {
    orphanSource.put(BytesUtils.merge(ORPHAN_PREFEX, block.getHashLow()), new byte[0]);
    long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
    logger.debug("orphan current size:" + currentsize);
    logger.debug(":" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
    orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
  }

  public long getOrphanSize() {
    long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
    logger.debug("current orphan size:" + currentsize);
    logger.debug("Hex:" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
    return currentsize;
  }

  public boolean containsKey(byte[] hashlow) {
    if (orphanSource.get(BytesUtils.merge(ORPHAN_PREFEX, hashlow)) != null) {
      return true;
    }
    return false;
  }
}
