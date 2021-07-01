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

import io.xdag.core.BlockInfo;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.math.BigInteger;

/**
 * Facade for Xdag Block ->  Evm Block
 */
public class XdagEvmBlock implements Block {

    private final long blockGasLimit;
    private final BlockInfo blockInfo;

    public XdagEvmBlock(BlockInfo blockInfo, long blockGasLimit) {
        this.blockInfo = blockInfo;
        this.blockGasLimit = blockGasLimit;
    }

    @Override
    public long getGasLimit() {
        return blockGasLimit;
    }

    @Override
    public Bytes32 getParentHash() {
        return Bytes32.wrap(blockInfo.getMaxDiffLink());
    }

    @Override
    public Bytes getCoinbase() {
        // TODO
        //return blockInfo.getCoinbase();
        return null;
    }

    @Override
    public long getTimestamp() {
        return blockInfo.getTimestamp();
    }

    @Override
    public long getNumber() {
        //TODO
//        return blockInfo.getNumber();
        return 0;
    }

    @Override
    public BigInteger getDifficulty() {
        return BigInteger.ONE;
    }

}
