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
import java.util.HashMap;
import java.util.Map;

public class RepositoryMock implements Repository {

    private final Map<Bytes, Account> accounts = new HashMap<>();
    private final RepositoryMock parent;

    public RepositoryMock() {
        this(null);
    }

    public RepositoryMock(RepositoryMock parent) {
        this.parent = parent;
    }

    /**
     * Returns an account if exists.
     *
     * @param address
     *            the account address
     * @return an account if exists, NULL otherwise
     */
    protected Account getAccount(Bytes address) {
        Bytes key = address;
        if (accounts.containsKey(key)) {
            return accounts.get(key);
        } else if (parent != null && parent.exists(address)) {
            Account account = parent.getAccount(address);
            Account accountTrack = new Account(account);
            accounts.put(key, accountTrack);
            return accountTrack;
        } else {
            return null;
        }
    }

    @Override
    public boolean exists(Bytes address) {
        if (accounts.containsKey(address)) {
            return accounts.get(address) != null;
        } else if (parent != null) {
            return parent.exists(address);
        } else {
            return false;
        }
    }

    @Override
    public void createAccount(Bytes address) {
        if (!exists(address)) {
            accounts.put(address, new Account());
        }
    }

    @Override
    public void delete(Bytes address) {
        accounts.put(address, null);
    }

    @Override
    public long increaseNonce(Bytes address) {
        createAccount(address);
        return getAccount(address).nonce += 1;
    }

    @Override
    public long setNonce(Bytes address, long nonce) {
        createAccount(address);
        return (getAccount(address).nonce = nonce);
    }

    @Override
    public long getNonce(Bytes address) {
        Account account = getAccount(address);
        return account == null ? 0 : account.nonce;
    }

    @Override
    public void saveCode(Bytes address, Bytes code) {
        createAccount(address);
        getAccount(address).code = code;
    }

    @Override
    public Bytes getCode(Bytes address) {
        Account account = getAccount(address);
        return account == null ? null : account.code;
    }

    @Override
    public void putStorageRow(Bytes address, DataWord key, DataWord value) {
        createAccount(address);
        getAccount(address).storage.put(key, value);
    }

    @Override
    public DataWord getStorageRow(Bytes address, DataWord key) {
        Account account = getAccount(address);
        return account == null ? null : account.storage.get(key);
    }

    @Override
    public BigInteger getBalance(Bytes address) {
        Account account = getAccount(address);
        return account == null ? BigInteger.ZERO : account.balance;
    }

    @Override
    public BigInteger addBalance(Bytes address, BigInteger value) {
        createAccount(address);
        Account account = getAccount(address);
        return account.balance = account.balance.add(value);
    }

    @Override
    public RepositoryMock startTracking() {
        return new RepositoryMock(this);
    }

    @Override
    public Repository clone() {
        RepositoryMock copy = new RepositoryMock(parent);
        for (Map.Entry<Bytes, Account> entry : accounts.entrySet()) {
            copy.accounts.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    @Override
    public void commit() {
        if (parent != null) {
            parent.accounts.putAll(accounts);
        }
    }

    @Override
    public void rollback() {
        accounts.clear();
    }

    static class Account {
        public long nonce = 0;
        public BigInteger balance = BigInteger.ZERO;
        public Bytes code = Bytes.EMPTY;
        public Map<DataWord, DataWord> storage = new HashMap<>();

        public Account() {
        }

        public Account(Account parent) {
            this.nonce = parent.nonce;
            this.balance = parent.balance;
            this.code = parent.code;
            this.storage = new HashMap<>(parent.storage);
        }

        public Account clone() {
            Account a = new Account();
            a.nonce = nonce;
            a.balance = balance;
            a.code = code.copy();
            a.storage = new HashMap<>(storage);

            return a;
        }
    }
}

