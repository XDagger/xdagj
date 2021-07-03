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
package io.xdag.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import io.xdag.core.XAmount;
import io.xdag.db.KVSource;

public class AccountStateImpl implements Cloneable, AccountState {

    protected static final byte TYPE_ACCOUNT = 0;
    protected static final byte TYPE_CODE = 1;
    protected static final byte TYPE_STORAGE = 2;

    protected KVSource<Bytes, Bytes> accountDB;
    protected AccountStateImpl prev;

    /**
     * All updates, or deletes if the value is null.
     */
    protected final Map<Bytes, Bytes> updates = new ConcurrentHashMap<>();

    /**
     * Create an {@link AccountState} that work directly on a database.
     *
     */
    public AccountStateImpl(KVSource<Bytes, Bytes> accountDB) {
        this.accountDB = accountDB;
    }

    /**
     * Create an {@link AccountState} based on a previous AccountState.
     *
     */
    public AccountStateImpl(AccountStateImpl prev) {
        this.prev = prev;
    }

    @Override
    public Account getAccount(Bytes address) {
        Bytes k = getKey(TYPE_ACCOUNT, address);
        XAmount noAmount = XAmount.ZERO;

        if (updates.containsKey(k)) {
            Bytes v = updates.get(k);
            return v == null ? new Account(address, noAmount, noAmount, 0) : Account.fromBytes(address, v);
        } else if (prev != null) {
            return prev.getAccount(address);
        } else {
            Bytes v = accountDB.get(k);
            return v == null ? new Account(address, noAmount, noAmount, 0) : Account.fromBytes(address, v);
        }
    }

    @Override
    public long increaseNonce(Bytes address) {
        Bytes k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        long nonce = acc.getNonce() + 1;
        acc.setNonce(nonce);
        updates.put(k, acc.toBytes());
        return nonce;
    }

    @Override
    public void adjustAvailable(Bytes address, XAmount delta) {
        Bytes k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        acc.setAvailable(acc.getAvailable().add(delta));
        updates.put(k, acc.toBytes());
    }

    @Override
    public void adjustLocked(Bytes address, XAmount delta) {
        Bytes k = getKey(TYPE_ACCOUNT, address);

        Account acc = getAccount(address);
        acc.setLocked(acc.getLocked().add(delta));
        updates.put(k, acc.toBytes());
    }

    @Override
    public Bytes getCode(Bytes address) {
        Bytes k = getKey(TYPE_CODE, address);

        if (updates.containsKey(k)) {
            return updates.get(k);
        } else if (prev != null) {
            return prev.getCode(address);
        } else {
            return accountDB.get(k);
        }
    }

    @Override
    public void setCode(Bytes address, Bytes code) {
        Bytes k = getKey(TYPE_CODE, address);
        updates.put(k, code);
    }

    @Override
    public Bytes getStorage(Bytes address, Bytes key) {
        Bytes k = getStorageKey(address, key);

        if (updates.containsKey(k)) {
            return updates.get(k);
        } else if (prev != null) {
            return prev.getStorage(address, key);
        } else {
            return accountDB.get(k);
        }
    }

    @Override
    public void putStorage(Bytes address, Bytes key, Bytes value) {
        Bytes storeKey = getStorageKey(address, key);
        updates.put(storeKey, value);
    }

    @Override
    public void removeStorage(Bytes address, Bytes key) {
        Bytes storeKey = getStorageKey(address, key);
        updates.put(storeKey, null);
    }

    @Override
    public AccountState track() {
        return new AccountStateImpl(this);
    }

    @Override
    public void commit() {
        synchronized (updates) {
            if (prev == null) {
                for (Map.Entry<Bytes, Bytes> entry : updates.entrySet()) {
                    if (entry.getValue() == null) {
                        accountDB.delete(entry.getKey());
                    } else {
                        accountDB.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                for (Map.Entry<Bytes, Bytes> e : updates.entrySet()) {
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
    public boolean exists(Bytes address) {
        Bytes k = getKey(TYPE_ACCOUNT, address);

        if (updates.containsKey(k)) {
            return true;
        } else if (prev != null) {
            return prev.exists(address);
        } else {
            Bytes v = accountDB.get(k);
            return v != null;
        }
    }

    @Override
    public long setNonce(Bytes address, long nonce) {
        Bytes k = getKey(TYPE_ACCOUNT, address);

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

    protected Bytes getKey(byte type, Bytes address) {
        return Bytes.concatenate(Bytes.of(type), address);
    }

    protected Bytes getStorageKey(Bytes address, Bytes key) {
//        byte[] buf = new byte[1 + address.length + key.length];
        MutableBytes buf = MutableBytes.create(1 + address.size() + key.size());
//        buf[0] = TYPE_STORAGE;
        buf.set(0, TYPE_STORAGE);
//        System.arraycopy(address, 0, buf, 1, address.length);
        buf.set(1, address);
//        System.arraycopy(key, 0, buf, 1 + address.length, key.length);
        buf.set(1 + address.size(), key);
        return buf;
    }

}
