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

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.db.OrphanBlockStore;
import io.xdag.utils.BytesUtils;

import java.util.*;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;

import com.google.common.collect.Lists;

@Getter
@Slf4j
public class OrphanBlockStoreImpl implements OrphanBlockStore {


    // <hash,nexthash>
    private final KVSource<byte[], byte[]> orphanSource;

    public OrphanBlockStoreImpl(KVSource<byte[], byte[]> orphan) {
        this.orphanSource = orphan;
    }

    public void start() {
        this.orphanSource.init();
        if (orphanSource.get(ORPHAN_SIZE) == null) {
            this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
        }
    }

    @Override
    public void stop() {
        orphanSource.close();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public void reset() {
        this.orphanSource.reset();
        this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
    }
    /**
     * 1. 从 orphanSource 中获取所有 prefix 为 ORPHAN_PREFEX 的数据
     * 2. 解析为 OrphanMeta (hashlow, nonce, isTx, time, fee, address)
     * 3. 链接块 isTx=false，按时间排序
     * 4. 交易块 isTx=true，先按 fee 降序 + time 升序排序
     * 5. 遇到含 nonce 的账户交易块：
     *    - 检查其后所有相同 address 的块中是否有 nonce 更小或相等但时间更早的
     *    - 插入这些块到当前块前
     * 6. 将所有处理好的交易块拼接在链接块之后
     * 7. 取前 num 个返回
     * @param num
     * @param sendtime
     * @return
     */
    public List<Address> getOrphan(long num, long[] sendtime) {
        List<Address> result = Lists.newArrayList();
        if (orphanSource.get(ORPHAN_SIZE) == null || getOrphanSize() == 0) {
            return null;
        }

        long addNum = Math.min(getOrphanSize(), num);
        List<Pair<byte[], byte[]>> raw = orphanSource.prefixKeyAndValueLookup(BytesUtils.of(ORPHAN_PREFEX));
        List<OrphanMeta> linkBlocks = new ArrayList<>();
        List<OrphanMeta> txBlocks = new ArrayList<>();

        for (Pair<byte[], byte[]> pair : raw) {
            OrphanMeta meta = OrphanMeta.parse(pair);
            if (meta.time > sendtime[0]) {
                continue;
            }

            if (!meta.isTx) {
                linkBlocks.add(meta);
            } else {
                txBlocks.add(meta);
            }
        }

        // 链接块：按时间升序
        linkBlocks.sort(Comparator.comparingLong(m -> m.time));
        // 交易块：按 fee 降序、time 升序
        txBlocks.sort(Comparator.<OrphanMeta>comparingLong(m -> -m.fee).thenComparingLong(m -> m.time));

        // 标记已插入的交易块，避免重复处理
        Set<OrphanMeta> handled = new HashSet<>();
        List<OrphanMeta> fixedTxBlocks = new ArrayList<>();

        // 核心逻辑：插入账户交易块的前置 nonce 块
        for (int i = 0; i < txBlocks.size(); i++) {
            OrphanMeta current = txBlocks.get(i);
            if (handled.contains(current)) {
                continue;
            }

            // 若是非账户交易块，直接添加，无需插入前置
            if (current.nonce == 0 && BytesUtils.isFullZero(current.address)) {
                fixedTxBlocks.add(current);
                handled.add(current);
                continue;
            }

            List<OrphanMeta> inserts = new ArrayList<>();

            for (int j = i + 1; j < txBlocks.size(); j++) {
                OrphanMeta later = txBlocks.get(j);
                if (handled.contains(later)) {
                    continue;
                }
                if (!Arrays.equals(current.address, later.address)) {
                    continue;
                }

                if (later.nonce < current.nonce
                        || (later.nonce == current.nonce && later.time < current.time && later.fee < current.fee)) {
                    inserts.add(later);
                    handled.add(later);
                }
            }

            // 插入顺序：nonce 升序、相同 nonce 按时间升序
            inserts.sort(Comparator.<OrphanMeta>comparingLong(m -> m.nonce).thenComparingLong(m -> m.time));
            fixedTxBlocks.addAll(inserts);
            fixedTxBlocks.add(current);
            handled.add(current);
        }

        // 补全未被处理的交易块 兜底，保险
        for (OrphanMeta m : txBlocks) {
            if (!handled.contains(m)) {
                fixedTxBlocks.add(m);
            }
        }

        // 拼接最终顺序：链接块 + 处理后的交易块
        List<OrphanMeta> finalSorted = new ArrayList<>();
        finalSorted.addAll(linkBlocks);
        finalSorted.addAll(fixedTxBlocks);

        // 取前 num 个，构造 Address 列表并更新 sendtime[1]
        for (int i = 0; i < finalSorted.size() && addNum > 0; i++) {
            OrphanMeta m = finalSorted.get(i);
            result.add(new Address(m.hashlow, XdagField.FieldType.XDAG_FIELD_OUT, false));
            sendtime[1] = Math.max(sendtime[1], m.time);
            addNum--;
        }
//        List<Address> res = Lists.newArrayList();
//        if (orphanSource.get(ORPHAN_SIZE) == null || getOrphanSize() == 0) {
//            return null;
//        } else {
//            long orphanSize = getOrphanSize();
//            long addNum = Math.min(orphanSize, num);
//            byte[] key = BytesUtils.of(ORPHAN_PREFEX);
//            List<Pair<byte[],byte[]>> ans = orphanSource.prefixKeyAndValueLookup(key);
//            ans.sort(Comparator.comparingLong(a -> BytesUtils.bytesToLong(a.getValue(), 0, true)));
//            for (Pair<byte[],byte[]> an : ans) {
//                if (addNum == 0) {
//                    break;
//                }
//                // TODO:判断时间，这里出现过orphanSource获取key时为空的情况
//                if (an.getValue() == null) {
//                    continue;
//                }
//                long time =  BytesUtils.bytesToLong(an.getValue(), 0, true);
//                if (time <= sendtime[0]) {
//                    addNum--;
//                    res.add(new Address(Bytes32.wrap(an.getKey(), 1), XdagField.FieldType.XDAG_FIELD_OUT,false));
//                    sendtime[1] = Math.max(sendtime[1],time);
////                    Bytes32 blockHashLow = Bytes32.wrap(an.getKey(),1);
////                    if(filter.filterOurLinkBlock(blockHashLow)){
////                        addNum--;
////                        //TODO:通过address 获取区块 遍历连接块是否都是output如果是 则为链接块 判断是否是自己的是才链接
////                        res.add(new Address(blockHashLow, XdagField.FieldType.XDAG_FIELD_OUT,false));
////                        sendtime[1] = Math.max(sendtime[1],time);
////                    }
//                }
//            }
            sendtime[1] = Math.min(sendtime[1]+1,sendtime[0]);
            return result;
        }

    public void deleteByKey(byte[] hashlow, boolean isTxBlock , UInt64 nonce, XAmount fee, byte[] address) {
        log.debug("deleteByKey");
        byte[] hashL = Arrays.copyOfRange(hashlow, 8, 32);
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8);
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false);
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashL, nonceBytes, isTx));
        orphanSource.delete(key);
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
    }

    public void addOrphan(Block block, boolean isTxBlock , UInt64 nonce, XAmount fee, byte[] address) {
        // key: 0x00 + hashlow(24B) + nonce(8B) + isTx(1B)
        byte[] hashlow = Arrays.copyOfRange(block.getHashLow().toArray(), 8, 32); // 提取有效 24B
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8); // 使用 UInt64 overload 方法
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false); // 1B
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashlow, nonceBytes, isTx));
//        System.out.println("OrphanKey: " + Arrays.toString(key));
        // value: time(8B) + fee(8B) + address(20B)，若非账户交易块则 address 全 0
        byte[] timeBytes = BytesUtils.longToBytes(block.getTimestamp(), true);
        byte[] feeBytes = fee.toXAmount().toBytes().toArray(); // XAmount -> long -> 8B
        byte[] addrBytes = (address == null) ? new byte[20] : address;
        byte[] value = BytesUtils.merge(timeBytes, feeBytes, addrBytes);
