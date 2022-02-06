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

import java.math.BigInteger;

/**
 * A facade interface for Transaction. The client needs to wrap the native
 * transaction to comply this specification, in order to use EVM.
 */
public interface Transaction {

    /**
     * Returns whether this transaction is a CREATE.
     *
     * @return true if it's CREATE.
     */
    boolean isCreate();

    /**
     * Returns the address.
     *
     * @return a 20-byte array, not NULL.
     */
    Bytes getFrom();

    /**
     * Returns the recipient address.
     *
     * @return a 20-byte array, or
     *         {@link org.apache.commons.lang3.ArrayUtils#EMPTY_BYTE_ARRAY} for
     *         CREATE, not NULL.
     */
    Bytes getTo();

    /**
     * Returns the nonce of the sender.
     *
     * @return the nonce
     */
    long getNonce();

    /**
     * Returns the value being transferred.
     *
     * @return the value with a decimal of <em>18</em>, not NULL.
     */
    BigInteger getValue();

    /**
     * Returns the data field.
     *
     * @return the call data, not NULL.
     */
    Bytes getData();

    /**
     * Returns the gas limit.
     *
     * @return the specified gas for this transaction.
     */
    long getGas();

    /**
     * Returns the gas price.
     *
     * @return the specified gas price with a decimal of <em>18</em>, not NULL.
     */
    BigInteger getGasPrice();
}

