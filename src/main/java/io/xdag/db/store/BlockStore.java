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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.xdag.core.BlockInfo;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.core.XdagStats;
import org.spongycastle.util.encoders.Hex;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.db.KVSource;
import io.xdag.db.SimpleFileStore;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlockStore {
    public static final byte CHAIN_STATE = 0x0A;
    public static final byte SETTING_VERSION                       =  0x10;
    public static final byte SETTING_CREATED                       =  0x11;
    public static final byte SETTING_STATS                         =  0x12;
    public static final byte SETTING_EXT_STATS                     =  0x13;
    public static final byte SETTING_PRE_TOP_MAIN                  =  0x14;
    public static final byte SETTING_TOP_MAIN                      =  0x15;
    public static final byte SETTING_OUR_FIRST_HASH                =  0x16;
    public static final byte SETTING_OUR_LAST_HASH                 =  0x17;
    public static final byte SETTING_OUR_BALANCE                   =  0x18;
    public static final byte SETTING_CUR_TIME                      =  0x19;
    public static final byte HASH_BLOCK_INFO                       =  0x22;

//    private Mapper<BlockInfo> biMapper;
    private final Kryo kryo = new Kryo();

    /** pretop */
    private static final byte[] PRETOP = Hex.decode("DDDDDDDDDDDDDDDD");
    /** pretop diff */
    private static final byte[] PRETOPDIFF = Hex.decode("CCCCCCCCCCCCCCCC");
    /** origin pretop diff */
    private static final byte[] ORIGINPRETOP = Hex.decode("FFFFFFFFFFFFFFFE");
    /** origin pretop diff */
    private static final byte[] ORIGINPRETOPDIFF = Hex.decode("FFFFFFFFFFFFFFEF");
    private static final byte[] GLOBAL_ADDRESS = Hex.decode("FFFFFFFFFFFFFEFF");
    /** <prefix-hash,value> eg:<diff-hash,blockdiff> */
    private KVSource<byte[], byte[]> indexSource;
    /** <hash,rawdata> */
    private KVSource<byte[], byte[]> blockSource;
    /** <time-hash,hash> */
    private KVSource<byte[], byte[]> timeSource;
    private SimpleFileStore simpleFileStore;

    public BlockStore(
            KVSource<byte[], byte[]> index,
            KVSource<byte[], byte[]> block,
            KVSource<byte[], byte[]> time,
            SimpleFileStore simpleFileStore) {
        this.indexSource = index;
        this.blockSource = block;
        this.timeSource = time;
        this.simpleFileStore = simpleFileStore;

        kryo.register(BlockInfo.class);
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
    }

    public byte[] serialize(final Object obj) throws SerializationException {
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

    public Object deserialize(final byte[] bytes, Class type) throws DeserializationException {
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
        blockSource.init();
        timeSource.init();
    }

    public void reset() {
        indexSource.reset();
        blockSource.reset();
        timeSource.reset();
//        indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(0, false));
//        indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(0, false));
        simpleFileStore.reset();
//        Runnable queueProducer = this::processQueue;
//        sumFuture = executorService.submit(queueProducer);
    }

//    public void processQueue() {
//        try {
//            while (isRuning) {
//                Block block = blockQueue.take();
//                simpleFileStore.saveBlockSums(block);
//            }
//        } catch (InterruptedException e) {
//            log.error(e.getMessage(), e);
//        }
//    }

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
        if (block == null) {
            return;
        }
//        log.debug("Save Block:" + block);
//        blockQueue.add(block);

        long timeIndex = block.getTimestamp();
        timeSource.put(getTimeKey(timeIndex, block.getHashLow()), block.getHashLow());
        blockSource.put(block.getHashLow(), block.getXdagBlock().getData());
//        if (indexSource.get(BLOCK_SIZE) != null
//                && BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false) != 0) {
//            long blocksize = BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false) + 1;
//            indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(blocksize, false));
//        } else {
//            indexSource.put(BLOCK_SIZE, BytesUtils.longToBytes(1, false));
//            // 赋值localaddress
//            indexSource.put(GLOBAL_ADDRESS, block.getHashLow());
//        }
        simpleFileStore.saveBlockSums(block);
        saveBlockInfo(block.getInfo());
    }

    public void saveBlockInfo(BlockInfo blockInfo) {
        byte[] value = null;
        try {
            value = serialize(blockInfo);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        indexSource.put(BytesUtils.merge(HASH_BLOCK_INFO, blockInfo.getHashlow()), value);
//        indexSource.put(
//                BytesUtils.merge(BLOCK_MAXDIFF, block.getHashLow()),
//                BytesUtils.bigIntegerToBytes(block.getDifficulty(), 16, false));
//        if (block.getMaxDifflink() != null) {
//            indexSource.put(
//                    BytesUtils.merge(BLOCK_MAXDIFFLINK, block.getHashLow()),
//                    block.getMaxDifflink().getHashLow());
//        }
//        if (block.getRef() != null) {
//            indexSource.put(BytesUtils.merge(BLOCK_REF, block.getHashLow()), block.getRef().getHashLow());
//        }
//        indexSource.put(
//                BytesUtils.merge(BLOCK_AMOUNT, block.getHashLow()),
//                BytesUtils.longToBytes(block.getAmount(), false));
//        indexSource.put(
//                BytesUtils.merge(BLOCK_FLAG, block.getHashLow()),
//                BytesUtils.intToBytes(block.getFlags(), false));
//        indexSource.put(
//                BytesUtils.merge(BLOCK_FEE, block.getHashLow()),
//                BytesUtils.longToBytes(block.getFee(), false));
//        indexSource.put(
//                BytesUtils.merge(BLOCK_TIME, block.getHashLow()),
//                BytesUtils.longToBytes(block.getTimestamp(), false));
//        indexSource.put(BytesUtils.merge(BLOCK_HASH, block.getHashLow()), block.getHash());
//        indexSource.put(BytesUtils.merge(BLOCK_HEIGHT, block.getHashLow()), BytesUtils.longToBytes(block.getHeight(), false));
    }

    public boolean hasBlock(byte[] hashlow) {
        return blockSource.get(hashlow) != null;
    }

    /*
     * @Author punk
     * 
     * @Description 获取时间索引键
     * 
     * @Date 2020/4/22
     * 
     * @Param [timestamp]
     * 
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
        block.setXdagBlock(new XdagBlock(blockSource.get(hashlow)));
        block.setParsed(false);
        block.parse();
        return block;
    }

    public Block getBlockInfoByHash(byte[] hashlow) {

        if (!hasBlock(hashlow)) {
            return null;
        }
//        long timestamp = BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_TIME, hashlow)), 0, false);
//        long amount = BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_AMOUNT, hashlow)), 0, false);
//        BigInteger diff;
//        if (indexSource.get(BytesUtils.merge(BLOCK_MAXDIFF, hashlow)) == null) {
//            diff = BigInteger.ZERO;
//        } else {
//            diff = BytesUtils.bytesToBigInteger(
//                    indexSource.get(BytesUtils.merge(BLOCK_MAXDIFF, hashlow)), 0, false);
//        }
//        long fee = BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_FEE, hashlow)), 0, false);
//        long height = BytesUtils.bytesToLong(indexSource.get(BytesUtils.merge(BLOCK_HEIGHT, hashlow)), 0, false);
//        byte[] ref = indexSource.get(BytesUtils.merge(BLOCK_REF, hashlow));
//        byte[] maxdiffLink = indexSource.get(BytesUtils.merge(BLOCK_MAXDIFFLINK, hashlow));
//        int flags = BytesUtils.bytesToInt(indexSource.get(BytesUtils.merge(BLOCK_FLAG, hashlow)), 0, false);
//        Block block = new Block(timestamp, amount, diff, fee, ref, maxdiffLink, flags);
//        block.setHashLow(hashlow);
//        block.setHash(indexSource.get(BytesUtils.merge(BLOCK_HASH, hashlow)));
//        block.setHeight(height);

        BlockInfo blockInfo = null;
        byte[] value = indexSource.get(BytesUtils.merge(HASH_BLOCK_INFO, hashlow));
        if(value == null) {
            return null;
        } else {
            try {
                blockInfo = (BlockInfo)deserialize(value, BlockInfo.class);
            } catch (DeserializationException e) {
                e.printStackTrace();
            }
        }
        return new Block(blockInfo);
    }

//    public void updateBlockInfo(byte TypePrefix, Block block) {
//        byte[] hashlow = block.getHashLow();
//        byte[] value;
//        switch (TypePrefix) {
//        case BLOCK_MAXDIFF:
//            value = BytesUtils.bigIntegerToBytes(block.getDifficulty(), 16, false);
//            break;
//        case BLOCK_MAXDIFFLINK:
//            value = block.getMaxDifflink().getHashLow();
//            break;
//        case BLOCK_AMOUNT:
//            value = BytesUtils.longToBytes(block.getAmount(), false);
//            break;
//        case BLOCK_REF:
//            if (block.getRef() == null) {
//                value = null;
//            } else {
//                value = block.getRef().getHashLow();
//            }
//            break;
//        case BLOCK_FLAG:
//            value = BytesUtils.intToBytes(block.getFlags(), false);
//            break;
//        case BLOCK_TIME:
//            value = BytesUtils.longToBytes(block.getTimestamp(), false);
//            break;
//        case BLOCK_FEE:
//            value = BytesUtils.longToBytes(block.getFee(), false);
//            break;
//        case BLOCK_HEIGHT:
//            value = BytesUtils.longToBytes(block.getHeight(), false);
//            break;
//        default:
//            throw new IllegalStateException("Unexpected value: " + TypePrefix);
//        }
//        if (value == null) {
//            indexSource.delete(BytesUtils.merge(TypePrefix, hashlow));
//        } else {
//            indexSource.put(BytesUtils.merge(TypePrefix, hashlow), value);
//        }
//    }

//    public void updateBlockKeyIndex(byte[] hashlow, int keyindex) {
//        indexSource.put(
//                BytesUtils.merge(BLOCK_KEY_INDEX, hashlow), BytesUtils.intToBytes(keyindex, false));
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

//    public void mainNumberInc() {
//        long currentsize = BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
//        indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
//    }
//
//    public void mainNumberDec() {
//        long currentsize = BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
//        indexSource.put(MAIN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
//    }

//    public long getMainNumber() {
//        return BytesUtils.bytesToLong(indexSource.get(MAIN_SIZE), 0, false);
//    }
//
//    public long getBlockNumber() {
//        return BytesUtils.bytesToLong(indexSource.get(BLOCK_SIZE), 0, false);
//    }

    public void setOriginpretopdiff(BigInteger pretopDiff) {
        indexSource.put(ORIGINPRETOPDIFF, BytesUtils.bigIntegerToBytes(pretopDiff, 16, false));
    }

    public void setPretopDiff(BigInteger pretopDiff) {
        indexSource.put(PRETOPDIFF, BytesUtils.bigIntegerToBytes(pretopDiff, 16, false));
    }

    public SimpleFileStore getSimpleFileStore() {
        return simpleFileStore;
    }

    public void closeSum() {
        log.debug("Sums service close...");
    }

    public byte[] getGlobalAddress() {
        if (indexSource.get(GLOBAL_ADDRESS) != null) {
            return indexSource.get(GLOBAL_ADDRESS);
        } else {
            return null;
        }
    }
}
