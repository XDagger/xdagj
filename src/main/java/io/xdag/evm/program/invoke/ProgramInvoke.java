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
package io.xdag.evm.program.invoke;

import io.xdag.evm.DataWord;
import io.xdag.evm.client.BlockStore;
import io.xdag.evm.client.Repository;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a program invoke.
 */
public interface ProgramInvoke {

    // ===========================
    // Transaction context
    // ===========================

    /**
     * Returns the address of currently executing account.
     *
     * @return an address, right-aligned
     */
    DataWord getOwnerAddress();

    /**
     * Returns the execution origination address.
     *
     * @return an address, right-aligned
     */
    DataWord getOriginAddress();

    /**
     * Returns the caller address.
     *
     * @return an address, right-aligned
     */
    DataWord getCallerAddress();

    /**
     * Returns the gas limit for the invocation.
     *
     * @return gas limit
     */
    long getGasLimit();

    /**
     * Returns the gas price.
     *
     * @return the gas price in {@link io.xdag.evm.client.Unit#WEI} per gas
     */
    DataWord getGasPrice();

    /**
     * Returns the deposited value by the instruction/transaction responsible for
     * this execution.
     *
     * @return the call value in {@link io.xdag.evm.client.Unit#WEI}
     */
    DataWord getValue();

    /**
     * Returns the size of call data.
     */
    DataWord getDataSize();

    /**
     * Returns the data at the given offset.
     *
     * @param offset
     *            an offset
     * @return a word starting from the offset; zeros are padded if out of range.
     */
    DataWord getDataValue(DataWord offset);

    /**
     * Returns the given number of bytes, starting from an offset.
     *
     * @param offset
     *            the starting offset
     * @param length
     *            the number of bytes to copy
     * @return a byte array copied from the call data; zeros are padded if out of
     *         range.
     */
    Bytes getDataCopy(DataWord offset, DataWord length);

    // ===========================
    // Block context
    // ===========================

    /**
     * Returns the hash of the previous block.
     *
     * @return a block hash.
     */
    DataWord getBlockPrevHash();

    /**
     * Returns the miner address of this block.
     *
     * @return an address, right-aligned.
     */
    DataWord getBlockCoinbase();

    /**
     * Returns the timestamp of this block.
     *
     * @return the timestamp.
     */
    DataWord getBlockTimestamp();

    /**
     * Returns the number of this block.
     *
     * @return the block number
     */
    DataWord getBlockNumber();

    /**
     * Returns the difficulty of this block.
     *
     * @return the block difficulty
     */
    DataWord getBlockDifficulty();

    /**
     * Returns the gas limit of this block.
     *
     * @return the block gas limit
     */
    DataWord getBlockGasLimit();

    // ===========================
    // Database context
    // ===========================

    /**
     * Returns the repository interface.
     *
     * @return repository implementation
     */
    Repository getRepository();

    /**
     * Returns the original repository.
     */
    Repository getOriginalRepository();

    /**
     * Returns the block storage interface.
     *
     * @return block store implementation
     */
    BlockStore getBlockStore();

    // ===========================
    // Miscellaneous
    // ===========================

    /**
     * Returns the current call depth.
     *
     * @return the call depth.
     */
    int getCallDepth();

    /**
     * Returns whether this invocation is a static call.
     *
     * @return true if it's a static call; otherwise false.
     */
    boolean isStaticCall();
}
