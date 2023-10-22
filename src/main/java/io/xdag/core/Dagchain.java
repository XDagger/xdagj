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
package io.xdag.core;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;

public interface Dagchain {

    /**
     * Returns the latest main block.
     */
    MainBlock getLatestMainBlock();

    /**
     * Returns the hash of the latest main block.
     */
    byte[] getLatestMainBlockHash();

    /**
     * Returns the number of the latest block.
     */
    long getLatestMainBlockNumber();

    /**
     * Returns block number by hash.
     */
    long getMainBlockNumber(byte[] hash);

    /**
     * Returns genesis block.
     */
    Genesis getGenesis();

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

    /**
     * Returns transaction by its hash.
     */
    Transaction getTransaction(byte[] hash);

    /**
     * Returns coinbase transaction of the block number. This method is required as
     * xdag doesn't store coinbase transaction in blocks.
     */
    Transaction getCoinbaseTransaction(long blockNumber);

    /**
     * Returns whether the transaction is in the blockchain.
     */
    boolean hasTransaction(byte[] hash);

    /**
     * Returns transaction result.
     */
    TransactionResult getTransactionResult(byte[] hash);

    /**
     * Returns the block number of the given transaction.
     */
    long getTransactionBlockNumber(byte[] hash);

    /**
     * Returns the total number of transactions from/to the given address.
     */
    int getTransactionCount(byte[] address);

    /**
     * Returns transactions from/to an address.
     *
     * @param address
     *            account address
     * @param from
     *            transaction index from
     * @param to
     *            transaction index to
     */
    List<Transaction> getTransactions(byte[] address, int from, int to);

    void addListener(DagchainListener listener);

    /**
     * Add a block to the chain.
     */
    void addMainBlock(MainBlock block, BlockState bsTrack);

    /**
     * Returns account state.
     */
    AccountState getAccountState(byte[] hash, long snapshotNumber);

    /**
     * Returns Latest account state.
     */
    AccountState getLatestAccountState();

    /**
     * Returns Block state.
     */
    BlockState getBlockState(byte[] hash, long snapshotNumber);

    /**
     * Returns Latest block state.
     */
    BlockState getLatestBlockState();

    /**
     * Returns whether a fork has been activated.
     */
    boolean isForkActivated(Fork fork, long number);

    /**
     * Returns whether a fork has been activated.
     */
    boolean isForkActivated(Fork fork);

    /**
     * Returns the date field for the next block, based on fork configuration.
     */
    byte[] constructBlockHeaderDataField();

    /**
     * Returns the state lock.
     */
    ReentrantReadWriteLock getStateLock();

    /**
     * Imports a new main block.
     *
     * @param mainblock
     *            the block to import
     * @param accountState
     *            the snapshot of AccountState
     * @param blockState
     *            the snapshot of BlockState
     * @return true if the block is successfully imported; otherwise, false
     */
    boolean importBlock(MainBlock mainblock, AccountState accountState, BlockState blockState);

}
