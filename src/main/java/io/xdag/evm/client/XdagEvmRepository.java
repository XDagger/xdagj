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
import io.xdag.state.AccountState;
import io.xdag.utils.EVMUtils;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

/**
 * Facade class for AccountState -> Repository
 *
 * We will probably want to make AccountState just implement repository but for
 * ease of initial integration, use a facade to limit scope
 */
public class XdagEvmRepository implements Cloneable, Repository {

    private final AccountState accountState;

    public XdagEvmRepository(AccountState accountState) {
        this.accountState = accountState;
    }

    public AccountState getAccountState() {
        return accountState;
    }

    @Override
    public boolean exists(Bytes address) {
        return accountState.exists(address);
    }

    @Override
    public void createAccount(Bytes address) {
        if (!exists(address)) {
            accountState.setCode(address, Bytes.wrap(new byte[] {}));
        }
    }

    @Override
    public void delete(Bytes address) {
        if (exists(address)) {
            accountState.setCode(address, null);
        }
    }

    @Override
    public long increaseNonce(Bytes address) {
        return accountState.increaseNonce(address);
    }

    @Override
    public long setNonce(Bytes address, long nonce) {
        return accountState.setNonce(address, nonce);
    }

    @Override
    public long getNonce(Bytes address) {
        return accountState.getAccount(address).getNonce();
    }

    @Override
    public void saveCode(Bytes address, Bytes code) {
        accountState.setCode(address, code);
    }

    @Override
    public Bytes getCode(Bytes address) {
        return accountState.getCode(address);
    }

    @Override
    public void putStorageRow(Bytes address, DataWord key, DataWord value) {
        accountState.putStorage(address, key.getData(), value.getData());
    }

    @Override
    public DataWord getStorageRow(Bytes address, DataWord key) {
        Bytes data = accountState.getStorage(address, key.getData());
        if (data != null) {
            return DataWord.of(data);
        }
        return null;
    }

    @Override
    public BigInteger getBalance(Bytes address) {
        return EVMUtils.xAmountToWei(accountState.getAccount(address).getAvailable());
    }

    @Override
    public BigInteger addBalance(Bytes address, BigInteger value) {
        accountState.adjustAvailable(address, EVMUtils.weiToXAmount(value));
        return value;
    }

    @Override
    public Repository startTracking() {
        return new XdagEvmRepository(accountState.track());
    }

    @Override
    public Repository clone() {
        return new XdagEvmRepository(accountState.clone());
    }

    @Override
    public void commit() {
        accountState.commit();
    }

    @Override
    public void rollback() {
        accountState.rollback();
    }

}
