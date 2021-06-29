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
package io.xdag.evm.client;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.math.BigInteger;

public class BlockMock implements Block {

    private long number;
    private Bytes32 parentHash;
    private Bytes coinbase;
    private long timestamp;
    private long gasLimit;

    public BlockMock(long number, Bytes32 parentHash, Bytes coinbase, long timestamp, long gasLimit) {
        this.gasLimit = gasLimit;
        this.parentHash = parentHash;
        this.coinbase = coinbase;
        this.timestamp = timestamp;
        this.number = number;
    }

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public Bytes32 getParentHash() {
        return parentHash;
    }

    @Override
    public Bytes getCoinbase() {
        return coinbase;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public long getNumber() {
        return number;
    }

    @Override
    public BigInteger getDifficulty() {
        return BigInteger.ONE;
    }
}

