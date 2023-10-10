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

import static io.xdag.core.XUnit.MILLI_XDAG;
import static io.xdag.core.XUnit.XDAG;
import static io.xdag.core.PendingManager.ALLOWED_TIME_DRIFT;
import static io.xdag.core.TransactionResult.Code.INVALID_TIMESTAMP;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

import io.xdag.KernelMock;
import io.xdag.Network;
import io.xdag.config.Constants;
import io.xdag.core.BlockHeader;
import io.xdag.core.DagchainImpl;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.db.LeveldbDatabase;
import io.xdag.core.state.AccountState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.net.ChannelManager;
import io.xdag.rules.KernelRule;
import io.xdag.utils.ArrayUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class PendingManagerTest {

    private static KernelMock kernel;
    private static PendingManager pendingMgr;
    private static AccountState accountState;

    private static final KeyPair key = SampleKeys.KEY1;
    private static Network network;
    private static final TransactionType type = TransactionType.TRANSFER;
    private static final byte[] from = Keys.toBytesAddress(key);
    private static final byte[] to = Keys.toBytesAddress(SampleKeys.KEY2);
    private static final XAmount value = XAmount.of(1, MILLI_XDAG);
    private static XAmount fee;

    @ClassRule
    public static KernelRule kernelRule = new KernelRule(8001);

    @BeforeClass
    public static void setUp() {
        kernel = kernelRule.getKernel();
        pendingMgr = Mockito.mock(PendingManager.class);

        kernel.setDagchain(
                new DagchainImpl(kernel.getConfig(), pendingMgr, new LeveldbDatabase.LeveldbFactory(kernel.getConfig().chainDir())));
        kernel.setChannelManager(new ChannelManager(kernel));

        accountState = kernel.getDagchain().getAccountState();
        accountState.adjustAvailable(from, XAmount.of(10000, XDAG));

        network = kernel.getConfig().getNodeSpec().getNetwork();
        fee = kernel.getConfig().getDagSpec().getMinTransactionFee();
    }

    @Before
    public void start() {
        pendingMgr = new PendingManager(kernel);
        pendingMgr.start();
    }

    @Test
    public void testGetTransaction() throws InterruptedException {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx);

        Thread.sleep(100);
        assertEquals(1, pendingMgr.getPendingTransactions().size());
    }

    @Test
    public void testAddTransaction() throws InterruptedException {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx);
        Transaction tx2 = new Transaction(network, type, to, value, fee, nonce + 128, now, BytesUtils.EMPTY_BYTES)
                .sign(key);
        pendingMgr.addTransaction(tx2);

        Thread.sleep(100);
        assertEquals(1, pendingMgr.getPendingTransactions().size());
    }

    @Test
    public void testAddTransactionSyncErrorInvalidFormat() {
        Transaction tx = new Transaction(network, type, to, value, fee, 0, 0, BytesUtils.EMPTY_BYTES).sign(key);
        PendingManager.ProcessingResult result = pendingMgr.addTransactionSync(tx);
        assertEquals(0, pendingMgr.getPendingTransactions().size());
        assertNotNull(result.error);
        assertEquals(TransactionResult.Code.INVALID_FORMAT, result.error);
    }

    @Test
    public void testAddTransactionSyncErrorDuplicatedHash() {
        Transaction tx = new Transaction(network, type, to, value, fee, 0, TimeUtils.currentTimeMillis(),
                BytesUtils.EMPTY_BYTES)
                        .sign(key);

        kernel.setDagchain(spy(kernel.getDagchain()));
        doReturn(true).when(kernel.getDagchain()).hasTransaction(tx.getHash());

        PendingManager.ProcessingResult result = pendingMgr.addTransactionSync(tx);
        assertEquals(0, pendingMgr.getPendingTransactions().size());
        assertNotNull(result.error);
        assertEquals(TransactionResult.Code.INVALID, result.error);

        Mockito.reset(kernel.getDagchain());
    }

    @Test
    public void testAddTransactionSyncInvalidRecipient() {
        Transaction tx = new Transaction(network, type, Keys.toBytesAddress(Constants.COINBASE_KEY), value, fee, 0,
                TimeUtils.currentTimeMillis(), BytesUtils.EMPTY_BYTES)
                        .sign(key);

        PendingManager.ProcessingResult result = pendingMgr.addTransactionSync(tx);
        assertEquals(0, pendingMgr.getPendingTransactions().size());
        assertNotNull(result.error);
        assertEquals(TransactionResult.Code.INVALID_FORMAT, result.error);
    }

    @Test
    public void testNonceJump() throws InterruptedException {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx3 = new Transaction(network, type, to, value, fee, nonce + 2, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx3);
        Transaction tx2 = new Transaction(network, type, to, value, fee, nonce + 1, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx2);

        Thread.sleep(100);
        assertEquals(0, pendingMgr.getPendingTransactions().size());

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx);

        Thread.sleep(100);
        assertEquals(3, pendingMgr.getPendingTransactions().size());
    }

    @Test
    public void testNonceJumpTimestampError() throws InterruptedException {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx3 = new Transaction(network, type, to, value, fee, nonce + 2,
                now - TimeUnit.HOURS.toMillis(2) + TimeUnit.SECONDS.toMillis(1),
                BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx3);
        Transaction tx2 = new Transaction(network, type, to, value, fee, nonce + 1, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx2);

        TimeUnit.SECONDS.sleep(1);
        assertEquals(0, pendingMgr.getPendingTransactions().size());

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx);

        TimeUnit.SECONDS.sleep(1);
        List<PendingManager.PendingTransaction> txs = pendingMgr.getPendingTransactions();
        assertEquals(3, txs.size());
    }

    @Test
    public void testTimestampError() {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx3 = new Transaction(network, type, to, value, fee, nonce, now - ALLOWED_TIME_DRIFT - 1,
                BytesUtils.EMPTY_BYTES).sign(key);
        PendingManager.ProcessingResult result = pendingMgr.addTransactionSync(tx3);
        assertEquals(INVALID_TIMESTAMP, result.error);
    }

    @Test
    public void testHighVolumeTransaction() {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        int[] perm = ArrayUtils.permutation(5000);
        for (int p : perm) {
            Transaction tx = new Transaction(network, type, to, value, fee, nonce + p, now, BytesUtils.EMPTY_BYTES)
                    .sign(key);
            pendingMgr.addTransaction(tx);
        }

        await().until(() -> pendingMgr.getPendingTransactions().size() == perm.length);
    }

    @Test
    public void testNewBlock() throws InterruptedException {
        long now = TimeUtils.currentTimeMillis();
        long nonce = accountState.getAccount(from).getNonce();

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx);
        Transaction tx2 = new Transaction(network, type, to, value, fee, nonce + 1, now, BytesUtils.EMPTY_BYTES).sign(key);
        // pendingMgr.addTransaction(tx3);

        Thread.sleep(100);
        assertEquals(1, pendingMgr.getPendingTransactions().size());

        long number = 1;
        byte[] coinbase = BytesUtils.random(20);
        byte[] prevHash = BytesUtils.random(20);
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] transactionsRoot = BytesUtils.random(32);
        byte[] resultsRoot = BytesUtils.random(32);
        byte[] data = {};
        List<Transaction> transactions = Arrays.asList(tx, tx2);
        List<TransactionResult> results = Arrays.asList(new TransactionResult(), new TransactionResult());
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, data);
        List<Bytes32> txHashs = new ArrayList<>();
        transactions.forEach(t-> txHashs.add(Bytes32.wrap(t.getHash())));
        MainBlock block = new MainBlock(header, transactions, txHashs, results);
        kernel.getDagchain().getAccountState().increaseNonce(from);
        kernel.getDagchain().getAccountState().increaseNonce(from);
        pendingMgr.onMainBlockAdded(block);

        Transaction tx3 = new Transaction(network, type, to, value, fee, nonce + 2, now, BytesUtils.EMPTY_BYTES).sign(key);
        pendingMgr.addTransaction(tx3);

        Thread.sleep(100);
        assertArrayEquals(tx3.getHash(), pendingMgr.getPendingTransactions().get(0).transaction.getHash());
    }

    @After
    public void stop() {
        pendingMgr.stop();
    }
}
