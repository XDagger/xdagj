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
package io.xdag.evm.program;

import java.math.BigInteger;

import io.xdag.evm.client.Transaction;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents an internal transaction.
 */
public class InternalTransaction implements Transaction {

    private boolean rejected = false;

    private int depth;
    private int index;
    private String type;

    private Bytes from;
    private Bytes to;
    private long nonce;
    private BigInteger value;
    private Bytes data;
    private long gas;
    private BigInteger gasPrice;

    public InternalTransaction(int depth, int index, String type,
                               Bytes from, Bytes to, long nonce, BigInteger value, Bytes data,
                               long gas, BigInteger gasPrice) {
        this.depth = depth;
        this.index = index;
        this.type = type;

        this.from = from;
        this.to = to;
        this.nonce = nonce;
        this.value = value;
        this.data = data;
        this.gas = gas;
        this.gasPrice = gasPrice;
    }

    public void reject() {
        this.rejected = true;
    }

    public boolean isRejected() {
        return rejected;
    }

    public int getDepth() {
        return depth;
    }

    public int getIndex() {
        return index;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean isCreate() {
        // CREATE and CREATE2
        return type.startsWith("CREATE");
    }

    @Override
    public Bytes getFrom() {
        return from;
    }

    @Override
    public Bytes getTo() {
        return to;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public BigInteger getValue() {
        return value;
    }

    @Override
    public Bytes getData() {
        return data;
    }

    @Override
    public long getGas() {
        return gas;
    }

    @Override
    public BigInteger getGasPrice() {
        return gasPrice;
    }

    @Override
    public String toString() {
        return "InternalTransaction{" +
                "rejected=" + rejected +
                ", depth=" + depth +
                ", index=" + index +
                ", type=" + type +
                ", from=" + from.toHexString() +
                ", to=" + to.toHexString() +
                ", nonce=" + nonce +
                ", value=" + value +
                ", data=" + data.toHexString() +
                ", gas=" + gas +
                ", gasPrice=" + gasPrice +
                '}';
    }
}
