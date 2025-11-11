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
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
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

    private final Queue<OrphanMeta> linkQueue = new PriorityBlockingQueue<>(10, Comparator
            .comparingLong((OrphanMeta m) -> m.time)
            .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Queue<OrphanMeta> mtxQueue = new PriorityBlockingQueue<>(10, Comparator
            .comparingLong((OrphanMeta m) -> -m.fee)
            .thenComparingLong(m -> m.time)
            .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Map<String, Queue<OrphanMeta>> accountTxMap = new ConcurrentHashMap<>();

    private final Queue<CandidateEntry> candidateQueue = new PriorityBlockingQueue<>(10, Comparator
            .comparingLong((CandidateEntry e) -> -e.meta.fee)
            .thenComparingLong(e -> e.meta.time)
            .thenComparing(e -> e.meta.hashlow.toArray(), UnsignedBytes.lexicographicalComparator()));

    private final Map<Bytes, Long> orphanInsertTimeMap = new ConcurrentHashMap<>();

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
        accountTxMap.clear();
        candidateQueue.clear();
        orphanInsertTimeMap.clear();

        List<Pair<byte[], byte[]>> raw = orphanSource.prefixKeyAndValueLookup(BytesUtils.of(ORPHAN_PREFEX));
        for (Pair<byte[], byte[]> pair : raw) {
            OrphanMeta meta = OrphanMeta.parse(pair);
            addOrphanToMemory(meta, pair.getKey());
        }

        log.info("OrphanBlockStore memory queues rebuilt from DB: {} link, {} mtx, {} accounts, {} candidates",
                linkQueue.size(), mtxQueue.size(), accountTxMap.size(), candidateQueue.size());
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
                    Queue<OrphanMeta> accountQueue = accountTxMap.get(addrKey);
                    if(accountQueue != null){
                        accountQueue.remove(meta);
                        if(accountQueue.isEmpty()) accountTxMap.remove(addrKey);
                    }
                }
                it.remove();
                synchronized (kernel.getBlockchain().getXdagStats()) {
                    kernel.getBlockchain().getXdagStats().nnoref--;
                }
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

        boolean isRemove = false;
        byte[] hashlow = Arrays.copyOfRange(block.getHashLow().toArray(), 8, 32); // Extract effective 24B
        byte[] nonceBytes = BytesUtils.bigIntegerToBytes(nonce, 8);
        byte[] isTx = BytesUtils.byteToBytes((byte) (isTxBlock ? 1 : 0), false); // 1B
        byte[] key = BytesUtils.merge(ORPHAN_PREFEX, BytesUtils.merge(hashlow, nonceBytes, isTx));

        byte[] timeBytes = BytesUtils.longToBytes(block.getTimestamp(), true);
        byte[] feeBytes = Bytes.wrap(BytesUtils.bigIntegerToBytes(fee.toXAmount(), 8)).toArray();
        byte[] addrBytes = (address == null) ? new byte[20] : address;
        byte[] value = BytesUtils.merge(timeBytes, feeBytes, addrBytes);

        OrphanMeta meta = OrphanMeta.parse(key, value);

        if (!meta.isTx) {
            linkQueue.remove(meta);
        } else if (BytesUtils.isFullZero(meta.address)) {
            mtxQueue.remove(meta);
        } else {
            String addrKey = Hex.toHexString(meta.address);
            Queue<OrphanMeta> accountQueue = accountTxMap.get(addrKey);
            if(accountQueue != null){
                accountQueue.remove(meta);
                if(accountQueue.isEmpty()) accountTxMap.remove(addrKey);
            }
        }
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

        orphanSource.put(key, value);
        addOrphanToMemory(meta, key);

        if (orphanSource.get(key) == null) {
            orphanSource.put(key, value);

            long currentSize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
            orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentSize + 1, false));
            log.debug("orphan current size:{}", currentSize);
        }
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
            accountTxMap.computeIfAbsent(addrKey, k -> new PriorityBlockingQueue<>(10, Comparator
                    .comparingLong((OrphanMeta m) -> m.nonce)
                    .thenComparingLong(m -> m.time)
                    .thenComparing(m -> m.hashlow.toArray(), UnsignedBytes.lexicographicalComparator())));
            if (!accountTxMap.get(addrKey).contains(meta)) {
                accountTxMap.get(addrKey).offer(meta);
            }
        }
    }

    public List<Address> getOrphan(long num, long[] sendtime) {
        List<Address> result = Lists.newArrayList();

        long addNum = Math.min(getOrphanSize(), num);
        List<OrphanMeta> getMeta = selectBlocks(addNum, sendtime[0]);

        for (int i = 0; i < getMeta.size(); i++) {
            OrphanMeta m = getMeta.get(i);
            result.add(new Address(m.hashlow, XdagField.FieldType.XDAG_FIELD_OUT, false));
            sendtime[1] = Math.max(sendtime[1], m.time);
        }

        sendtime[1] = Math.min(sendtime[1]+1,sendtime[0]);
        return result;

    }

    public List<OrphanMeta> selectBlocks(long totalRequired, long cutoffTime) {
        List<OrphanMeta> result = new ArrayList<>();

        // Step 1: 优先取链接块
        while (!linkQueue.isEmpty() && result.size() < totalRequired) {
            OrphanMeta m = linkQueue.peek();
            if (m.time > cutoffTime) break;

            result.add(linkQueue.poll());
        }

        long remain = totalRequired - result.size();
        if (remain <= 0) return result;

        // Step 2: 调用递进式交易选择逻辑（含多轮补位）
        List<OrphanMeta> selectedTxs = selectTxBlocksRecursively(remain, cutoffTime);
        if (selectedTxs == null) return result;
        result.addAll(selectedTxs);

        return result;
    }

    private List<OrphanMeta> selectTxBlocksRecursively(long remain, long cutoffTime) {
        List<OrphanMeta> result = new ArrayList<>();

        // Clearing the queue every time
        candidateQueue.clear();
        if (!mtxQueue.isEmpty()) {
            candidateQueue.offer(new CandidateEntry(mtxQueue.peek(), null, CandidateEntry.EntryType.MTX));
        }
        if (!accountTxMap.isEmpty()) {
            for (Map.Entry<String, Queue<OrphanMeta>> entry : accountTxMap.entrySet()) {
                candidateQueue.offer(new CandidateEntry(entry.getValue().peek(), entry.getKey(), CandidateEntry.EntryType.ACCOUNT_TX));
            }
        }
        if (candidateQueue.isEmpty()) return null;

        for (int i = 0; i < remain; i++) {

            CandidateEntry chosen = candidateQueue.poll();
            result.add(chosen.meta);

            //todo:This data should be removed from addOrphanToMemory here.
            orphanInsertTimeMap.remove(Bytes.wrap(getKeyFromMeta(chosen.meta)));

            // Mark as "Take the next block from this queue in the next round" and replenish the candidate pool.
            if (chosen.type == CandidateEntry.EntryType.MTX) {
                mtxQueue.poll();
                OrphanMeta next = mtxQueue.peek();
                if (next != null && next.time <= cutoffTime) {
                    candidateQueue.offer(new CandidateEntry(next, null, CandidateEntry.EntryType.MTX));
                }
            } else {
                Queue<OrphanMeta> q = accountTxMap.get(chosen.accountKey);
                if (q != null) {
                    q.poll();
                    if (!q.isEmpty()) {
                        OrphanMeta next = q.peek();
                        if (next.time <= cutoffTime) {
                            candidateQueue.offer(new CandidateEntry(next, chosen.accountKey, CandidateEntry.EntryType.ACCOUNT_TX));
                        }
                    } else {
                        accountTxMap.remove(chosen.accountKey);
                    }
                }
            }
        }

        // 将本轮未使用的候选重新放回池中
        for (int i = (int) canTake; i < thisRound.size(); i++) {
            candidateQueue.offer(thisRound.get(i));
        }

        // 如果没满足要求，再递归下一轮
        long remaining = remain - result.size();
        if (remaining > 0) {
            result.addAll(selectTxBlocksRecursively(remaining, cutoffTime));
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
        long accountTxCount = accountTxMap.values().stream().mapToLong(Queue::size).sum();
        return linkQueue.size() + mtxQueue.size() + accountTxCount;
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

        public CandidateEntry(OrphanMeta meta, String accountKey, EntryType type) {
            this.meta = meta;
            this.accountKey = accountKey;
            this.type = type;
        }
    }

}
