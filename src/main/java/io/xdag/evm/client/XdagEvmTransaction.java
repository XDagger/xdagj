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

import io.xdag.core.TransactionType;
import io.xdag.core.XdagTransaction;
import io.xdag.utils.EVMUtils;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

/**
 * Facade for Xdag Transaction Block -> Evm Transaction
 */
public class XdagEvmTransaction implements Transaction {

    private final XdagTransaction transaction;

    public XdagEvmTransaction(XdagTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public boolean isCreate() {
        return transaction.getType().equals(TransactionType.CREATE);
    }

    @Override
    public Bytes getFrom() {
        return transaction.getFrom();
    }

    @Override
    public Bytes getTo() {
        return transaction.getTo();
    }

    @Override
    public long getNonce() {
        return transaction.getNonce();
    }

    @Override
    public BigInteger getValue() {
        return EVMUtils.xAmountToWei(transaction.getValue());
    }

    @Override
    public Bytes getData() {
        return transaction.getData();
    }

    @Override
    public long getGas() {
        return transaction.getGas();
    }

    @Override
    public BigInteger getGasPrice() {
        return EVMUtils.xAmountToWei(transaction.getGasPrice());
    }

}
