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

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.XAmount;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.evm.client.RocksdbProxy;

public class AccountStateTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();
    private AccountState state;

    @Before
    public void setUp() throws Exception {
        Config config = new DevnetConfig();
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());
        RocksdbKVSource dataSource = new RocksdbKVSource("xdag-evm");
        dataSource.setConfig(config);
        RocksdbProxy rocksdbProxy = new RocksdbProxy(dataSource);
        rocksdbProxy.init();
        state = new AccountStateImpl(rocksdbProxy);
    }

    @Test
    public void testAccount() {
        Bytes address = Bytes.random(20);
        Account acc = state.getAccount(address);
        acc.setAvailable(XAmount.of(1));
        acc.setLocked(XAmount.of(2));
        acc.setNonce(3);

        Account acc2 = Account.fromBytes(address, acc.toBytes());
        assertEquals(XAmount.of(1), acc2.getAvailable());
        assertEquals(XAmount.of(2), acc2.getLocked());
        assertEquals(3L, acc2.getNonce());
    }

    @Test
    public void testNonExists() {
        Bytes address = Bytes.random(20);
        Account acc = state.getAccount(address);

        assertEquals(address, acc.getAddress());
        assertEquals(XAmount.ZERO, acc.getAvailable());
        assertEquals(XAmount.ZERO, acc.getLocked());
        assertEquals(0, acc.getNonce());
    }

    @Test
    public void testCode() {
        Bytes address = Bytes.random(20);
        Bytes addressCode = Bytes.random(20);
        Bytes code = state.getCode(address);
        assertNull(code);
        state.setCode(address, addressCode);
        code = state.getCode(address);
        assertEquals(addressCode, code);
        state.commit();
        code = state.getCode(address);
        assertEquals(addressCode, code);
    }

    @Test
    public void testStorage() {
        Bytes address = Bytes.random(20);
        Bytes addressStorage1 = Bytes.random(20);
        Bytes storageKey1 = Bytes.random(3);
        Bytes storageKey2 = Bytes.random(3);
        Bytes addressStorage2 = Bytes.random(21);
        state.putStorage(address, storageKey2, addressStorage2);
        Bytes storage = state.getStorage(address, storageKey1);
        assertNull(storage);
        state.putStorage(address, storageKey1, addressStorage1);
        storage = state.getStorage(address, storageKey1);
        assertEquals(addressStorage1, storage);
        state.commit();
        storage = state.getStorage(address, storageKey1);
        assertEquals(addressStorage1, storage);
    }

    @Test
    public void testAvailable() {
        Bytes address = Bytes.random(20);
        assertEquals(XAmount.ZERO, state.getAccount(address).getAvailable());
        state.adjustAvailable(address, XAmount.of(20));
        assertEquals(XAmount.of(20), state.getAccount(address).getAvailable());

        AccountState state2 = state.track();
        assertEquals(XAmount.of(20), state2.getAccount(address).getAvailable());

        state.rollback();
        assertEquals(XAmount.ZERO, state2.getAccount(address).getAvailable());
    }

    @Test
    public void testLocked() {
        Bytes address = Bytes.random(20);
        assertEquals(XAmount.ZERO, state.getAccount(address).getLocked());
        state.adjustLocked(address, XAmount.of(20));
        assertEquals(XAmount.of(20), state.getAccount(address).getLocked());

        AccountState state2 = state.track();
        assertEquals(XAmount.of(20), state2.getAccount(address).getLocked());

        state.rollback();
        assertEquals(XAmount.ZERO, state2.getAccount(address).getLocked());
    }

    @Test
    public void testNonce() {
        Bytes address = Bytes.random(20);
        assertEquals(0, state.getAccount(address).getNonce());
        state.increaseNonce(address);
        assertEquals(1, state.getAccount(address).getNonce());

        AccountState state2 = state.track();
        assertEquals(1, state2.getAccount(address).getNonce());

        state.rollback();
        assertEquals(0, state2.getAccount(address).getNonce());
    }
}

