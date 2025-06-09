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

import com.google.common.primitives.UnsignedBytes;
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
import org.apache.tuweni.bytes.Bytes;
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
     * Retrieve a sorted list of orphan block addresses based on a structured sorting and nonce-consistency strategy.
     *
     * Sorting Strategy:
     * 1. All orphan entries are parsed from the underlying key-value storage (prefix = ORPHAN_PREFIX).
     * 2. Entries are classified as:
     *    - Link blocks (non-transaction): isTx == false
     *    - Transaction blocks: isTx == true
     *
     * 3. Link blocks are sorted by:
     *    - Ascending timestamp (time)
     *    - Lexicographical order of hashlow (to break ties deterministically)
     *
     * 4. Transaction blocks are first globally sorted by:
     *    - Descending fee
     *    - Ascending timestamp (for equal fees)
     *    - Ascending hashlow (for completely identical fee and time)
     *
     * 5. Then, to satisfy nonce execution order constraints (i.e., tx[n] cannot be executed before tx[n-1]):
     *    - For each transaction block with a valid account (address + nonce),
     *      we collect all same-account transactions that appear after or at the same index (including itself).
     *    - This group is sorted by:
     *        - Ascending nonce
     *        - Ascending time
     *        - Ascending hashlow (to resolve ties deterministically)
     *    - The group is inserted one-by-one into the output list `fixedTxBlocks`, ensuring:
     *        - The first transaction is inserted based on fee priority in the full list.
     *        - Each subsequent transaction in the group only compares from the insertion point of the previous one onwards,
     *          ensuring nonce order is preserved and higher-nonce txs never appear before lower-nonce ones.
     *
     * 6. Any unprocessed transaction blocks are inserted into the output list using standard fee-priority rules.
     *
     * 7. Final result list is constructed as:
     *    - All sorted link blocks
     *    - All nonce-aware ordered transaction blocks
     *    - Up to `num` entries are selected, updating `sendtime[1]` with the latest timestamp seen.
     *
     * @param num       Maximum number of orphan block addresses to return
     * @param sendtime  A two-element array: [0] is the cutoff time, [1] will be updated with latest used time
     * @return          List of sorted and filtered orphan block addresses
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

        // 链接块：按时间升序、以及区块hash降序
        linkBlocks.sort(Comparator.<OrphanMeta>comparingLong(m -> m.time)
                  .thenComparing(m -> m.getHashlow().toArray(), UnsignedBytes.lexicographicalComparator()));
        // 交易块：按 fee 降序、time 升序、以及区块hash降序
        txBlocks.sort(Comparator.<OrphanMeta>comparingLong(m -> -m.fee)
                .thenComparingLong(m -> m.time)
                .thenComparing(m -> m.getHashlow().toArray(), UnsignedBytes.lexicographicalComparator()));


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
                insertInOrder(fixedTxBlocks, current, 0);
                handled.add(current);
                continue;
            }

            List<OrphanMeta> sameAccountTxs = new ArrayList<>();
            sameAccountTxs.add(current);
            handled.add(current);

            for (int j = i + 1; j < txBlocks.size(); j++) {
                OrphanMeta candidate = txBlocks.get(j);
                if (handled.contains(candidate)) {
                    continue;
                }
                if (!Arrays.equals(current.address, candidate.address)) {
                    continue;
                }

                sameAccountTxs.add(candidate);
                handled.add(candidate);
            }

            sameAccountTxs.sort(Comparator.<OrphanMeta>comparingLong(m -> m.nonce)
                    .thenComparingLong(m -> m.time)
                    .thenComparing(m -> m.getHashlow().toArray(), UnsignedBytes.lexicographicalComparator()));


            int insertFrom = 0;
            for (OrphanMeta tx : sameAccountTxs) {
                insertFrom = insertInOrder(fixedTxBlocks, tx, insertFrom);
                insertFrom++;
            }

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

        sendtime[1] = Math.min(sendtime[1]+1,sendtime[0]);
        return result;
    }

    private int insertInOrder(List<OrphanMeta> list, OrphanMeta tx, int startIndex) {
        int index = startIndex;
        while (index < list.size()) {
            OrphanMeta existing = list.get(index);
            int cmp = Comparator.<OrphanMeta>comparingLong(m -> -m.fee)
                    .thenComparingLong(m -> m.time)
                    .thenComparing(m -> m.getHashlow().toArray(), UnsignedBytes.lexicographicalComparator())
                    .compare(tx, existing);
            if (cmp < 0) break;
            index++;
        }
        list.add(index, tx);
        return index;
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
//        byte[] feeBytes = fee.toXAmount().toBytes().toArray(); // XAmount -> long -> 8B
        byte[] feeBytes = Bytes.wrap(BytesUtils.bigIntegerToBytes(fee.toXAmount(),8)).toArray();
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
//            m.nonce = BytesUtils.bytesToLong(key, 25, false);
            m.nonce = UInt64.fromBytes(Bytes.wrap(key).slice(25, 8)).toLong();
            m.isTx = BytesUtils.toByte(BytesUtils.subArray(key, 33, 1)) == 1;

            // value 解析：time(8B) + fee(8B) + address(20B)
            m.time = BytesUtils.bytesToLong(value, 0, true);
//            m.fee = BytesUtils.bytesToLong(value, 8, true);//UInt64.fromBytes(value.slice(8, 8)).toLong()
            m.fee = UInt64.fromBytes(Bytes.wrap(value).slice(8, 8)).toLong();//UInt64.fromBytes(Bytes.wrap(BytesUtils.bigIntegerToBytes(fee.toXAmount(),8))).toLong()
            m.address = BytesUtils.subArray(value, 16, 20);

            return m;
        }
    }

}