//        System.out.println("OrphanValue: " + Arrays.toString(value));

        orphanSource.put(key, value);

        long currentSize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentSize + 1, false));
        log.debug("orphan current size:{}", currentSize);
//        orphanSource.put(BytesUtils.merge(ORPHAN_PREFEX, block.getHashLow().toArray()),
//                BytesUtils.longToBytes(block.getTimestamp(), true));
//        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
//        log.debug("orphan current size:{}", currentsize);
////        log.debug(":" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
//        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
    }

    public long getOrphanSize() {
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        log.debug("current orphan size:{}", currentsize);
        log.debug("Hex:{}", Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
        return currentsize;
    }

    @Getter
    public static class OrphanMeta {
        private Bytes32 hashlow;
        private long nonce;
        private boolean isTx;
        private long time;
        private long fee;
        private byte[] address; // 20B

        public static OrphanMeta parse(Pair<byte[], byte[]> pair) {
            byte[] key = pair.getKey();
            byte[] value = pair.getValue();

            OrphanMeta m = new OrphanMeta();

            // key 解析：0x00(1B) + hashlow(24B) + nonce(8B) + isTx(1B)
            byte[] fullHash = new byte[32];
            System.arraycopy(key, 1, fullHash, 8, 24);
            m.hashlow = Bytes32.wrap(fullHash);
            m.nonce = BytesUtils.bytesToLong(key, 25, false);
            m.isTx = BytesUtils.toByte(BytesUtils.subArray(key, 33, 1)) == 1;

            // value 解析：time(8B) + fee(8B) + address(20B)
            m.time = BytesUtils.bytesToLong(value, 0, true);
            m.fee = BytesUtils.bytesToLong(value, 8, true);
            m.address = BytesUtils.subArray(value, 16, 20);

            return m;
        }
    }

}
