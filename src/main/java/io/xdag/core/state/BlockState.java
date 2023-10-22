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
package io.xdag.core.state;

import java.util.List;
import java.util.Map;

import io.xdag.core.BlockHeader;
import io.xdag.core.MainBlock;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;

public interface BlockState {

    /** Database */
    int getDatabaseVersion();

    void putDatabaseVersion(int version);

    long getLatestMainBlockNumber();

    /** Blocks */

    void addMainBlock(MainBlock mainBlock);

    MainBlock getBlock(long number, boolean skipResults);

    /**
     * Returns main block by number.
     */
    MainBlock getMainBlockByNumber(long number);

    /**
     * Returns main block by its hash.
     */
    MainBlock getMainBlockByHash(byte[] hash);

    /**
     * Returns block header by block number.
     */
    BlockHeader getBlockHeader(long number);

    /**
     * Returns block header by block hash.
     */
    BlockHeader getBlockHeader(byte[] hash);

    /**
     * Returns whether the block is existing.
     */
    boolean hasMainBlock(long number);

    long getMainBlockNumber(byte[] hash);

    /** Transactions */
    Transaction getCoinbaseTransaction(long number);

    boolean hasTransaction(final byte[] hash);

    Transaction getTransaction(byte[] hash);

    int getTransactionCount(byte[] address);

    void setTransactionCount(byte[] address, int total);

    void addTransactionToAccount(Transaction tx, byte[] address);

    void addTransactionIndex(byte[] hash, byte[] bytes);

    void addBlockCoinbaseByNumber(long number, byte[] hash);

    List<Transaction> getTransactions(byte[] address, int from, int to);

    TransactionResult getTransactionResult(byte[] hash);

    long getTransactionBlockNumber(byte[] hash);

    void addLatestBlockNumber(long number);

    void addActivatedForks(byte[] bytes);

    byte[] getActivatedForks();

    void commit();

    Map<ByteArray, byte[]> getBlockUpdates();
    Map<ByteArray, byte[]> getIndexUpdates();

    /**
     * Remove updates form a BlockState.
     */
    void removeUpdates(BlockState otherBs);

    void rollback();

    /**
     * Clone this BlockState, including all the uncommitted changes.
     */
    BlockState clone();

    class TransactionIndex {
        long blockNumber;
        int transactionOffset;
        int resultOffset;

        public TransactionIndex(long blockNumber, int transactionOffset, int resultOffset) {
            this.blockNumber = blockNumber;
            this.transactionOffset = transactionOffset;
            this.resultOffset = resultOffset;
        }

        public byte[] toBytes() {
            SimpleEncoder enc = new SimpleEncoder();
            enc.writeLong(blockNumber);
            enc.writeInt(transactionOffset);
            enc.writeInt(resultOffset);
            return enc.toBytes();
        }

        public static TransactionIndex fromBytes(byte[] bytes) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            long number = dec.readLong();
            int transactionOffset = dec.readInt();
            int resultOffset = dec.readInt();
            return new TransactionIndex(number, transactionOffset, resultOffset);
        }
    }
}
