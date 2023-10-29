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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
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
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.rules.KernelRule;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class DagchainImportTest {

    @Rule
    public KernelRule kernelRule = new KernelRule(8000);

    @Rule
    public KernelRule kernelRule1 = new KernelRule(8001);

    @Rule
    public KernelRule kernelRule2 = new KernelRule(8002);

    @Rule
    public TemporaryDatabaseRule temporaryDBRule = new TemporaryDatabaseRule();

    @Rule
    public TemporaryDatabaseRule temporaryDBRule1 = new TemporaryDatabaseRule();

    @Rule
    public TemporaryDatabaseRule temporaryDBRule2 = new TemporaryDatabaseRule();

    private PendingManager pendingMgr;

    @Before
    public void setUp() {
        pendingMgr = mock(PendingManager.class);
    }

    @Test
    public void testDuplicatedTransaction()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // mock blockchain with a single transaction
        KeyPair to = Keys.createEcKeyPair();
        KeyPair from1 = Keys.createEcKeyPair();
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
        MainBlock latestMainBlock = kernelRule.getKernel().getDagchain().getLatestMainBlock();
        AccountState latestAccountState = kernelRule.getKernel().getDagchain().getAccountState(latestMainBlock.getHash(), latestMainBlock.getNumber());
        BlockState latestBlockState = kernelRule.getKernel().getDagchain().getBlockState(latestMainBlock.getHash(), latestMainBlock.getNumber());
        latestAccountState.adjustAvailable(Keys.toBytesAddress(from1), XAmount.of(1000, XDAG));
        MainBlock block1 = kernelRule.createMainBlock(from1, Collections.singletonList(tx1));
        kernelRule.getKernel().getDagchain().importBlock(block1, latestAccountState, latestBlockState);

        // create a tx with the same hash with tx1 from a different signer in the second
        // block
        KeyPair from2 = Keys.createEcKeyPair();
        kernelRule.getKernel().getDagchain().getLatestAccountState().adjustAvailable(Keys.toBytesAddress(from2), XAmount.of(
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

        MainBlock block2 = kernelRule.createMainBlock(from2, Collections.singletonList(tx2));

        // this test case is valid if and only if tx1 and tx2 have the same tx hash
        assert (Arrays.equals(tx1.getHash(), tx2.getHash()));

        latestAccountState = kernelRule.getKernel().getDagchain().getLatestAccountState();
        latestBlockState = kernelRule.getKernel().getDagchain().getLatestBlockState();

        // the block should be rejected because of the duplicated tx
        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block2, latestAccountState, latestBlockState));
    }

    @Test
    public void testValidateCoinbaseMagic()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        DagchainImpl chain = spy(new DagchainImpl(kernelRule.getKernel().getConfig(), pendingMgr, temporaryDBRule));
        kernelRule.getKernel().setDagchain(chain);

        AccountState latestAccountState = kernelRule.getKernel().getDagchain().getLatestAccountState();
        BlockState latestBlockState = kernelRule.getKernel().getDagchain().getLatestBlockState();

        // block.coinbase = coinbase magic account
        MainBlock block = TestUtils.createBlock(
                kernelRule.getKernel().getDagchain().getLatestMainBlock().getHash(),
                Constants.COINBASE_KEY,
                kernelRule.getKernel().getDagchain().getLatestMainBlockNumber() + 1,
                Collections.emptyList(),
                Collections.emptyList());

        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block, latestAccountState, latestBlockState));

        // tx.to = coinbase magic account
        Transaction tx = TestUtils.createTransaction(kernelRule.getKernel().getConfig(), Keys.createEcKeyPair(),
                Constants.COINBASE_KEY, XAmount.ZERO);
        MainBlock block2 = TestUtils.createBlock(
                kernelRule.getKernel().getDagchain().getLatestMainBlock().getHash(),
                Keys.createEcKeyPair(),
                kernelRule.getKernel().getDagchain().getLatestMainBlockNumber() + 1,
                Collections.singletonList(tx),
                Collections.singletonList(new TransactionResult()));

        latestAccountState = kernelRule.getKernel().getDagchain().getLatestAccountState();
        latestBlockState = kernelRule.getKernel().getDagchain().getLatestBlockState();

        assertFalse(kernelRule.getKernel().getDagchain().importBlock(block2, latestAccountState, latestBlockState));
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
        MainBlock block = kernelRule.createMainBlock(SampleKeys.KEY1, Collections.emptyList());

        AccountState latestAccountState = kernelRule.getKernel().getDagchain().getLatestAccountState();
        BlockState latestBlockState = kernelRule.getKernel().getDagchain().getLatestBlockState();

        // tests
        assertFalse(chain.importBlock(block, latestAccountState, latestBlockState));
    }

    @Test
    public void testTwoDagchain()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair to = Keys.createEcKeyPair();
        KeyPair from = Keys.createEcKeyPair();
        long time = TimeUtils.currentTimeMillis();

        Config config1 = spy(kernelRule1.getKernel().getConfig());
        Config config2 = spy(kernelRule2.getKernel().getConfig());

        DagchainImpl chain1 = spy(new DagchainImpl(config1, pendingMgr, temporaryDBRule1));
        DagchainImpl chain2 = spy(new DagchainImpl(config2, pendingMgr, temporaryDBRule2));

        kernelRule1.getKernel().setDagchain(chain1);
        kernelRule2.getKernel().setDagchain(chain2);

        MainBlock latestMainBlock1 = kernelRule1.getKernel().getDagchain().getLatestMainBlock();
        MainBlock latestMainBlock2 = kernelRule2.getKernel().getDagchain().getLatestMainBlock();

        AccountState as1 = kernelRule1.getKernel().getDagchain().getAccountState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());
        AccountState as2 = kernelRule2.getKernel().getDagchain().getAccountState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());

        as1.adjustAvailable(Keys.toBytesAddress(from), XAmount.of(1000, XDAG));
        as2.adjustAvailable(Keys.toBytesAddress(from), XAmount.of(1000, XDAG));

        for(int i = 0 ; i < 20 ; i++) {
            latestMainBlock1 = kernelRule1.getKernel().getDagchain().getLatestMainBlock();
            latestMainBlock2 = kernelRule2.getKernel().getDagchain().getLatestMainBlock();

            as1 = kernelRule1.getKernel().getDagchain().getAccountState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());
            BlockState bs1 = kernelRule1.getKernel().getDagchain().getBlockState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());

            as2 = kernelRule2.getKernel().getDagchain().getAccountState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());
            BlockState bs2 = kernelRule2.getKernel().getDagchain().getBlockState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());


            Transaction tx1 = new Transaction(
                    kernelRule1.getKernel().getConfig().getNodeSpec().getNetwork(),
                    TransactionType.TRANSFER,
                    Keys.toBytesAddress(to),
                    XAmount.of(1, XDAG),
                    kernelRule1.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                    i,
                    time,
                    BytesUtils.EMPTY_BYTES).sign(from);

            Transaction tx2 = new Transaction(
                    kernelRule1.getKernel().getConfig().getNodeSpec().getNetwork(),
                    TransactionType.TRANSFER,
                    Keys.toBytesAddress(to),
                    XAmount.of(2, XDAG),
                    kernelRule1.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                    i,
                    time,
                    BytesUtils.EMPTY_BYTES).sign(from);

            MainBlock block1 = kernelRule1.createMainBlock(from, Collections.singletonList(tx1));
            // block2 have more difficulty
            MainBlock block2 = kernelRule2.createMainBlockWithdifficulty(from, Collections.singletonList(tx2), null, block1.getHash());

            // 1. chain1 and chain2 Run normally separately
            XAmount oldAvailable1 = chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable();
            assertTrue(chain1.importBlock(block1, as1.clone(), bs1.clone()));
            assertArrayEquals(block1.getHash(), chain1.getLatestMainBlock().getHash());
            assertEquals(i + 1, chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(from)).getNonce());
            assertEquals(XAmount.of( 1, XDAG).add(oldAvailable1), chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());

            XAmount oldAvailable2 = chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable();
            assertTrue(chain2.importBlock(block2, as2.clone(), bs2.clone()));
            assertArrayEquals(block2.getHash(), chain2.getLatestMainBlock().getHash());
            assertEquals(i + 1 , chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(from)).getNonce());
            assertEquals(XAmount.of( 2, XDAG).add(oldAvailable2), chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());

            assertEquals(i + 1, chain1.getLatestMainBlockNumber());
            assertEquals(i + 1, chain2.getLatestMainBlockNumber());

            // 2. chain1's block send to chain2, and chan2's block sand to chain1
            assertTrue(chain1.importBlock(block2, as1.clone(), bs1.clone()));
            // import block2 after block1 on same number, chain header will be reorg to block2, because block2 have more difficulty
            assertArrayEquals(block2.getHash(), chain1.getLatestMainBlock().getHash());
            assertEquals(XAmount.of( 2, XDAG).add(oldAvailable1), chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());

            assertTrue(chain2.importBlock(block1, as1.clone(), bs1.clone()));
            // import block1 after block2 on same number, chain header will not be reorg, because block2 have more difficulty
            assertArrayEquals(block2.getHash(), chain2.getLatestMainBlock().getHash());
            assertEquals(XAmount.of( 2, XDAG).add(oldAvailable2), chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());

            assertEquals(i + 1, chain1.getLatestMainBlockNumber());
            assertEquals(i + 1, chain2.getLatestMainBlockNumber());
        }

        assertEquals(20, chain1.getLatestMainBlockNumber());
        assertEquals(20, chain2.getLatestMainBlockNumber());
    }

    @Test
    public void testTwoDagchainSplitAndMerge()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair to = Keys.createEcKeyPair();
        KeyPair from = Keys.createEcKeyPair();
        long time = TimeUtils.currentTimeMillis();

        Config config1 = spy(kernelRule1.getKernel().getConfig());
        Config config2 = spy(kernelRule2.getKernel().getConfig());

        DagchainImpl chain1 = spy(new DagchainImpl(config1, pendingMgr, temporaryDBRule1));
        DagchainImpl chain2 = spy(new DagchainImpl(config2, pendingMgr, temporaryDBRule2));

        kernelRule1.getKernel().setDagchain(chain1);
        kernelRule2.getKernel().setDagchain(chain2);

        MainBlock latestMainBlock1 = kernelRule1.getKernel().getDagchain().getLatestMainBlock();
        MainBlock latestMainBlock2 = kernelRule2.getKernel().getDagchain().getLatestMainBlock();

        AccountState as1 = kernelRule1.getKernel().getDagchain()
                .getAccountState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());
        AccountState as2 = kernelRule2.getKernel().getDagchain()
                .getAccountState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());

        as1.adjustAvailable(Keys.toBytesAddress(from), XAmount.of(1000, XDAG));
        as2.adjustAvailable(Keys.toBytesAddress(from), XAmount.of(1000, XDAG));

        for (int i = 0; i < 20; i++) {
            latestMainBlock1 = kernelRule1.getKernel().getDagchain().getLatestMainBlock();
            latestMainBlock2 = kernelRule2.getKernel().getDagchain().getLatestMainBlock();

            as1 = kernelRule1.getKernel().getDagchain()
                    .getAccountState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());
            BlockState bs1 = kernelRule1.getKernel().getDagchain()
                    .getBlockState(latestMainBlock1.getHash(), latestMainBlock1.getNumber());

            as2 = kernelRule2.getKernel().getDagchain()
                    .getAccountState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());
            BlockState bs2 = kernelRule2.getKernel().getDagchain()
                    .getBlockState(latestMainBlock2.getHash(), latestMainBlock2.getNumber());

            Transaction tx1 = new Transaction(
                    kernelRule1.getKernel().getConfig().getNodeSpec().getNetwork(),
                    TransactionType.TRANSFER,
                    Keys.toBytesAddress(to),
                    XAmount.of(1, XDAG),
                    kernelRule1.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                    i,
                    time,
                    BytesUtils.EMPTY_BYTES).sign(from);

            Transaction tx2 = new Transaction(
                    kernelRule1.getKernel().getConfig().getNodeSpec().getNetwork(),
                    TransactionType.TRANSFER,
                    Keys.toBytesAddress(to),
                    XAmount.of(2, XDAG),
                    kernelRule1.getKernel().getConfig().getDagSpec().getMinTransactionFee(),
                    i,
                    time,
                    BytesUtils.EMPTY_BYTES).sign(from);

            MainBlock block1 = kernelRule1.createMainBlock(from, Collections.singletonList(tx1));

            // block2 have more difficulty
            MainBlock block2;

            // fork form number 5
            if (i < 5) {
                block2 = block1;
            } else {
                BlockHeader lastBlockHeader = chain2.getLatestMainBlock().getHeader();
                block2 = kernelRule2.createMainBlockWithdifficulty(from, Collections.singletonList(tx2), lastBlockHeader, block1.getHash());
            }

            // 1. chain1 and chain2 Run normally separately
            XAmount oldChain1Available = chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable();
            assertTrue(chain1.importBlock(block1, as1.clone(), bs1.clone()));
            assertArrayEquals(block1.getHash(), chain1.getLatestMainBlock().getHash());
            assertEquals(i + 1, chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(from)).getNonce());
            assertEquals(XAmount.of(1, XDAG).add(oldChain1Available),
                    chain1.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());

            XAmount oldChain2Available = chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable();
            assertTrue(chain2.importBlock(block2, as2.clone(), bs2.clone()));
            assertArrayEquals(block2.getHash(), chain2.getLatestMainBlock().getHash());
            assertEquals(i + 1, chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(from)).getNonce());

            if(i < 5) {
                assertEquals(XAmount.of(1, XDAG).add(oldChain2Available),
                        chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());
            } else {
                assertEquals(XAmount.of(2, XDAG).add(oldChain2Available),
                        chain2.getLatestAccountState().getAccount(Keys.toBytesAddress(to)).getAvailable());
            }

            assertEquals(i + 1, chain1.getLatestMainBlockNumber());
            assertEquals(i + 1, chain2.getLatestMainBlockNumber());
        }
        assertEquals(20, chain1.getLatestMainBlockNumber());
        assertEquals(20, chain2.getLatestMainBlockNumber());


        MainBlock chain1ForkBlock = chain1.getMainBlockByNumber(5);
        MainBlock chain2ForkBlock = chain2.getMainBlockByNumber(5);

        assertArrayEquals(chain1ForkBlock.getHash(), chain2ForkBlock.getHash());

        // merge chain2's main block (6 - 20) to chain1
        for(int i = 6; i < 21; i++) {
            chain1ForkBlock = chain1.getMainBlockByNumber(i);
            chain2ForkBlock = chain2.getMainBlockByNumber(i);

            AccountState as3;
            BlockState bs3;
            if( i < 7) {
                as3 = chain1.getAccountState(chain1ForkBlock.getParentHash(), chain1ForkBlock.getNumber() - 1);
                bs3 = chain1.getBlockState(chain1ForkBlock.getParentHash(), chain1ForkBlock.getNumber() - 1);
            } else {
                as3 = chain1.getAccountState(chain2ForkBlock.getParentHash(), chain2ForkBlock.getNumber() - 1);
                bs3 = chain2.getBlockState(chain2ForkBlock.getParentHash(), chain2ForkBlock.getNumber() - 1);
            }
            // merge
            assertTrue(chain1.importBlock(chain2ForkBlock, as3.clone(), bs3.clone()));
        }

        for(int i = 20; i > 0; i--) {
            chain1ForkBlock = chain1.getMainBlockByNumber(i);
            chain2ForkBlock = chain2.getMainBlockByNumber(i);

            assertArrayEquals("chain1 and chain2 equals at number:" + i, chain1ForkBlock.getHash(), chain2ForkBlock.getHash());
        }

        chain1ForkBlock = chain1.getLatestMainBlock();
        chain2ForkBlock = chain2.getLatestMainBlock();

        assertArrayEquals("chain1 and chain2 latest main block equals", chain1ForkBlock.getHash(), chain2ForkBlock.getHash());

    }
}
