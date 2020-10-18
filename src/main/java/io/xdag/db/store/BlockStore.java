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

import cn.hutool.core.lang.Pair;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLong;
import io.xdag.core.Block;
import io.xdag.core.BlockInfo;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagStats;
import io.xdag.db.KVSource;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FastByteComparisons;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class BlockStore {
    public static final byte SETTING_STATS                         =  0x10;
    public static final byte TIME_HASH_INFO                        =  0x20;
    public static final byte HASH_BLOCK_INFO                       =  0x30;
    public static final byte SUMS_BLOCK_INFO                       =  0x40;
    public static final byte OURS_BLOCK_INFO                       =  0x50;

    public static final String SUM_FILE_NAME = "sums.dat";

    private final Kryo kryo;

    /** <prefix-hash,value> eg:<diff-hash,blockDiff> */
    private final KVSource<byte[], byte[]> indexSource;
    /** <prefix-time-hash,hash> */
    private final KVSource<byte[], byte[]> timeSource;
    /** <hash,rawData> */
    private final KVSource<byte[], byte[]> blockSource;

    public BlockStore(
            KVSource<byte[], byte[]> index,
            KVSource<byte[], byte[]> time,
            KVSource<byte[], byte[]> block) {
        this.indexSource = index;
        this.timeSource = time;
        this.blockSource = block;
        this.kryo = new Kryo();
        kryoRegister();
    }

    private void kryoRegister() {
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
    }

    private byte[] serialize(final Object obj) throws SerializationException {
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

    private Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
        try {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            final Input input = new Input(inputStream);
            return kryo.readObject(input, type);
        } catch (final IllegalArgumentException | KryoException exception) {
            throw new DeserializationException(exception.getMessage(), exception);
        }
    }

    public void init() {
        indexSource.init();
        timeSource.init();
        blockSource.init();
    }

    public void reset() {
        indexSource.reset();
        timeSource.reset();
        blockSource.reset();
    }

    public void saveXdagStatus(XdagStats status) {
        byte[] value = null;
        try {
            value = serialize(status);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(new byte[] {SETTING_STATS}, value);
    }

    public XdagStats getXdagStatus() {
        XdagStats status = null;
        byte[] value = indexSource.get(new byte[] {SETTING_STATS});
        if(value == null) {
            return null;
        }
        try {
            status = (XdagStats)deserialize(value, XdagStats.class);
        } catch ( DeserializationException e) {
            log.error(e.getMessage(), e);
        }
        return status;
    }

    // 存储block的过程
    public void saveBlock(Block block) {
        long time = block.getTimestamp();
        timeSource.put(getTimeKey(time, block.getHashLow()), block.getHashLow());
        blockSource.put(block.getHashLow(), block.getXdagBlock().getData());
        saveBlockSums(block);
        saveBlockInfo(block.getInfo());
    }

    public void saveOurBlock(int index, byte[] hashlow) {
        indexSource.put(getOurKey(index), hashlow);
    }

    public byte[] getOurBlock(int index) {
        return indexSource.get(getOurKey(index));
    }

    public void removeOurBlock(byte[] hashlow) {
        fetchOurBlocks(pair -> {
            Block block = pair.getValue();
            if(FastByteComparisons.equalBytes(hashlow, block.getHashLow())) {
                int index = pair.getKey();
                indexSource.delete(getOurKey(index));
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public void fetchOurBlocks(Function<Pair<Integer, Block>, Boolean> function) {
        indexSource.fetchPrefix(new byte[]{OURS_BLOCK_INFO}, pair -> {
            int index = getOurIndex(pair.getKey());
            Block block = getBlockInfoByHash(pair.getValue());
            if(function.apply(Pair.of(index, block))) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public static List<String> getFileName(long time) {
        List<String> files = Lists.newArrayList(SUM_FILE_NAME);
        StringBuilder stringBuffer = new StringBuilder(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer.toString() + SUM_FILE_NAME);
        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer.toString() + SUM_FILE_NAME);
        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
        stringBuffer.append("/");
        files.add(stringBuffer.toString() + SUM_FILE_NAME);
        return files;
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

    public byte[] getSums(String key) {
        byte[] value = indexSource.get(BytesUtils.merge(SUMS_BLOCK_INFO, key.getBytes()));
        if(value == null) {
            return null;
        } else {
            byte[] sums = null;
            try {
                sums = (byte[])deserialize(value, byte[].class);
            } catch (DeserializationException e) {
                log.error(e.getMessage(), e);
            }
            return sums;
        }
    }

    public void putSums(String key, byte[] sums) {
        byte[] value = null;
        try {
            value = serialize(sums);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(BytesUtils.merge(SUMS_BLOCK_INFO, key.getBytes()), value);
    }

    public void updateSum(String key, long sum, long size, long index) {
        byte[] sums = getSums(key);
        if (sums == null) {
            sums = new byte[4096];
            System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, (int) (16 * index), 8);
            System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, (int) (index * 16 + 8), 8);
            putSums(key, sums);
        } else {
            // size + sum
            byte[] data = ArrayUtils.subarray(sums, 16 * (int)index, 16 * (int)index + 16);
            sum += BytesUtils.bytesToLong(data, 0, true);
            size += BytesUtils.bytesToLong(data, 8, true);
            System.arraycopy(BytesUtils.longToBytes(sum, true), 0, data, 0, 8);
            System.arraycopy(BytesUtils.longToBytes(size, true), 0, data, 8, 8);
            System.arraycopy(data, 0, sums, 16 * (int)index, 16);
            putSums(key, sums);
        }
    }

    public int loadSum(long starttime, long endtime, byte[] sums) {
        int level;
        String key;
        endtime -= starttime;

        if (endtime == 0 || (endtime & (endtime - 1)) != 0) return -1;
//        if (endtime == 0 || (endtime & (endtime - 1)) != 0 || (endtime & 0xFFFEEEEEEEEFFFFFL) != 0) return -1;

        for (level = -6; endtime != 0; level++, endtime >>= 4);


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

        byte[] buf = getSums(key);
        if(buf == null) {
            Arrays.fill(sums, (byte)0);
            return 1;
        }
        long size = 0;
        long sum = 0;
        if ((level & 1) != 0) {
            Arrays.fill(sums, (byte)0);
            for (int i = 0; i < 256; i++) {
                long totalsum = BytesUtils.bytesToLong(buf, i * 16, true);
                sum += totalsum;
                long totalsize = BytesUtils.bytesToLong(buf, i * 16 + 8, true);
                size += totalsize;
                if (i % 16 == 0 && i != 0) {
                    System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, i - 16, 8);
                    System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, i - 8, 8);
                    sum = 0;
                    size = 0;
                }
            }
        } else {
            long index = (starttime >> (level + 4) * 4) & 0xf0;
            System.arraycopy(buf, (int) (index * 16), sums, 0, 16 * 16);
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
    }

    public boolean hasBlock(byte[] hashlow) {
        return blockSource.get(hashlow) != null;
    }

    public static byte[] getTimeKey(long timestamp, byte[] hashlow) {
        long t = UnsignedLong.fromLongBits(timestamp >> 16).longValue();
        byte[] key = BytesUtils.merge(TIME_HASH_INFO, BytesUtils.longToBytes(t, false));
        if(hashlow == null) {
            return key;
        }
        return BytesUtils.merge(key, hashlow);
    }

    public static byte[] getOurKey(int index) {
        return BytesUtils.merge(OURS_BLOCK_INFO, BytesUtils.intToBytes(index, false));
    }

    public static int getOurIndex(byte[] key) {
        return BytesUtils.bytesToInt(key, 1, false);
    }

//    public List<Block> getBlocksUsedTime(long startTime, long endTime) {
//        List<Block> res = Lists.newArrayList();
//        long time = startTime;
//        while (time < endTime) {
//            List<Block> blocks = getBlocksByTime(time);
//            time += 0x10000;
//            if (CollectionUtils.isEmpty(blocks)) {
//                continue;
//            }
//            res.addAll(blocks);
//        }
//        return res;
//    }

//    public List<Block> getBlocksByTime(long startTime) {
//        List<Block> blocks = Lists.newArrayList();
//        long key = UnsignedLong.fromLongBits(startTime >> 16).longValue();
//        byte[] keyPrefix = getTimeKey(key, null);
//        List<byte[]> keys = timeSource.prefixValueLookup(keyPrefix);
//        for (byte[] bytes : keys) {
//            Block block = getBlockByHash(bytes, true);
//            if (block != null) {
//                blocks.add(block);
//            }
//        }
//        return blocks;
//    }

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
        BlockInfo blockInfo = null;
        byte[] value = indexSource.get(BytesUtils.merge(HASH_BLOCK_INFO, hashlow));
        if(value == null) {
            return null;
        } else {
            try {
                blockInfo = (BlockInfo)deserialize(value, BlockInfo.class);
            } catch (DeserializationException e) {
                log.error(e.getMessage(), e);
            }
        }
        return new Block(blockInfo);
    }

//    public void updateBlockKeyIndex(byte[] hashlow, int keyIndex) {
//        indexSource.put(
//                BytesUtils.merge(BLOCK_KEY_INDEX, hashlow), BytesUtils.intToBytes(keyIndex, false));
//    }
//
//    public void deleteBlockKeyIndex(byte[] hashlow) {
//        if (indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)) == null) {
//            return;
//        }
//        indexSource.delete(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow));
//    }
//
//    public int getBlockKeyIndex(byte[] hashlow) {
//        if (indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)) != null) {
//            return BytesUtils.bytesToInt(
//                    indexSource.get(BytesUtils.merge(BLOCK_KEY_INDEX, hashlow)), 0, false);
//        } else {
//            // 不存在
//            return -2;
//        }
//    }
}
