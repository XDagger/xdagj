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
package io.xdag.core;

import static io.xdag.core.XAmount.ZERO;
import static io.xdag.core.XUnit.XDAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.core.state.AccountState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class TransactionExecutorTest {

    @Rule
    public TemporaryDatabaseRule temporaryDBFactory = new TemporaryDatabaseRule();

    private Config config;
    private Dagchain chain;
    private AccountState as;
    private TransactionExecutor exec;
    private Network network;
    private PendingManager pendingMgr;

    @Before
    public void prepare() {
        config = new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR);
        pendingMgr = Mockito.mock(PendingManager.class);
        chain = new DagchainImpl(config, pendingMgr, temporaryDBFactory);
        as = chain.getLatestAccountState();
        exec = new TransactionExecutor(config);
        network = config.getNodeSpec().getNetwork();
    }

    private TransactionResult executeAndCommit(TransactionExecutor exec, Transaction tx, AccountState as) {
        TransactionResult res = exec.execute(tx, as);
        as.commit();

        return res;
    }

    @Test
    public void testTransfer() {
        KeyPair key = SampleKeys.KEY1;

        TransactionType type = TransactionType.TRANSFER;
        byte[] from = Keys.toBytesAddress(key);
        byte[] to = BytesUtils.random(20);
        XAmount value = XAmount.of(5);
        XAmount fee = config.getDagSpec().getMinTransactionFee();
        long nonce = as.getAccount(from).getNonce();
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] data = BytesUtils.random(16);

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, data);
        tx.sign(key);
        assertTrue(tx.validate(network));

        // insufficient available
        TransactionResult result = exec.execute(tx, as.clone());
        assertFalse(result.getCode().isSuccess());

        XAmount available = XAmount.of(1000, XDAG);
        as.adjustAvailable(Keys.toBytesAddress(key), available);

        // execute but not commit
        result = exec.execute(tx, as.clone());
        assertTrue(result.getCode().isSuccess());
        assertEquals(available, as.getAccount(Keys.toBytesAddress(key)).getAvailable());
        assertEquals(ZERO, as.getAccount(to).getAvailable());

        // execute and commit
        result = executeAndCommit(exec, tx, as);
        assertTrue(result.getCode().isSuccess());
        assertEquals(available.subtract(value.add(fee)), as.getAccount(Keys.toBytesAddress(key)).getAvailable());
        assertEquals(value, as.getAccount(to).getAvailable());
    }

}
