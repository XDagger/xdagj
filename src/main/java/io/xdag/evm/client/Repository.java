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

import io.xdag.evm.DataWord;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

public interface Repository {

    /**
     * Checks whether an account exists.
     *
     * @param address
     *            the account address
     * @return true if account exist, false otherwise
     */
    boolean exists(Bytes address);

    /**
     * Creates an account if not exist.
     *
     * @param address
     *            the account address
     * @ImplNote trigger account creation
     */
    void createAccount(Bytes address);

    /**
     * Deletes an account.
     *
     * @param address
     *            the account address
     */
    void delete(Bytes address);

    /**
     * Increases the account nonce of the given account by one.
     *
     * @param address
     *            the account address
     * @return new value of the nonce
     * @ImplNote trigger account creation
     */
    long increaseNonce(Bytes address);

    /**
     * Sets the account nonce of the given account
     *
     * @param address
     *            the account address
     * @param nonce
     *            new nonce
     * @return new value of the nonce
     * @ImplNote trigger account creation
     */
    long setNonce(Bytes address, long nonce);

    /**
     * Returns current nonce of a given account
     *
     * @param address
     *            the account address
     * @return value of the nonce
     */
    long getNonce(Bytes address);

    /**
     * Stores code associated with an account
     *
     * @param address
     *            the account address
     * @param code
     *            that will be associated with this account
     * @ImplNote trigger account creation
     */
    void saveCode(Bytes address, Bytes code);

    /**
     * Retrieves the code associated with an account
     *
     * @param address
     *            the account address
     * @return code in byte-array format, or NULL if not exist
     */
    Bytes getCode(Bytes address);

    /**
     * Puts a value in storage of an account at a given key
     *
     * @param address
     *            the account address
     * @param key
     *            of the data to store
     * @param value
     *            is the data to store
     * @ImplNote trigger account creation
     */
    void putStorageRow(Bytes address, DataWord key, DataWord value);

    /**
     * Retrieves storage value from an account for a given key
     *
     * @param address
     *            the account address
     * @param key
     *            associated with this value
     * @return the value, or NULL if not exist
     */
    DataWord getStorageRow(Bytes address, DataWord key);

    /**
     * Retrieves balance of an account
     *
     * @param address
     *            the account address
     * @return balance of the account as a <code>BigInteger</code> value
     */
    BigInteger getBalance(Bytes address);

    /**
     * Add value to the balance of an account
     *
     * @param address
     *            the account address
     * @param value
     *            to be added
     * @return new balance of the account
     * @ImplNote trigger account creation
     */
    BigInteger addBalance(Bytes address, BigInteger value);

    /**
     * Save a snapshot and start tracking future changes
     *
     * @return the tracker repository
     */
    Repository startTracking();

    /**
     * Clone this repository, including all the uncommitted changes.
     */
    Repository clone();

    /**
     * Stores all the temporary changes made to the repository in the actual
     * database
     */
    void commit();

    /**
     * Undoes all the changes made so far to a snapshot of the repository
     */
    void rollback();
}

