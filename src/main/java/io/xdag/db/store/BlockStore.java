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
import io.xdag.core.*;
import io.xdag.db.KVSource;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FastByteComparisons;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

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


    public static final byte SETTING_TOP_STATUS                       =  0x60;

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
        kryo.register(XdagTopStatus.class);
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
    //状态也是存在区块里面的
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

    public void saveXdagTopStatus(XdagTopStatus status) {
        byte[] value = null;
        try {
            value = serialize(status);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(new byte[] {SETTING_TOP_STATUS}, value);
    }

    // pretop状态
    public XdagTopStatus getXdagTopStatus() {
        XdagTopStatus status= null;
        byte[] value = indexSource.get(new byte[] {SETTING_TOP_STATUS});
        if(value == null) {
            return null;
        }
        try {
            status = (XdagTopStatus)deserialize(value, XdagTopStatus.class);
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
        indexSource.put(getOurKey(index,hashlow), hashlow);
    }

    public byte[] getOurBlock(int index) {
        return indexSource.get(getOurKey(index,null));
    }

    public void removeOurBlock(byte[] hashlow) {
        fetchOurBlocks(pair -> {
            Block block = pair.getValue();
            if(FastByteComparisons.equalBytes(hashlow, block.getHashLow())) {
                int index = pair.getKey();
                indexSource.delete(getOurKey(index,hashlow));
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

    public static byte[] getOurKey(int index, byte[] hashlow) {
        byte[] key = BytesUtils.merge(OURS_BLOCK_INFO, BytesUtils.intToBytes(index, false));
        key = BytesUtils.merge(key,hashlow);
        return key;
    }

    public static int getOurIndex(byte[] key) {
        try {
            byte[] index = BytesUtils.subArray(key,1,4);
            return BytesUtils.bytesToInt(index,0,false);
        }catch (Exception e) {
            return 0;
        }
//        return BytesUtils.bytesToInt(key, 1, false);
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
//        long key = UnsignedLong.fromLongBits(startTime >> 16).longValue();
        byte[] keyPrefix = getTimeKey(startTime, null);
        List<byte[]> keys = timeSource.prefixValueLookup(keyPrefix);
        for (byte[] bytes : keys) {
            Block block = getBlockByHash(bytes, true);
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
        log.debug("data:{}",Hex.toHexString(blockSource.get(hashlow)));
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
        Block block = new Block(blockInfo);
        return block;
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

//00000000000000003859050000000040ffffa6878101000000000000000000005c6dc2b49e6f0d34ed258cfcc3ea5242128e1a9479977d700000000000000000586461674a0000000000000000000000000000000000000000000000000000006b0dc6bd42597c7e851e50765c54ff7806c101541ff93aeed069055754ef848a126db6d67118bd87b0285892e1cfb5732b64e143faa684de59d819f876cd1e8e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000026fa07df9cf004af7f26a7bbdfb3a005cbee0a917bdd119208fa1643422ff157
//00000000000000007f777777777777776ea8a887810100000000000000000000e6817a0a1f25b3aabc642d11f483834544181d13b203c82d0000000064000000566ea89f0a19c54b680fa2ccfb72e7fec43e6f45af6bfdf20000000064000000b2c4be0a4ab43a573e2ed5c74e29698f64223197431365dd0000000064000000874577499b94e92411da6a6f3aa232c48da77137cbebc6e40000000064000000f74b0c413847f1692e6a2bf4b514d99f60a1389edf2251580000000000000000058fc0f060c2fbb44c429c66d1614a0d1aba25899225428000000000640000008544b0443cc6b23133053a1a4935f9d491a0112eb5c398c10000000064000000deec92447a11abbc7bd2fc465602a853ad635fa167590ad20000000064000000e8c6620c6aa5644d63752280b98b9917ec5429bfb7c85076000000006400000007ea1dc8f6846d46d0abeb86c52326e4cc3d75ee2718463b00000000640000005c6dc2b49e6f0d34ed258cfcc3ea5242128e1a9479977d7000000000640000000563b2fac3351c62130ec087b4df4beb3052e37e2acf193400000000640000007c1b40afbf18dcc4eea74a72db47b9b2c549f9e723b4e0fa0000000064000000e508e9014d983e9aeee779af084f02f91553dc93aeb3bdff00000000000000001c7879f01e006b94f2aa6050e0721d87ba8b4563947f74650000000064000000
//82cddef261c33bea24b476d480b17a941c8e198f8ed547870000000064000000cd608db1387ea5573679bba7e8036630c79337d742908cd9000000006400000047a1ac2f42cc5a0158e1a078c60838a1f7a093067115e2db00000000640000006351f40eead568fefe14321280ae8b2f5d38fa9779ad84df000000006400000007b8caf403eeb788292a7ea87baf727754d6608ad082b55a00000000000000002e25416f10ee057a88f4de67f5711d4588ae2436726328b50000000064000000d377a8e0754e8739488bc36f62f626d9114ec80bf9cb00be00000000640000006351f40eead568fefe14321280ae8b2f5d38fa9779ad84df000000006400000007ea1dc8f6846d46d0abeb86c52326e4cc3d75ee2718463b000000000000000061ca4abdf7129b5625213b84def58b258b460a79ba9584780000000000000000 525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3
//525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3 525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3525c7e8ea2a4e0b94b24486b7484e7109685b0adb582ec08059601491effb2a3
//d1f8dfcc55975e3afac9ca26edfa9fc9ddffe60e76537ebd78073947b41a85836deecb9d634fa0e35c966f0b30a068e6c47b25fc2aa73fabbbe4e2124ddc0a1d0000000000000000000000000000000000000000000000000000000000000000
