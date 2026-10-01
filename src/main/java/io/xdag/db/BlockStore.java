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
package io.xdag.db;

import io.xdag.core.XdagLifecycle;
import io.xdag.core.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

import java.util.List;
import java.util.function.Function;

public interface BlockStore extends XdagLifecycle {

    byte SETTING_STATS = (byte) 0x10;
    byte TIME_HASH_INFO = (byte) 0x20;
    byte HASH_BLOCK_INFO = (byte) 0x30;
    byte SUMS_BLOCK_INFO = (byte) 0x40;
    byte OURS_BLOCK_INFO = (byte) 0x50;
    byte SETTING_TOP_STATUS = (byte) 0x60;
    byte SNAPSHOT_BOOT = (byte) 0x70;
    byte BLOCK_HEIGHT = (byte) 0x80;
    byte SNAPSHOT_PRESEED = (byte) 0x90;
    byte TX_HISTORY = (byte) 0xa0;
    byte OPEN_NET_FORK_LATCH = (byte) 0xb0;
    byte MAIN_UPDATE_MARK = (byte) 0xc0;
    byte TX_SKIPPED_BY = (byte) 0xd0;
    String SUM_FILE_NAME = "sums.dat";

    void reset();

    XdagStats getXdagStatus();

    void saveXdagTopStatus(XdagTopStatus status);

    XdagTopStatus getXdagTopStatus();

    void saveBlock(Block block);

    void saveBlockInfo(BlockInfo blockInfo);

    void saveOurBlock(int index, byte[] hashlow);

    void saveTxHistoryToRocksdb(TxHistory txHistory,int id);

    List<TxHistory> getAllTxHistoryFromRocksdb();

    void deleteAllTxHistoryFromRocksdb();

    boolean hasBlock(Bytes32 hashlow);

    boolean hasBlockInfo(Bytes32 hashlow);

    List<Block> getBlocksUsedTime(long startTime, long endTime);

    List<Block> getBlocksByTime(long startTime);

    Block getBlockByHeight(long height);

    Block getBlockByHash(Bytes32 hashlow, boolean isRaw);

    Block getBlockInfoByHash(Bytes32 hashlow);

    BlockInfo getBlockInfo(Bytes32 hashlow);

    Block getRawBlockByHash(Bytes32 hashlow);

    Bytes getOurBlock(int index);

    int getKeyIndexByHash(Bytes32 hashlow);

    void removeOurBlock(byte[] hashlow);

    void fetchOurBlocks(Function<Pair<Integer, Block>, Boolean> function);

    // Snapshot Boot
    boolean isSnapshotBoot();

    void setSnapshotBoot();

    // RandomX seed
    void savePreSeed(byte[] preseed);

    byte[] getPreSeed();

    // sums.dat and sum.dat
    void saveBlockSums(Block block);

    MutableBytes getSums(String key);

    void putSums(String key, Bytes sums);

    void updateSum(String key, long sum, long size, long index);

    int loadSum(long starttime, long endtime, MutableBytes sums);

    void saveXdagStatus(XdagStats status);

    /**
     * Whether this node's main chain has ever contained a main block at or after the open-network hardening
     * fork epoch. Once set the mark is never cleared (see BlockchainImpl#isOpenNetLatched).
     */
    boolean isOpenNetForkLatched();

    void setOpenNetForkLatched();

    /**
     * Mark that a main block is being confirmed or unwound. The many writes this takes are not one atomic
     * transaction, so a process that dies in between leaves balances and flags half updated. The mark makes
     * that detectable on the next start (it survives a killed process; it cannot help against a power failure,
     * where the last writes of several databases may be lost independently).
     */
    void setMainUpdateInProgress(boolean inProgress);

    boolean isMainUpdateInProgress();

    /**
     * Record which block reached a transaction whose nonce did not fit (hardened execution). The transaction is
     * left unexecuted for good, and this record is what lets exactly that block undo the decision when it is
     * unwound. {@code byHashlow == null} removes the record.
     */
    void setTxSkippedBy(Bytes32 txHashlow, Bytes32 byHashlow);

    Bytes32 getTxSkippedBy(Bytes32 txHashlow);

}
