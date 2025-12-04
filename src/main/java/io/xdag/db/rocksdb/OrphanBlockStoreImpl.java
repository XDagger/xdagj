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
import io.xdag.Kernel;
import io.xdag.core.*;
import io.xdag.db.OrphanBlockStore;
import io.xdag.utils.BytesUtils;

import java.util.*;
import java.util.concurrent.*;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
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

    private final Queue<OrphanMeta> linkQueue = new PriorityBlockingQueue<>(50, Comparator
            .comparingLong((OrphanMeta m) -> m.time)
            .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Queue<OrphanMeta> mtxQueue = new PriorityBlockingQueue<>(100, Comparator
            .comparingLong((OrphanMeta m) -> -m.fee)
            .thenComparingLong(m -> m.time)
            .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Queue<OrphanMeta> accountTxQueue = new PriorityBlockingQueue<>(150, Comparator
            .comparingLong((OrphanMeta m) -> m.time)
            .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Queue<CandidateEntry> candidateQueue = new PriorityBlockingQueue<>(20, Comparator
            .comparingLong((CandidateEntry e) -> -e.meta.fee)
            .thenComparingLong(e -> e.meta.time)
            .thenComparing(e -> e.meta.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Map<Bytes, Long> orphanInsertTimeMap = new ConcurrentHashMap<>();

    private final Map<String, Queue<OrphanMeta>> vipTxMap = new ConcurrentHashMap<>();

    @Getter
    private final Deque<OrphanMeta> mainRef = new ConcurrentLinkedDeque<>();

    private final Map<String, UInt64> accountNonce = new ConcurrentHashMap<>();

    private static final XAmount averageFee = XAmount.of(100, XUnit.MILLI_XDAG);

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    private final Kernel kernel;

    public OrphanBlockStoreImpl(KVSource<byte[], byte[]> orphan, Kernel kernel) {
        this.orphanSource = orphan;
        this.kernel = kernel;
    }

    public void start() {
        this.orphanSource.init();
        if (orphanSource.get(ORPHAN_SIZE) == null) {
            this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
        }
        rebuildMemoryFromDb();
        startCleaner();
    }

    public void rebuildMemoryFromDb() {
        linkQueue.clear();
        mtxQueue.clear();
        accountTxQueue.clear();
        candidateQueue.clear();
        orphanInsertTimeMap.clear();
        vipTxMap.clear();
        mainRef.clear();

        List<Pair<byte[], byte[]>> raw = orphanSource.prefixKeyAndValueLookup(BytesUtils.of(ORPHAN_PREFEX));
        for (Pair<byte[], byte[]> pair : raw) {
            OrphanMeta meta = OrphanMeta.parse(pair);
            addOrphanToMemory(meta, pair.getKey());
        }
        long vipSize = vipTxMap.values().stream().mapToLong(Queue::size).sum();

        log.info("OrphanBlockStore memory queues rebuilt from DB: {} link, {} mtx, {} accounts, {} vipTxMap",
                linkQueue.size(), mtxQueue.size(), accountTxQueue.size(),  vipSize);
    }

    private void startCleaner() {
        cleaner.scheduleAtFixedRate(() -> cleanExpiredOrphans(15 * 60 * 1000L), 5, 300, TimeUnit.SECONDS);
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

    private void cleanExpiredOrphans(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Bytes, Long>> it = orphanInsertTimeMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Bytes, Long> entry = it.next();
            if (now - entry.getValue() > maxAgeMillis) {
                byte[] value = orphanSource.get(entry.getKey().toArrayUnsafe());
                orphanSource.delete(entry.getKey().toArrayUnsafe());
                long currentSize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
                orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentSize - 1, false));
                log.debug("cleanExpiredOrphans orphan current size:{}", currentSize);
                OrphanMeta meta = OrphanMeta.parse(entry.getKey().toArrayUnsafe(), value);
                if (!meta.isTx) {
                    linkQueue.remove(meta);
                } else if (BytesUtils.isFullZero(meta.address)) {
                    mtxQueue.remove(meta);
                } else {
                    String addrKey = Hex.toHexString(meta.address);
                    Queue<OrphanMeta> accountQueue = vipTxMap.get(addrKey);
                    if (accountQueue != null && accountQueue.contains(meta)) {
                        accountQueue.remove(meta);
                        if (accountQueue.isEmpty()) vipTxMap.remove(addrKey);
                    }else {
                        accountTxQueue.remove(meta);
                    }
                }
                it.remove();
                kernel.getBlockchain().getXdagStats().nnoref--;
                kernel.getBlockStore().saveXdagStatus(kernel.getBlockchain().getXdagStats());
                log.debug("Cleaned expired orphan: {}", Hex.toHexString(entry.getKey().toArray()));
            }
        }
    }

    public void deleteByKey(byte[] hashlow, boolean isTxBlock, UInt64 nonce, XAmount fee, byte[] address) {
        log.debug("deleteByKey");
        byte[] hashL = Arrays.copyOfRange(hashlow, 8, 32);
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8);
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false);
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashL, nonceBytes, isTx));

        if (orphanSource.get(key) != null) {
            orphanSource.delete(key);
            orphanInsertTimeMap.remove(Bytes.wrap(key));

            long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
            orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
            log.debug("deleteByKey current orphan size: {}", currentsize);
        }
    }

    public void deleteFromQueue(Block block, boolean isTxBlock, UInt64 nonce, XAmount fee, byte[] address) {

        byte[] hashlow = Arrays.copyOfRange(block.getHashLow().toArray(), 8, 32); // Extract effective 24B
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8);
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false); // 1B
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashlow, nonceBytes, isTx));

        byte[] timeBytes = BytesUtils.longToBytes(block.getTimestamp(), true);
        byte[] feeBytes = Bytes.wrap(BytesUtils.bigIntegerToBytes(fee.toXAmount(), 8)).toArray();
        byte[] addrBytes = (address == null) ? new byte[20] : address;
        byte[] value = BytesUtils.merge(timeBytes, feeBytes, addrBytes);

        OrphanMeta meta = OrphanMeta.parse(key, value);
        mainRef.remove(meta);

        if (!meta.isTx) {
            linkQueue.remove(meta);
        } else if (BytesUtils.isFullZero(meta.address)) {
            mtxQueue.remove(meta);
        } else {
            String addrKey = Hex.toHexString(meta.address);
            Queue<OrphanMeta> accountQueue = vipTxMap.get(addrKey);
            if (accountQueue != null) {
                accountQueue.remove(meta);
                if (accountQueue.isEmpty()) vipTxMap.remove(addrKey);
                return;
            }
            accountTxQueue.remove(meta);
        }
        long vipTxCount = vipTxMap.values().stream().mapToLong(Queue::size).sum();
        log.info("vipTxCount: {}, accountTxQueue.size(): {}, mtxQueue.size(): {}, linkQueue.size(): {}, mainRef.size() :{}",
                vipTxCount, accountTxQueue.size(), mtxQueue.size(), linkQueue.size(), mainRef.size());
    }

    public void addOrphan(Block block, boolean isTxBlock, UInt64 nonce, XAmount fee, byte[] address) {
        // key: 0x00 + hashlow(24B) + nonce(8B) + isTx(1B)
        byte[] hashlow = Arrays.copyOfRange(block.getHashLow().toArray(), 8, 32); // Extract effective 24B
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8);
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false); // 1B
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashlow, nonceBytes, isTx));
//        System.out.println("OrphanKey: " + Arrays.toString(key));
        // value: time(8B) + fee(8B) + address(20B)，Non-account transaction blocks address 全 0
        byte[] timeBytes = BytesUtils.longToBytes(block.getTimestamp(), true);
