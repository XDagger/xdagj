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

import static io.xdag.core.XUnit.XDAG;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.xdag.TestUtils;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.rules.KernelRule;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class BlockchainImportTest {

    @Rule
    public KernelRule kernelRule = new KernelRule(8001);

    @Rule
    public TemporaryDatabaseRule temporaryDBRule = new TemporaryDatabaseRule();

    private PendingManager pendingMgr;

    @Before
    public void setUp() {
        pendingMgr = mock(PendingManager.class);
    }

    @Test
    public void testDuplicatedTransaction() {
        // mock blockchain with a single transaction
        KeyPair to = SampleKeys.KEY_PAIR;
        KeyPair from1 = SampleKeys.KEY1;
        long time = TimeUtils.currentTimeMillis();
        Transaction tx1 = new Transaction(
                kernelRule.getKernel().getConfig().getNodeSpec().getNetwork(),
                TransactionType.TRANSFER,
                Keys.toBytesAddress(to),
                XAmount.of(10, XDAG),
                kernelRule.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                0,
                time,
                BytesUtils.EMPTY_BYTES).sign(from1);
        kernelRule.getKernel().setDagchain(new DagchainImpl(kernelRule.getKernel().getConfig(), pendingMgr, temporaryDBRule));
        kernelRule.getKernel().getDagchain().getAccountState().adjustAvailable(Keys.toBytesAddress(from1), XAmount.of(
                1000, XDAG));
        MainBlock block1 = kernelRule.createMainBlock(Collections.singletonList(tx1));
        kernelRule.getKernel().getDagchain().addMainBlock(block1);
        // create a tx with the same hash with tx1 from a different signer in the second
        // block
        KeyPair from2 = SampleKeys.KEY2;
        kernelRule.getKernel().getDagchain().getAccountState().adjustAvailable(Keys.toBytesAddress(from2), XAmount.of(
                1000, XDAG));
        Transaction tx2 = new Transaction(
                kernelRule.getKernel().getConfig().getNodeSpec().getNetwork(),
                TransactionType.TRANSFER,
                Keys.toBytesAddress(to),
                XAmount.of(10, XDAG),
                kernelRule.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                0,
                time,
                BytesUtils.EMPTY_BYTES).sign(from2);
        MainBlock block2 = kernelRule.createMainBlock(Collections.singletonList(tx2));

        // this test case is valid if and only if tx1 and tx2 have the same tx hash
        assert (Arrays.equals(tx1.getHash(), tx2.getHash()));

        // the block should be rejected because of the duplicated tx
        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block2));
    }

    @Test
    public void testValidateCoinbaseMagic() {
        DagchainImpl chain = spy(new DagchainImpl(kernelRule.getKernel().getConfig(), pendingMgr, temporaryDBRule));
        kernelRule.getKernel().setDagchain(chain);

        // block.coinbase = coinbase magic account
        MainBlock block = TestUtils.createBlock(
                kernelRule.getKernel().getDagchain().getLatestMainBlock().getHash(),
                Constants.COINBASE_KEY,
                kernelRule.getKernel().getDagchain().getLatestMainBlockNumber() + 1,
                Collections.emptyList(),
                Collections.emptyList());

        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block));

        // tx.to = coinbase magic account
        Transaction tx = TestUtils.createTransaction(kernelRule.getKernel().getConfig(), SampleKeys.KEY_PAIR,
                Constants.COINBASE_KEY, XAmount.ZERO);
        MainBlock block2 = TestUtils.createBlock(
                kernelRule.getKernel().getDagchain().getLatestMainBlock().getHash(),
                SampleKeys.KEY1,
                kernelRule.getKernel().getDagchain().getLatestMainBlockNumber() + 1,
                Collections.singletonList(tx),
                Collections.singletonList(new TransactionResult()));

        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block2));
    }

    @Test
    public void testCheckpoints() {
        // mock checkpoints
        Map<Long, byte[]> checkpoints = new HashMap<>();
        checkpoints.put(1L, BytesUtils.random(32));
        Config config = spy(kernelRule.getKernel().getConfig());
        when(config.getDagSpec().checkpoints()).thenReturn(checkpoints);

        // mock the chain
        DagchainImpl chain = spy(new DagchainImpl(config, pendingMgr, temporaryDBRule));
        kernelRule.getKernel().setDagchain(chain);

        // prepare block
        MainBlock block = kernelRule.createMainBlock(Collections.emptyList());

        // tests
        assertFalse(chain.importBlock(block));
    }
}
