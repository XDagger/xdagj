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

import java.math.BigInteger;
import java.util.Objects;

import io.xdag.evm.DataWord;
import io.xdag.evm.client.BlockStore;
import io.xdag.evm.client.Repository;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class ProgramInvokeImpl implements ProgramInvoke {

    /**
     * Transaction environment
     */
    private final DataWord address, origin, caller, gasPrice, value;
    private final Bytes data;
    private final long gasLimit;

    /**
     * Block environment
     */
    private final DataWord blockPrevHash, blockCoinbase, blockTimestamp, blockNumber, blockDifficulty, blockGasLimit;

    /**
     * Database environment
     */
    private final Repository repository;
    private final Repository originalRepository;
    private final BlockStore blockStore;

    private final int callDepth;
    private final boolean isStaticCall;

    public ProgramInvokeImpl(DataWord address, DataWord origin, DataWord caller,
                             long gas, DataWord gasPrice, DataWord value, Bytes data, DataWord blockPrevHash,
                             DataWord blockCoinbase, DataWord blockTimestamp, DataWord blockNumber, DataWord blockDifficulty,
                             DataWord gasLimit, Repository repository, Repository originalRepository, BlockStore blockStore,
                             int callDepth, boolean isStaticCall) {

        Objects.requireNonNull(address);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(caller);
        Objects.requireNonNull(gasPrice);
        Objects.requireNonNull(value);
        Objects.requireNonNull(data);

        Objects.requireNonNull(blockPrevHash);
        Objects.requireNonNull(blockCoinbase);
        Objects.requireNonNull(blockTimestamp);
        Objects.requireNonNull(blockNumber);
        Objects.requireNonNull(blockDifficulty);
        Objects.requireNonNull(gasLimit);

        Objects.requireNonNull(repository);
        Objects.requireNonNull(blockStore);

        this.address = address;
        this.origin = origin;
        this.caller = caller;
        this.gasLimit = gas;
        this.gasPrice = gasPrice;
        this.value = value;
        this.data = data;

        this.blockPrevHash = blockPrevHash;
        this.blockCoinbase = blockCoinbase;
        this.blockTimestamp = blockTimestamp;
        this.blockNumber = blockNumber;
        this.blockDifficulty = blockDifficulty;
        this.blockGasLimit = gasLimit;

        this.repository = repository;
        this.originalRepository = originalRepository;
        this.blockStore = blockStore;

        this.callDepth = callDepth;
        this.isStaticCall = isStaticCall;
    }

    @Override
    public DataWord getOwnerAddress() {
        return address;
    }

    @Override
    public DataWord getOriginAddress() {
        return origin;
    }

    @Override
    public DataWord getCallerAddress() {
        return caller;
    }

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public DataWord getGasPrice() {
        return gasPrice;
    }

    @Override
    public DataWord getValue() {
        return value;
    }

    // open for testing
    public Bytes getData() {
        return data;
    }

    @Override
    public DataWord getDataValue(DataWord indexData) {
        Bytes data = getData();

        BigInteger indexBI = indexData.value();
        if (indexBI.compareTo(BigInteger.valueOf(data.size())) >= 0) {
            return DataWord.ZERO;
        }

        int idx = indexBI.intValue();
        int size = Math.min(data.size() - idx, DataWord.SIZE);

//        byte[] buffer = new byte[DataWord.SIZE];
        MutableBytes buffer = MutableBytes.create(DataWord.SIZE);
//        System.arraycopy(data, idx, buffer, 0, size); // left-aligned
        buffer.set(0, data.slice(idx, size));
        return DataWord.of(buffer);
    }

    @Override
    public DataWord getDataSize() {
        Bytes data = getData();

        return DataWord.of(data.size());
    }

    @Override
    public Bytes getDataCopy(DataWord offsetData, DataWord lengthData) {
        Bytes data = getData();

        BigInteger offsetBI = offsetData.value();
        BigInteger lengthBI = lengthData.value();

        if (offsetBI.compareTo(BigInteger.valueOf(data.size())) >= 0) {
//            return new byte[0];
            return Bytes.EMPTY;
        }

        int offset = offsetBI.intValue();
        int size = data.size() - offset;
        if (lengthBI.compareTo(BigInteger.valueOf(size)) < 0) {
            size = lengthBI.intValue();
        }

//        byte[] buffer = new byte[size];
        MutableBytes buffer = MutableBytes.create(size);
//        System.arraycopy(data, offset, buffer, 0, size);
        buffer.set(0, data.slice(offset, size));

        return buffer;
    }

    @Override
    public DataWord getBlockPrevHash() {
        return blockPrevHash;
    }

    @Override
    public DataWord getBlockCoinbase() {
        return blockCoinbase;
    }

    @Override
    public DataWord getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public DataWord getBlockNumber() {
        return blockNumber;
    }

    @Override
    public DataWord getBlockDifficulty() {
        return blockDifficulty;
    }

    @Override
    public DataWord getBlockGasLimit() {
        return blockGasLimit;
    }

    @Override
    public Repository getRepository() {
        return repository;
    }

    @Override
    public Repository getOriginalRepository() {
        return originalRepository;
    }

    @Override
    public BlockStore getBlockStore() {
        return blockStore;
    }

    @Override
    public int getCallDepth() {
        return this.callDepth;
    }

    @Override
    public boolean isStaticCall() {
        return isStaticCall;
    }

    @Override
    public String toString() {
        return "ProgramInvokeImpl{" +
                "address=" + address +
                ", origin=" + origin +
                ", caller=" + caller +
                ", gasLimit=" + gasLimit +
                ", gasPrice=" + gasPrice +
                ", value=" + value +
                ", data=" + data.toHexString() +
                ", blockPrevHash=" + blockPrevHash +
                ", blockCoinbase=" + blockCoinbase +
                ", blockTimestamp=" + blockTimestamp +
                ", blockNumber=" + blockNumber +
                ", blockDifficulty=" + blockDifficulty +
                ", blockGasLimit=" + blockGasLimit +
                ", repository=" + repository +
                ", blockStore=" + blockStore +
                ", callDepth=" + callDepth +
                ", isStaticCall=" + isStaticCall +
                '}';
    }
}
