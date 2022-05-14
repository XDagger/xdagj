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

package io.xdag.db.store;

import static io.xdag.utils.BytesUtils.equalBytes;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLong;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockInfo;
import io.xdag.core.TxHistory;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.core.XdagStats;
import io.xdag.core.XdagTopStatus;
import io.xdag.db.KVSource;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.snapshot.core.SnapshotInfo;
import io.xdag.utils.BytesUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class BlockStore {

    public static final byte SETTING_STATS = 0x10;
    public static final byte TIME_HASH_INFO = 0x20;
    public static final byte HASH_BLOCK_INFO = 0x30;
    public static final byte SUMS_BLOCK_INFO = 0x40;
    public static final byte OURS_BLOCK_INFO = 0x50;


    public static final byte SETTING_TOP_STATUS = 0x60;


    public static final byte SNAPSHOT_BOOT = 0x70;

    // ADD: 根据高度查询,添加新的标志
    public static final byte BLOCK_HEIGHT = (byte) 0x80;

    public static final byte SNAPSHOT_PRESEED = (byte) 0x90;

    // tx history
    public static final byte TX_HISTORY = (byte) 0xa0;

    public static final String SUM_FILE_NAME = "sums.dat";

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

    public BlockStore(
            KVSource<byte[], byte[]> index,
            KVSource<byte[], byte[]> time,
            KVSource<byte[], byte[]> block) {
        this.indexSource = index;
        this.timeSource = time;
        this.blockSource = block;
        this.txHistorySource = null;
        this.kryo = new Kryo();
        kryoRegister();
    }

    public BlockStore(
            KVSource<byte[], byte[]> index,
            KVSource<byte[], byte[]> time,
            KVSource<byte[], byte[]> block,
            KVSource<byte[], byte[]> txHistory) {
        this.indexSource = index;
        this.timeSource = time;
        this.blockSource = block;
        this.kryo = new Kryo();
        this.txHistorySource = txHistory;
        kryoRegister();
    }

    public static List<String> getFileName(long time) {
        List<String> files = Lists.newArrayList(SUM_FILE_NAME);
        StringBuilder stringBuffer = new StringBuilder(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer + SUM_FILE_NAME);
        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer + SUM_FILE_NAME);
        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer + SUM_FILE_NAME);
        return files;
    }

    public static byte[] getTimeKey(long timestamp, Bytes32 hashlow) {
        long t = UnsignedLong.fromLongBits(timestamp >> 16).longValue();
        byte[] key = BytesUtils.merge(TIME_HASH_INFO, BytesUtils.longToBytes(t, false));
        if (hashlow == null) {
            return key;
        }
        return BytesUtils.merge(key, hashlow.toArray());
    }

    public static byte[] getOurKey(int index, byte[] hashlow) {
        byte[] key = BytesUtils.merge(OURS_BLOCK_INFO, BytesUtils.intToBytes(index, false));
        key = BytesUtils.merge(key, hashlow);
        return key;
    }

    // ADD: 高度键
    public static byte[] getHeight(long height) {
        return BytesUtils.merge(BLOCK_HEIGHT, BytesUtils.longToBytes(height, false));
    }

    private static int getOurIndex(byte[] key) {
        try {
            byte[] index = BytesUtils.subArray(key, 1, 4);
            return BytesUtils.bytesToInt(index, 0, false);
        } catch (Exception e) {
            return 0;
        }
//        return BytesUtils.bytesToInt(key, 1, false);
    }

    private static byte[] getOurHash(byte[] key) {
        try {
            return BytesUtils.subArray(key, 5, 32);
        } catch (Exception e) {
            return null;
        }
    }

    private void kryoRegister() {
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
        kryo.register(XdagTopStatus.class);
        kryo.register(SnapshotInfo.class);
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

    //状态也是存在区块里面的
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
        timeSource.put(getTimeKey(time, block.getHashLow()), new byte[]{0});
        blockSource.put(block.getHashLow().toArray(), block.getXdagBlock().getData().toArray());
        saveBlockSums(block);
        saveBlockInfo(block.getInfo());
    }

    public void saveOurBlock(int index, byte[] hashlow) {
        indexSource.put(getOurKey(index, hashlow), new byte[]{0});
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
                indexSource.delete(getOurKey(index, hashlow));
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public void fetchOurBlocks(Function<Pair<Integer, Block>, Boolean> function) {
        indexSource.fetchPrefix(new byte[]{OURS_BLOCK_INFO}, pair -> {
            int index = getOurIndex(pair.getKey());
//            Block block = getBlockInfoByHash(Bytes32.wrap(pair.getValue()));
            Block block = getBlockInfoByHash(Bytes32.wrap(getOurHash(pair.getKey())));
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
        List<String> filename = getFileName(time);
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
            ;
        }

        List<String> files = getFileName((starttime) & 0xffffff000000L);

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
            for (int i = 0; i < 256; i++) {
//                long totalsum = BytesUtils.bytesToLong(buf, i * 16, true);
                long totalsum = buf.getLong(i * 16, ByteOrder.LITTLE_ENDIAN);
                sum += totalsum;
//                long totalsize = BytesUtils.bytesToLong(buf, i * 16 + 8, true);
                long totalsize = buf.getLong(i * 16 + 8, ByteOrder.LITTLE_ENDIAN);
                size += totalsize;
                if (i % 16 == 0 && i != 0) {
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
        indexSource.put(getHeight(blockInfo.getHeight()), blockInfo.getHashlow());
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
        byte[] keyPrefix = getTimeKey(startTime, null);
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

    //ADD: 通过高度获取区块
    public Block getBlockByHeight(long height) {
        byte[] hashlow = indexSource.get(getHeight(height));
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
            log.error("No block origin data");
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


    public void saveTxHistory(Bytes32 addressHashlow, Bytes32 txHashlow, XdagField.FieldType type, BigInteger amount,
            long time, int id, byte[] remark) { // id is used to avoid repeat key
        if (remark == null) {
            remark = new byte[]{};
        }
        byte[] key = BytesUtils.merge(TX_HISTORY,
                BytesUtils.merge(addressHashlow.toArray(), BytesUtils.merge(txHashlow.toArray(),
                        BytesUtils.intToBytes(id, true)))); // key 0xa0 + address hash + tx hash + id

        byte[] value = null;
        value = BytesUtils.merge(type.asByte(),
                BytesUtils.merge(txHashlow.toArray(),
                        BytesUtils.merge(BytesUtils.bigIntegerToBytes(amount, 8, true),
                                BytesUtils.merge(BytesUtils.longToBytes(time, true),
                                        BytesUtils.merge(BytesUtils.longToBytes(remark.length, true),
                                                remark))))); // type + tx hash + amount + time + remark_length + remark
        txHistorySource.put(key, value);
    }

    public List<TxHistory> getTxHistoryByAddress(Bytes32 addressHashlow) {
        List<byte[]> values = txHistorySource.prefixValueLookup(BytesUtils.merge(TX_HISTORY, addressHashlow.toArray()));
        List<TxHistory> res = new ArrayList<>();

        for (byte[] value : values) {
            byte type = BytesUtils.subArray(value, 0, 1)[0];
            XdagField.FieldType fieldType = XdagField.FieldType.fromByte(type);
            Bytes32 hashlow = Bytes32.wrap(BytesUtils.subArray(value, 1, 32));
            long amount = BytesUtils.bytesToLong(BytesUtils.subArray(value, 33, 8), 0, true);
            long timestamp = BytesUtils.bytesToLong(BytesUtils.subArray(value, 41, 8), 0, true);
            Address address = new Address(hashlow, fieldType, amount);

            long remarkLength = BytesUtils.bytesToLong(BytesUtils.subArray(value, 49, 8), 0, true);

            String remark = "";
            if (remarkLength != 0) {
                remark = new String(BytesUtils.subArray(value, 57, (int) remarkLength), StandardCharsets.UTF_8).trim();
            }
            res.add(new TxHistory(address, timestamp, remark));
        }
        return res;

    }
}

