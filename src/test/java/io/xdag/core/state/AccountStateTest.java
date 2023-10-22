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

import static io.xdag.core.XAmount.ZERO;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.core.XAmount;
import io.xdag.core.Dagchain;
import io.xdag.core.DagchainImpl;
import io.xdag.core.Genesis;
import io.xdag.core.PendingManager;
import io.xdag.core.XUnit;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BytesUtils;

public class AccountStateTest {

    private Dagchain chain;
    private AccountState state;

    @Rule
    public TemporaryDatabaseRule temporaryDBFactory = new TemporaryDatabaseRule();

    @Before
    public void setUp() {
        PendingManager pendingMgr = Mockito.mock(PendingManager.class);
        chain = new DagchainImpl(new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR), pendingMgr, temporaryDBFactory);
        state = chain.getLatestAccountState();
    }

    @Test
    public void testAtGenesis() {
        Map<ByteArray, Genesis.XSnapshot> snapshot = chain.getGenesis().getSnapshots();

        for (ByteArray k : snapshot.keySet()) {
            Account acc = state.getAccount(k.getData());
            assertEquals(snapshot.get(k).getAmount(), acc.getAvailable());
        }
    }

    @Test
    public void testAccount() {
        byte[] address = BytesUtils.random(20);
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
        byte[] address = BytesUtils.random(20);
        Account acc = state.getAccount(address);

        assertArrayEquals(address, acc.getAddress());
        assertEquals(ZERO, acc.getAvailable());
        assertEquals(ZERO, acc.getLocked());
        assertEquals(0, acc.getNonce());
    }

    @Test
    public void testAvailable() {
        byte[] address = BytesUtils.random(20);
        assertEquals(ZERO, state.getAccount(address).getAvailable());
        state.adjustAvailable(address, XAmount.of(20));
        assertEquals(XAmount.of(20), state.getAccount(address).getAvailable());

        AccountState state2 = state.clone();
        assertEquals(XAmount.of(20), state2.getAccount(address).getAvailable());

        state2.rollback();
        assertEquals(ZERO, state2.getAccount(address).getAvailable());
    }

    @Test
    public void testLocked() {
        byte[] address = BytesUtils.random(20);
        assertEquals(ZERO, state.getAccount(address).getLocked());
        state.adjustLocked(address, XAmount.of(20));
        assertEquals(XAmount.of(20), state.getAccount(address).getLocked());

        AccountState state2 = state.clone();
        assertEquals(XAmount.of(20), state2.getAccount(address).getLocked());

        state2.rollback();
        assertEquals(ZERO, state2.getAccount(address).getLocked());
    }

    @Test
    public void testNonce() {
        byte[] address = BytesUtils.random(20);
        assertEquals(0, state.getAccount(address).getNonce());
        state.increaseNonce(address);
        assertEquals(1, state.getAccount(address).getNonce());

        AccountState state2 = state.clone();
        assertEquals(1, state2.getAccount(address).getNonce());

        state2.rollback();
        assertEquals(0, state2.getAccount(address).getNonce());
    }

    @Test
    public void testClone() {
        byte[] address = BytesUtils.random(20);

        state.adjustAvailable(address, XAmount.of(100, XUnit.XDAG));

        AccountState s1 = state.clone();
        AccountState s2 = state.clone();

        s1.adjustAvailable(address, XAmount.of(10, XUnit.XDAG));
        s2.adjustAvailable(address, XAmount.of(20, XUnit.XDAG));

        assertEquals(XAmount.of(110, XUnit.XDAG), s1.getAccount(address).getAvailable());

        assertEquals(XAmount.of(120, XUnit.XDAG), s2.getAccount(address).getAvailable());

        assertEquals(XAmount.of(100, XUnit.XDAG), state.getAccount(address).getAvailable());

        s1.commit();
        assertEquals(XAmount.of(110, XUnit.XDAG), s1.getAccount(address).getAvailable());

        s2.commit();
        assertEquals(XAmount.of(120, XUnit.XDAG), s2.getAccount(address).getAvailable());

        assertEquals(XAmount.of(100, XUnit.XDAG), state.getAccount(address).getAvailable());
        state.commit();

        assertEquals(XAmount.of(100, XUnit.XDAG), s1.getAccount(address).getAvailable());
        assertEquals(XAmount.of(100, XUnit.XDAG), s2.getAccount(address).getAvailable());
    }
}