//        byte[] feeBytes = fee.toXAmount().toBytes().toArray(); // XAmount -> long -> 8B
        byte[] feeBytes = Bytes.wrap(BytesUtils.bigIntegerToBytes(fee.toXAmount(),8)).toArray();
        byte[] addrBytes = (address == null) ? new byte[20] : address;
        byte[] value = BytesUtils.merge(timeBytes, feeBytes, addrBytes);
//        System.out.println("OrphanValue: " + Arrays.toString(value));

        OrphanMeta meta = OrphanMeta.parse(key, value);

        addOrphanToMemory(meta, key);

        if (orphanSource.get(key) == null) {
            orphanSource.put(key, value);

            long currentSize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
            orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentSize + 1, false));
            log.debug("orphan current size:{}", currentSize);
        }
        long vipTxCount = vipTxMap.values().stream().mapToLong(Queue::size).sum();
        log.info("vipTxCount: {}, accountTxQueue.size(): {}, mtxQueue.size(): {}, linkQueue.size(): {}, mainRef.size() :{}",
                vipTxCount, accountTxQueue.size(), mtxQueue.size(), linkQueue.size(), mainRef.size());
    }

    public void addOrphanToMemory(OrphanMeta meta, byte[] dbKey) {
        orphanInsertTimeMap.put(Bytes.wrap(dbKey), System.currentTimeMillis());
        if (!meta.isTx) {
            if (!linkQueue.contains(meta)) {
                linkQueue.offer(meta);
            }
        } else if (BytesUtils.isFullZero(meta.address)) {
            if (!mtxQueue.contains(meta)) {
                mtxQueue.offer(meta);
            }
        } else {
            String addrKey = Hex.toHexString(meta.address);
            UInt64 executedNonceNum = kernel.getAddressStore().getExecutedNonceNum(meta.address);
            if (accountNonce.get(addrKey) != null && executedNonceNum.compareTo(accountNonce.get(addrKey)) < 0) {
                executedNonceNum = accountNonce.get(addrKey);
            }
            UInt64 blockNonce = UInt64.valueOf(meta.nonce);
            if(blockNonce.compareTo(executedNonceNum.add(UInt64.ONE)) == 0 && averageFee.lessThan(XAmount.ofXAmount(meta.fee))){
                log.info("averageFee:{}, meta.fee: {}", averageFee.toDecimal(2, XUnit.XDAG).toPlainString(), XAmount.ofXAmount(meta.fee).toDecimal(2, XUnit.XDAG).toPlainString());
                accountNonce.put(addrKey, blockNonce);
                vipTxMap.computeIfAbsent(addrKey, k -> new PriorityBlockingQueue<>(10, Comparator
                        .comparingLong((OrphanMeta m) -> m.nonce)
                        .thenComparing(m -> m.time)
                        .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator())));
                if (!vipTxMap.get(addrKey).contains(meta)) {
                    vipTxMap.get(addrKey).offer(meta);
                }
            } else {
                if(!accountTxQueue.contains(meta)) accountTxQueue.add(meta);
            }
        }
    }

    public List<Address> getOrphan(long num, long[] sendtime, boolean isMain) {
        List<Address> result = Lists.newArrayList();

        long addNum;
        if (!isMain) {
            addNum = Math.min(getOrphanSize(), num);
        } else {
            addNum = Math.min(getOrphanSize() + mainRef.size(), num);
        }
        List<OrphanMeta> getMeta = selectBlocks(addNum, sendtime[0], isMain);

        for (int i = 0; i < getMeta.size(); i++) {
            OrphanMeta m = getMeta.get(i);
            if (isMain && !mainRef.contains(m)) mainRef.add(m);
            result.add(new Address(m.hashlow, XdagField.FieldType.XDAG_FIELD_OUT, false));
            sendtime[1] = Math.max(sendtime[1], m.time);
        }

        sendtime[1] = Math.min(sendtime[1] + 1, sendtime[0]);
        long vipTxCount = vipTxMap.values().stream().mapToLong(Queue::size).sum();
        log.info("vipTxCount: {}, accountTxQueue.size(): {}, mtxQueue.size(): {}, linkQueue.size(): {}, mainRef.size() :{}", vipTxCount, accountTxQueue.size(), mtxQueue.size(), linkQueue.size(), mainRef.size());
        return result;

    }

    public List<OrphanMeta> selectBlocks(long totalRequired, long cutoffTime, boolean isMain) {
        List<OrphanMeta> result = new ArrayList<>();

        if (!mainRef.isEmpty() && (isMain || mainRef.size() > 18)) {
            Iterator<OrphanMeta> it = mainRef.iterator();
            while (it.hasNext() && result.size() < totalRequired) {
                result.add(it.next());
                if (!isMain) it.remove();
            }
        }
        if (isMain && !vipTxMap.isEmpty()) {
            long remain = 0;
            for (Map.Entry<String, Queue<OrphanMeta>> entry : vipTxMap.entrySet()) {
                if (entry.getValue().peek() != null) candidateQueue.offer(new CandidateEntry(entry.getValue().peek(), entry.getKey()));
            }
            while (!candidateQueue.isEmpty() && result.size() < totalRequired && remain < 6) {
                CandidateEntry chosen = candidateQueue.poll();
                if (chosen == null) continue;
                result.add(chosen.meta);
                remain++;
                orphanInsertTimeMap.remove(Bytes.wrap(getKeyFromMeta(chosen.meta)));
                Queue<OrphanMeta> vipQueue = vipTxMap.get(chosen.accountKey);
                if (vipQueue != null) {
                    vipQueue.poll();
                    if (!vipQueue.isEmpty()) {
                        OrphanMeta next = vipQueue.peek();
                        if (next.getTime() <= cutoffTime) candidateQueue.offer(new CandidateEntry(next, chosen.accountKey));
                    } else {
                        vipTxMap.remove(chosen.accountKey);
                    }
                }
            }
            candidateQueue.clear();
        }

        if ((isMain || (accountTxQueue.size() + mtxQueue.size()) == 0 || linkQueue.size() >= totalRequired) && !linkQueue.isEmpty()) {
            while (!linkQueue.isEmpty() && result.size() < totalRequired) {
                OrphanMeta m = linkQueue.peek();
                if (m.time > cutoffTime) break;
                result.add(m);
                linkQueue.remove(m);
                orphanInsertTimeMap.remove(Bytes.wrap(getKeyFromMeta(m)));
            }
            return result;
        }

        while ((!mtxQueue.isEmpty() || !accountTxQueue.isEmpty()) && result.size() < totalRequired) {
            OrphanMeta mTXnext = mtxQueue.peek();
            if (mTXnext != null && mTXnext.time <= cutoffTime) {
                result.add(mTXnext);
                mtxQueue.remove(mTXnext);
                orphanInsertTimeMap.remove(Bytes.wrap(getKeyFromMeta(mTXnext)));
                continue;
            }
            OrphanMeta accountTxNext = accountTxQueue.peek();
            if (accountTxNext != null && accountTxNext.time <= cutoffTime ){
                result.add(accountTxNext);
                accountTxQueue.remove(accountTxNext);
                orphanInsertTimeMap.remove(Bytes.wrap(getKeyFromMeta(accountTxNext)));
                continue;
            }
            boolean mtxOver = (mTXnext != null && mTXnext.getTime() > cutoffTime);
            boolean accountOver = (accountTxNext != null && accountTxNext.getTime() > cutoffTime);
            if (mtxOver && accountOver) {
                break;
            }
        }

        return result;
    }

    private byte[] getKeyFromMeta(OrphanMeta meta) {
        byte[] hashL = Arrays.copyOfRange(meta.hashlow.toArray(), 8, 32); // Extract effective 24B
        byte[] nonceBytes = BytesUtils.longToBytes(meta.nonce, false);
        byte[] isTx = BytesUtils.byteToBytes((byte) (meta.isTx ? 1 : 0), false);
        return BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashL, nonceBytes, isTx));
    }

    public long getOrphanSize() {
        long vipTxCount = vipTxMap.values().stream().mapToLong(Queue::size).sum();
        return linkQueue.size() + mtxQueue.size() + vipTxCount + accountTxQueue.size();
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class OrphanMeta {
        private Bytes32 hashlow;
        private long nonce;
        private boolean isTx;
        private long time;
        private long fee;
        private byte[] address; // 20B

        public static OrphanMeta parse(Pair<byte[], byte[]> pair) {
            byte[] key = pair.getLeft();
            byte[] val = pair.getRight();

            return parse(key, val);
        }

        public static OrphanMeta parse(byte[] key, byte[] val) {

            byte[] fullhash = new byte[32];
            System.arraycopy(key, 1, fullhash, 8, 24);

            return new OrphanMeta()
                    .setHashlow(Bytes32.wrap(fullhash))
                    .setNonce(BytesUtils.bytesToLong(key, 25, false))
                    .setTx(key[33] == 1)
                    .setTime(BytesUtils.bytesToLong(val, 0, true))
                    .setFee(UInt64.fromBytes(Bytes.wrap(val).slice(8, 8)).toLong())
                    .setAddress(Arrays.copyOfRange(val, 16, 36));
        }

        @Override
        public boolean equals(Object meta) {
            if (meta == null || getClass() != meta.getClass()) return false;
            OrphanMeta m = (OrphanMeta) meta;
            return Arrays.equals(hashlow.toArray(), m.hashlow.toArray());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hashlow.toArray());
        }

    }

    @Getter
    public static class CandidateEntry {
        public enum EntryType { ACCOUNT_TX, MTX }

        public final OrphanMeta meta;
        public final String accountKey;
        public final EntryType type;

        public CandidateEntry(OrphanMeta meta, String accountKey) {
            this.meta = meta;
            this.accountKey = accountKey;
        }
    }

}
