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
package io.xdag.core.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.xdag.core.XAmount;
import io.xdag.db.Database;
import io.xdag.utils.BytesUtils;

/**
 * Account state implementation.
 * 
 * <pre>
 * account DB structure:
 * 
 * [0, address] => [account_object]
 * </pre>
 */
public class AccountStateImpl implements Cloneable, AccountState {

    protected static final byte TYPE_ACCOUNT = 0;

    protected Database accountDB;
    protected AccountStateImpl prev;

    /**
     * All updates, or deletes if the value is null.
     */
    protected final Map<ByteArray, byte[]> updates = new ConcurrentHashMap<>();

    /**
     * Create an {@link AccountState} that work directly on a database.
     */
    public AccountStateImpl(Database accountDB) {
        this.accountDB = accountDB;
    }

    /**
     * Create an {@link AccountState} based on a previous AccountState.
     */
    public AccountStateImpl(AccountStateImpl prev) {
        this.prev = prev;
    }

    @Override
    public Account getAccount(byte[] address) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);
        XAmount noAmount = XAmount.ZERO;

        if (updates.containsKey(k)) {
            byte[] v = updates.get(k);
            return v == null ? new Account(address, noAmount, noAmount, 0) : Account.fromBytes(address, v);
        } else if (prev != null) {
            return prev.getAccount(address);
        } else {
            byte[] v = accountDB.get(k.getData());
            return v == null ? new Account(address, noAmount, noAmount, 0) : Account.fromBytes(address, v);
        }
    }

    @Override
    public long increaseNonce(byte[] address) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        long nonce = acc.getNonce() + 1;
        acc.setNonce(nonce);
        updates.put(k, acc.toBytes());
        return nonce;
    }

    @Override
    public void adjustAvailable(byte[] address, XAmount delta) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        acc.setAvailable(acc.getAvailable().add(delta));
        updates.put(k, acc.toBytes());
    }

    @Override
    public void adjustLocked(byte[] address, XAmount delta) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        acc.setLocked(acc.getLocked().add(delta));
        updates.put(k, acc.toBytes());
    }

    @Override
    public AccountState track() {
        return new AccountStateImpl(this);
    }

    @Override
    public void commit() {
        synchronized (updates) {
            if (prev == null) {
                for (Map.Entry<ByteArray, byte[]> entry : updates.entrySet()) {
                    if (entry.getValue() == null) {
                        accountDB.delete(entry.getKey().getData());
                    } else {
                        accountDB.put(entry.getKey().getData(), entry.getValue());
                    }
                }
            } else {
                for (Map.Entry<ByteArray, byte[]> e : updates.entrySet()) {
                    prev.updates.put(e.getKey(), e.getValue());
                }
            }

            updates.clear();
        }
    }

    @Override
    public void rollback() {
        updates.clear();
    }

    @Override
    public boolean exists(byte[] address) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);

        if (updates.containsKey(k)) {
            return true;
        } else if (prev != null) {
            return prev.exists(address);
        } else {
            byte[] v = accountDB.get(k.getData());
            return v != null;
        }
    }

    @Override
    public long setNonce(byte[] address, long nonce) {
        ByteArray k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        acc.setNonce(nonce);
        updates.put(k, acc.toBytes());
        return nonce;
    }

    @Override
    public AccountState clone() {
        AccountStateImpl clone = new AccountStateImpl(accountDB);
        clone.prev = prev;
        clone.updates.putAll(updates);

        return clone;
    }

    protected ByteArray getKey(byte type, byte[] address) {
        return ByteArray.of(BytesUtils.merge(type, address));
    }

}
