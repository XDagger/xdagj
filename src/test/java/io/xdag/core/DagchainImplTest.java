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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.util.Lists;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.xdag.Network;
import io.xdag.TestUtils;
import io.xdag.config.AbstractConfig;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;

public class DagchainImplTest {

    @Rule
    public TemporaryDatabaseRule temporaryDBFactory = new TemporaryDatabaseRule();

    private Config config;
    private DagchainImpl chain;
    private TransactionResult res;

    private final byte[] coinbase = BytesUtils.random(30);
    private byte[] prevHash;

    private final Network network = Network.DEVNET;
    private final KeyPair key = SampleKeys.KEY1;
    private final byte[] from = Keys.toBytesAddress(key);
    private final byte[] to = BytesUtils.random(20);
    private final XAmount value = XAmount.of(20);
    private final XAmount fee = XAmount.of(1);
    private final long nonce = 12345;
    private final byte[] data = BytesUtils.of("test");
    private final long timestamp = TimeUtils.currentTimeMillis() - 60 * 1000;
    private final Transaction tx = new Transaction(network, TransactionType.TRANSFER, to, value, fee, nonce, timestamp,
            data).sign(key);

    private PendingManager pendingMgr;

    @Before
    public void setUp() {
        config = new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR);
        pendingMgr = Mockito.mock(PendingManager.class);
        when(pendingMgr.getPendingTransactions()).thenReturn(Lists.newArrayList(new PendingManager.PendingTransaction(tx, res)));
        chain = new DagchainImpl(config, pendingMgr, temporaryDBFactory);
        res = new TransactionResult();
        prevHash = chain.getLatestMainBlockHash();
    }

    @Test
    public void testGetLatestBlock() {
        assertEquals(0, chain.getLatestMainBlockNumber());
        assertNotNull(chain.getLatestMainBlockHash());
        assertNotNull(chain.getLatestMainBlock());

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertNotEquals(0, chain.getLatestMainBlockNumber());
        assertEquals(newBlock.getNumber(), chain.getLatestMainBlock().getNumber());
    }

    @Test
    public void testGetLatestMainBlockHash() {
        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertEquals(newBlock.getNumber(), chain.getLatestMainBlockNumber());
        assertArrayEquals(newBlock.getHash(), chain.getLatestMainBlockHash());
    }

    @Test
    public void testGetMainBlock() {
        assertEquals(0, chain.getMainBlockByNumber(0).getNumber());
        assertNull(chain.getMainBlockByNumber(1));

        long number = 1;
        MainBlock newBlock = createMainBlock(number);

        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertEquals(number, chain.getMainBlockByNumber(number).getNumber());
        assertEquals(number, chain.getMainBlockByHash(newBlock.getHash()).getNumber());
    }

    @Test
    public void testHasMainBlock() {
        assertFalse(chain.hasMainBlock(-1));
        assertTrue(chain.hasMainBlock(0));
        assertFalse(chain.hasMainBlock(1));
    }

    @Test
    public void testGetBlockNumber() {
        long number = 1;
        MainBlock newBlock = createMainBlock(number);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertEquals(number, chain.getMainBlockNumber(newBlock.getHash()));
    }

    @Test
    public void testGetGenesis() {
        assertArrayEquals(Genesis.load(network).getHash(), chain.getGenesis().getHash());
    }

    @Test
    public void testGetBlockHeader() {
        assertArrayEquals(Genesis.load(network).getHash(), chain.getBlockHeader(0).getHash());

        long number = 1;
        MainBlock newBlock = createMainBlock(number);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertArrayEquals(newBlock.getHash(), chain.getBlockHeader(1).getHash());
        assertEquals(newBlock.getNumber(), chain.getBlockHeader(newBlock.getHash()).getNumber());
    }

    @Test
    public void testGetTransaction() {
        assertNull(chain.getTransaction(tx.getHash()));

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        Transaction t = chain.getTransaction(tx.getHash());
        assertNotNull(t);
        assertArrayEquals(from, t.getFrom());
        assertArrayEquals(to, t.getTo());
        assertArrayEquals(data, t.getData());
        assertEquals(value, t.getValue());
        assertEquals(nonce, t.getNonce());
        assertEquals(timestamp, t.getTimestamp());
    }

    @Test
    public void testHasTransaction() {
        assertFalse(chain.hasTransaction(tx.getHash()));

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertTrue(chain.hasTransaction(tx.getHash()));
    }

    @Test
    public void testGetTransactionResult() {
        assertNull(chain.getTransaction(tx.getHash()));

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        TransactionResult r = chain.getTransactionResult(tx.getHash());
        assertArrayEquals(res.toBytes(), r.toBytes());
    }

    @Test
    public void testGetTransactionBlockNumber() {
        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();
        assertEquals(newBlock.getNumber(), chain.getTransactionBlockNumber(tx.getHash()));
    }

    @Test
    public void testGetCoinbaseTransactionBlockNumber()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        for (int i = 1; i <= 10; i++) {
            byte[] coinbase = Keys.toBytesAddress(Keys.createEcKeyPair());
            MainBlock newBlock = createMainBlock(i, coinbase, BytesUtils.EMPTY_BYTES, Collections.emptyList(),
                    Collections.emptyList());
            BlockState bsTrack = chain.getLatestBlockState().clone();
            chain.addMainBlock(newBlock, bsTrack);
            bsTrack.commit();
            List<Transaction> transactions = chain.getTransactions(coinbase, 0, 1);
            assertEquals(1, transactions.size());
            assertEquals(newBlock.getNumber(), transactions.get(0).getNonce());
            assertEquals(TransactionType.COINBASE, transactions.get(0).getType());
            assertEquals(newBlock.getNumber(), chain.getTransactionBlockNumber(transactions.get(0).getHash()));
        }
    }

    @Test
    public void testGetTransactionCount() {
        assertNull(chain.getTransaction(tx.getHash()));

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        assertEquals(1, chain.getTransactionCount(tx.getFrom()));
    }

    @Test
    public void testGetAccountTransactions() {
        assertNull(chain.getTransaction(tx.getHash()));

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        List<Transaction> txs = chain.getTransactions(tx.getFrom(), 0, 100);
        assertEquals(1, txs.size());
        assertArrayEquals(tx.toBytes(), txs.get(0).toBytes());
    }

    @Test
    public void testSerialization() {
        MainBlock block1 = createMainBlock(1);

        MainBlock block2 = MainBlock.fromComponents(block1.getEncodedHeader(), block1.getEncodedTransactions(), block1.getEncodedResults(), false);
        assertArrayEquals(block1.getHash(), block2.getHash());
        assertArrayEquals(block1.getCoinbase(), block2.getCoinbase());
        assertArrayEquals(block1.getParentHash(), block2.getParentHash());
        assertEquals(block1.getNumber(), block2.getNumber());

        assertEquals(block1.getTransactions().size(), block2.getTransactions().size());
    }

    @Test
    public void testGetTransactions() {
        MainBlock block = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(block, bsTrack);
        bsTrack.commit();

        List<Transaction> list = chain.getTransactions(from, 0, 1024);
        assertEquals(1, list.size());
        assertArrayEquals(tx.getHash(), list.get(0).getHash());

        list = chain.getTransactions(to, 0, 1024);
        assertEquals(1, list.size());
        assertArrayEquals(tx.getHash(), list.get(0).getHash());
    }

    @Test
    public void testGetTransactionsSelfTx() {
        Transaction selfTx = new Transaction(network, TransactionType.TRANSFER, Keys.toBytesAddress(key), value, fee, nonce,
                timestamp, data).sign(key);
        MainBlock block = createMainBlock(
                1,
                Collections.singletonList(selfTx),
                Collections.singletonList(res));
        PendingManager.PendingTransaction pt = new PendingManager.PendingTransaction(selfTx, res);
        when(pendingMgr.getPendingTransactions()).thenReturn(Lists.newArrayList(pt));

        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(block, bsTrack);
        bsTrack.commit();

        // there should be only 1 transaction added into index database
        List<Transaction> list = chain.getTransactions(Keys.toBytesAddress(key), 0, 1024);
        assertEquals(1, list.size());
        assertArrayEquals(selfTx.getHash(), list.get(0).getHash());
    }

    @Test
    public void testForkActivated() {
        final Fork fork = Fork.APOLLO_FORK;
        for (long i = 1; i <= fork.blocksToCheck(); i++) {
            BlockState bsTrack = chain.getLatestBlockState().clone();
            chain.addMainBlock(
                    createMainBlock(i, coinbase, new BlockHeaderData(ForkSignalSet.of(fork)).toBytes(),
                            Collections.singletonList(tx), Collections.singletonList(res)), bsTrack);
            bsTrack.commit();

            // post-import check
            if (i < fork.blocksRequired()) {
                assertFalse(chain.isForkActivated(fork));
            } else {
                assertTrue(chain.isForkActivated(fork));
            }
        }
    }

    @Test
    public void testForkCompatibility() {
        Fork fork = Fork.APOLLO_FORK;
        MainBlock block = createMainBlock(1, coinbase, new BlockHeaderData(ForkSignalSet.of(fork)).toBytes(),
                Collections.singletonList(tx), Collections.singletonList(res));
        TestUtils.setInternalState(config, "forkApolloEnabled", false, AbstractConfig.class);
        chain = new DagchainImpl(config, pendingMgr, temporaryDBFactory);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(block, bsTrack);
    }

    private MainBlock createMainBlock(long number) {
        return createMainBlock(number, Collections.singletonList(tx), Collections.singletonList(res));
    }

    private MainBlock createMainBlock(long number, List<Transaction> transactions, List<TransactionResult> results) {
        return createMainBlock(number, coinbase, BytesUtils.EMPTY_BYTES, transactions, results);
    }

    private MainBlock createMainBlock(long number, byte[] coinbase, byte[] data, List<Transaction> transactions,
            List<TransactionResult> results) {
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(transactions);
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(results);
        long timestamp = TimeUtils.currentTimeMillis();

        BlockHeader header = BlockUtils.createProofOfWorkHeader(prevHash, number, coinbase, timestamp, transactionsRoot, resultsRoot, 0L, data);
        List<Bytes32> txHashs = new ArrayList<>();
        transactions.forEach(t-> txHashs.add(Bytes32.wrap(t.getHash())));
        return new MainBlock(header, transactions, txHashs, results);
    }
}
