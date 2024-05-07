/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.xdag.db.rocksdb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.google.common.collect.Lists;
import io.xdag.core.*;
import io.xdag.db.BlockStore;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.xdag.utils.BytesUtils.equalBytes;

@Slf4j
public class BlockStoreImpl implements BlockStore {

    private final Kryo kryo;

    /**
     * <prefix-hash,value> eg:<diff-hash,blockDiff>
     */
    private final KVSource<byte[], byte[]> indexSource;
    /**
     * <prefix-time-hash,hash>
     */
    private final KVSource<byte[], byte[]> timeSource;
    /**
     * <hash,rawData>
     */
    private final KVSource<byte[], byte[]> blockSource;
    private final KVSource<byte[], byte[]> txHistorySource;

    public BlockStoreImpl(
            KVSource<byte[], byte[]> index,
            KVSource<byte[], byte[]> time,
            KVSource<byte[], byte[]> block,
            KVSource<byte[], byte[]> txHistory) {
        this.indexSource = index;
        this.timeSource = time;
        this.blockSource = block;
        this.txHistorySource = txHistory;
        this.kryo = new Kryo();
        kryoRegister();
    }

    private void kryoRegister() {
        kryo.setReferences(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
        kryo.register(XdagTopStatus.class);
        kryo.register(SnapshotInfo.class);
        kryo.register(UInt64.class);
        kryo.register(XAmount.class);
    }

    private byte[] serialize(final Object obj) throws SerializationException {
        synchronized (kryo) {
            try {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final Output output = new Output(outputStream);
                kryo.writeObject(output, obj);
                output.flush();
                output.close();
                return outputStream.toByteArray();
            } catch (final IllegalArgumentException | KryoException exception) {
                throw new SerializationException(exception.getMessage(), exception);
            }
        }
    }

    private Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
        synchronized (kryo) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                final Input input = new Input(inputStream);
                return kryo.readObject(input, type);
            } catch (final IllegalArgumentException | KryoException | NullPointerException exception) {
                log.debug("Deserialize data:{}", Hex.toHexString(bytes));
                throw new DeserializationException(exception.getMessage(), exception);
            }
        }
    }

    public void init() {
        indexSource.init();
        timeSource.init();
        blockSource.init();
        txHistorySource.init();
    }

    public void reset() {
        indexSource.reset();
        timeSource.reset();
        blockSource.reset();
        txHistorySource.reset();
    }

    public void saveXdagStatus(XdagStats status) {
        byte[] value = null;
        try {
            value = serialize(status);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(new byte[]{SETTING_STATS}, value);
    }

    @Override
    public void saveTxHistoryToRocksdb(TxHistory txHistory, int id) {
        byte[] remark = new byte[]{};
        if (txHistory.getRemark() != null) {
            remark = txHistory.getRemark().getBytes(StandardCharsets.UTF_8);
        }
        byte[] isWalletAddress = new byte[]{(byte) (txHistory.getAddress().getIsAddress() ? 1 : 0)};
        byte[] key = BytesUtils.merge(TX_HISTORY, BytesUtils.merge(txHistory.getAddress().getAddress().toArray(),
                BasicUtils.address2Hash(txHistory.getHash()).toArray(), BytesUtils.intToBytes(id, true)));
        // key: 0xa0 + address hash + txHashLow + id
        byte[] value;
        value = BytesUtils.merge(txHistory.getAddress().getType().asByte(), BytesUtils.merge(isWalletAddress,
                txHistory.getAddress().getAddress().toArray(),
                BasicUtils.address2Hash(txHistory.getHash()).toArray(),
                txHistory.getAddress().getAmount().toXAmount().toBytes().reverse().toArray(),
                BytesUtils.longToBytes(txHistory.getTimestamp(), true),
                BytesUtils.longToBytes(remark.length, true),
                remark));
        // value: type  +  isWalletAddress +address hash +txHashLow+ amount + timestamp + remark_length + remark
        txHistorySource.put(key, value);
        log.info("MySQL write exception, transaction history stored in Rocksdb. " + txHistory);
    }

    public List<TxHistory> getAllTxHistoryFromRocksdb() {
        List<TxHistory> res = Lists.newArrayList();
        Set<byte[]> Keys = txHistorySource.keys();
        for (byte[] key : Keys) {
            byte[] txHistoryBytes = txHistorySource.get(key);
            byte type = BytesUtils.subArray(txHistoryBytes, 0, 1)[0];
            boolean isAddress = BytesUtils.subArray(txHistoryBytes, 1, 1)[0] == 1;
            XdagField.FieldType fieldType = XdagField.FieldType.fromByte(type);
            Bytes32 addresshashlow = Bytes32.wrap(BytesUtils.subArray(txHistoryBytes, 2, 32));
            Bytes32 txhashlow = Bytes32.wrap(BytesUtils.subArray(txHistoryBytes, 34, 32));
            String hash = BasicUtils.hash2Address(txhashlow);
            XAmount amount =
                    XAmount.ofXAmount(Bytes.wrap(BytesUtils.subArray(txHistoryBytes, 66, 8)).reverse().toLong());
            long timestamp = BytesUtils.bytesToLong(BytesUtils.subArray(txHistoryBytes, 74, 8), 0, true);
            Address address = new Address(addresshashlow, fieldType, amount, isAddress);
            long remarkLength = BytesUtils.bytesToLong(BytesUtils.subArray(txHistoryBytes, 82, 8), 0, true);
            String remark = null;
            if (remarkLength != 0) {
                remark = new String(BytesUtils.subArray(txHistoryBytes, 90, (int) remarkLength),
                        StandardCharsets.UTF_8).trim();
            }
            res.add(new TxHistory(address, hash, timestamp, remark));
        }
        return res;
    }

    public void deleteAllTxHistoryFromRocksdb() {
        for (byte[] key : txHistorySource.keys()) {
            try {
                txHistorySource.delete(key);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }


    // 状态也是存在区块里面的
    public XdagStats getXdagStatus() {
        XdagStats status = null;
        byte[] value = indexSource.get(new byte[]{SETTING_STATS});
        if (value == null) {
            return null;
        }
        try {
            status = (XdagStats) deserialize(value, XdagStats.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return status;
    }

    public void saveXdagTopStatus(XdagTopStatus status) {
        byte[] value = null;
        try {
            value = serialize(status);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(new byte[]{SETTING_TOP_STATUS}, value);
    }

    // pretop状态
    public XdagTopStatus getXdagTopStatus() {
        XdagTopStatus status = null;
        byte[] value = indexSource.get(new byte[]{SETTING_TOP_STATUS});
        if (value == null) {
            return null;
        }
        try {
            status = (XdagTopStatus) deserialize(value, XdagTopStatus.class);
        } catch (DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return status;
    }

    // 存储block的过程
    public void saveBlock(Block block) {
        long time = block.getTimestamp();
        // Fix: time中只拿key的后缀（hashlow）就够了，值可以不存
        timeSource.put(BlockUtils.getTimeKey(time, block.getHashLow()), new byte[]{0});
        blockSource.put(block.getHashLow().toArray(), block.getXdagBlock().getData().toArray());
        saveBlockSums(block);
        saveBlockInfo(block.getInfo());
    }

    public void saveOurBlock(int index, byte[] hashlow) {
        indexSource.put(BlockUtils.getOurKey(index, hashlow), new byte[]{0});
    }

    public Bytes getOurBlock(int index) {
        AtomicReference<Bytes> blockHashLow = new AtomicReference<>(Bytes.of(0));
        fetchOurBlocks(pair -> {
            int keyIndex = pair.getKey();
            if (keyIndex == index) {
                if (pair.getValue() != null && pair.getValue().getHashLow() != null) {
                    blockHashLow.set(pair.getValue().getHashLow());
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }
            return Boolean.FALSE;
        });
        return blockHashLow.get();
    }

    public int getKeyIndexByHash(Bytes32 hashlow) {
        AtomicInteger keyIndex = new AtomicInteger(-1);
        fetchOurBlocks(pair -> {
            Block block = pair.getValue();
            if (hashlow.equals(block.getHashLow())) {
                int index = pair.getKey();
                keyIndex.set(index);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
        return keyIndex.get();
    }

    public void removeOurBlock(byte[] hashlow) {
        fetchOurBlocks(pair -> {
            Block block = pair.getValue();
            if (equalBytes(hashlow, block.getHashLow().toArray())) {
                int index = pair.getKey();
                indexSource.delete(BlockUtils.getOurKey(index, hashlow));
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public void fetchOurBlocks(Function<Pair<Integer, Block>, Boolean> function) {
        indexSource.fetchPrefix(new byte[]{OURS_BLOCK_INFO}, pair -> {
            int index = BlockUtils.getOurIndex(pair.getKey());
            assert BlockUtils.getOurHash(pair.getKey()) != null;
            Block block = getBlockInfoByHash(Bytes32.wrap(Objects.requireNonNull(BlockUtils.getOurHash(pair.getKey()))));
            if (function.apply(Pair.of(index, block))) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public void saveBlockSums(Block block) {
        long size = 512;
        long sum = block.getXdagBlock().getSum();
        long time = block.getTimestamp();
        List<String> filename = FileUtils.getFileName(time);
        for (int i = 0; i < filename.size(); i++) {
            updateSum(filename.get(i), sum, size, (time >> (40 - 8 * i)) & 0xff);
        }
    }

    public MutableBytes getSums(String key) {
        byte[] value = indexSource.get(BytesUtils.merge(SUMS_BLOCK_INFO, key.getBytes(StandardCharsets.UTF_8)));
        if (value == null) {
            return null;
        } else {
            MutableBytes sums = null;
            try {
                sums = MutableBytes.wrap((byte[]) deserialize(value, byte[].class));
            } catch (DeserializationException e) {
                log.error(e.getMessage(), e);
            }
            return sums;
        }
    }

    public void putSums(String key, Bytes sums) {
        byte[] value = null;
        try {
            value = serialize(sums.toArray());
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(BytesUtils.merge(SUMS_BLOCK_INFO, key.getBytes(StandardCharsets.UTF_8)), value);
    }

    public void updateSum(String key, long sum, long size, long index) {
        MutableBytes sums = getSums(key);
        if (sums == null) {
//            sums = new byte[4096];
            sums = MutableBytes.create(4096);
//            System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, (int) (16 * index), 8);
            sums.set((int) (16 * index), Bytes.wrap(BytesUtils.longToBytes(sum, true)));
//            System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, (int) (index * 16 + 8), 8);
            sums.set((int) (index * 16 + 8), Bytes.wrap(BytesUtils.longToBytes(size, true)));
            putSums(key, sums);
        } else {
            // size + sum
//            byte[] data = ArrayUtils.subarray(sums, 16 * (int)index, 16 * (int)index + 16);
            MutableBytes data = sums.slice(16 * (int) index, 16).mutableCopy();
//            sum += BytesUtils.bytesToLong(data, 0, true);
            sum += data.getLong(0, ByteOrder.LITTLE_ENDIAN);
//            size += BytesUtils.bytesToLong(data, 8, true);
            size += data.getLong(8, ByteOrder.LITTLE_ENDIAN);
//            System.arraycopy(BytesUtils.longToBytes(sum, true), 0, data, 0, 8);
            data.set(0, Bytes.wrap(BytesUtils.longToBytes(sum, true)));
//            System.arraycopy(BytesUtils.longToBytes(size, true), 0, data, 8, 8);
            data.set(8, Bytes.wrap(BytesUtils.longToBytes(size, true)));
//            System.arraycopy(data, 0, sums, 16 * (int)index, 16);
            sums.set(16 * (int) index, data.slice(0, 16));
            putSums(key, sums);
        }
    }

    public int loadSum(long starttime, long endtime, MutableBytes sums) {
        int level;
        String key;
        endtime -= starttime;

        if (endtime == 0 || (endtime & (endtime - 1)) != 0) {
            return -1;
        }
//        if (endtime == 0 || (endtime & (endtime - 1)) != 0 || (endtime & 0xFFFEEEEEEEEFFFFFL) != 0) return -1;

        for (level = -6; endtime != 0; level++, endtime >>= 4) {
        }

        List<String> files = FileUtils.getFileName((starttime) & 0xffffff000000L);

        if (level < 2) {
            key = files.get(3);
        } else if (level < 4) {
            key = files.get(2);
        } else if (level < 6) {
            key = files.get(1);
        } else {
            key = files.get(0);
        }

        Bytes buf = getSums(key);
        if (buf == null) {
//            Arrays.fill(sums, (byte)0);
            sums.fill((byte) 0);
            return 1;
        }
        long size = 0;
        long sum = 0;
        if ((level & 1) != 0) {
//            Arrays.fill(sums, (byte)0);
            sums.fill((byte) 0);
            for (int i = 1; i <= 256; i++) {
//                long totalsum = BytesUtils.bytesToLong(buf, i * 16, true);
                long totalsum = buf.getLong((i-1) * 16, ByteOrder.LITTLE_ENDIAN);
                sum += totalsum;
//                long totalsize = BytesUtils.bytesToLong(buf, i * 16 + 8, true);
                long totalsize = buf.getLong((i-1) * 16 + 8, ByteOrder.LITTLE_ENDIAN);
                size += totalsize;
                if (i % 16 == 0) {
//                    System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, i - 16, 8);
                    sums.set(i - 16, Bytes.wrap(BytesUtils.longToBytes(sum, true)));
//                    System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, i - 8, 8);
                    sums.set(i - 8, Bytes.wrap(BytesUtils.longToBytes(size, true)));
                    sum = 0;
                    size = 0;
                }
            }
        } else {
            long index = (starttime >> (level + 4) * 4) & 0xf0;
//            System.arraycopy(buf, (int) (index * 16), sums, 0, 16 * 16);
            sums.set(0, buf.slice((int) index * 16, 16 * 16));
        }
        return 1;
    }

    public void saveBlockInfo(BlockInfo blockInfo) {
        byte[] value = null;
        try {
            value = serialize(blockInfo);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(BytesUtils.merge(HASH_BLOCK_INFO, blockInfo.getHashlow()), value);
        // 如果区块是主块的话顺便保存对应的高度信息
        // TODO: paulochen 如果回滚了，对应高度的键值对该怎么更新(直接让其height=0的区块覆盖)
//        if (blockInfo.getHeight() > 0) {
        indexSource.put(BlockUtils.getHeight(blockInfo.getHeight()), blockInfo.getHashlow());
//        } else {
//            indexSource.get()
//        }
    }

    public boolean hasBlock(Bytes32 hashlow) {
        return blockSource.get(hashlow.toArray()) != null;
    }

    public boolean hasBlockInfo(Bytes32 hashlow) {
        return indexSource.get(BytesUtils.merge(HASH_BLOCK_INFO, hashlow.toArray())) != null;
    }

    public List<Block> getBlocksUsedTime(long startTime, long endTime) {
        List<Block> res = Lists.newArrayList();
        long time = startTime;
        while (time < endTime) {
            List<Block> blocks = getBlocksByTime(time);
            time += 0x10000;
            if (CollectionUtils.isEmpty(blocks)) {
                continue;
            }
            res.addAll(blocks);
        }
        return res;
    }

    public List<Block> getBlocksByTime(long startTime) {
        List<Block> blocks = Lists.newArrayList();
        byte[] keyPrefix = BlockUtils.getTimeKey(startTime, null);
        List<byte[]> keys = timeSource.prefixKeyLookup(keyPrefix);
        for (byte[] bytes : keys) {
            // 1 + 8 : prefix + time
            byte[] hash = BytesUtils.subArray(bytes, 1 + 8, 32);
            Block block = getBlockByHash(Bytes32.wrap(hash), true);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    // ADD: 通过高度获取区块
    public Block getBlockByHeight(long height) {
        byte[] hashlow = indexSource.get(BlockUtils.getHeight(height));
        if (hashlow == null) {
            return null;
        }
        return getBlockByHash(Bytes32.wrap(hashlow), false);
    }

    public Block getBlockByHash(Bytes32 hashlow, boolean isRaw) {
        if (isRaw) {
            return getRawBlockByHash(hashlow);
        }
        return getBlockInfoByHash(hashlow);
    }

    public Block getRawBlockByHash(Bytes32 hashlow) {
        Block block = getBlockInfoByHash(hashlow);
        if (block == null) {
            return null;
        }
//        log.debug("Data:{}",Hex.toHexString(blockSource.get(hashlow)));
        // 没有源数据
        if (blockSource.get(hashlow.toArray()) == null) {
//            log.error("No block origin data");
            return null;
        }
        block.setXdagBlock(new XdagBlock(blockSource.get(hashlow.toArray())));
        block.setParsed(false);
        block.parse();
        return block;
    }

    public Block getBlockInfoByHash(Bytes32 hashlow) {
        if (!hasBlockInfo(hashlow)) {
            return null;
        }
        BlockInfo blockInfo = null;
        byte[] value = indexSource.get(BytesUtils.merge(HASH_BLOCK_INFO, hashlow.toArray()));
        if (value == null) {
            return null;
        } else {
            try {
                blockInfo = (BlockInfo) deserialize(value, BlockInfo.class);
            } catch (DeserializationException e) {
                log.error("hash low:" + hashlow.toHexString());
                log.error("can't deserialize data:{}", Hex.toHexString(value));
                log.error(e.getMessage(), e);
            }
        }
        return new Block(blockInfo);
    }

    public boolean isSnapshotBoot() {
        byte[] data = indexSource.get(new byte[]{SNAPSHOT_BOOT});
        if (data == null) {
            return false;
        } else {
            int res = BytesUtils.bytesToInt(data, 0, false);
            return res == 1;
        }
    }

    public void setSnapshotBoot() {
        indexSource.put(new byte[]{SNAPSHOT_BOOT}, BytesUtils.intToBytes(1, false));
    }

    public void savePreSeed(byte[] preseed) {
        indexSource.put(new byte[]{SNAPSHOT_PRESEED}, preseed);
    }

    public byte[] getPreSeed() {
        return indexSource.get(new byte[]{SNAPSHOT_PRESEED});
    }

}

