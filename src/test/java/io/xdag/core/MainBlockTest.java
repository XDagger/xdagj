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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.crypto.SampleKeys;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.TimeUtils;

public class MainBlockTest {

    private Config config = new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR);

    private long number = 5;
    private byte[] coinbase = BytesUtils.random(20);
    private byte[] prevHash = BytesUtils.random(32);
    private long timestamp = TimeUtils.currentTimeMillis();
    private byte[] data = BytesUtils.of("data");

    private Transaction tx = new Transaction(Network.DEVNET, TransactionType.TRANSFER, BytesUtils.random(20), ZERO,
            config.getDagSpec().getMinTransactionFee(),
            1, TimeUtils.currentTimeMillis(), BytesUtils.EMPTY_BYTES).sign(SampleKeys.KEY1);
    private TransactionResult res = new TransactionResult();
    private List<Transaction> transactions = Collections.singletonList(tx);
    private List<TransactionResult> results = Collections.singletonList(res);

    private byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(transactions);
    private byte[] resultsRoot = MerkleUtils.computeResultsRoot(results);
    private byte[] hash;

    @Test
    public void testGenesis() {
        MainBlock block = Genesis.load(config.getNodeSpec().getNetwork());
        assertTrue(block.getHeader().validate());
    }

    @Test
    public void testNew() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, data);
        MainBlock block = new MainBlock(header, transactions, results);
        hash = block.getHash();

        testFields(block);
    }

    @Test
    public void testSerialization() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, data);
        MainBlock block = new MainBlock(header, transactions, results);
        hash = block.getHash();

        testFields(MainBlock.fromComponents(block.getEncodedHeader(), block.getEncodedTransactions(),
                block.getEncodedResults(), false));
    }

    private void testFields(MainBlock block) {
        assertArrayEquals(hash, block.getHash());
        assertEquals(number, block.getNumber());
        assertArrayEquals(coinbase, block.getCoinbase());
        assertArrayEquals(prevHash, block.getParentHash());
        assertEquals(timestamp, block.getTimestamp());
        assertArrayEquals(transactionsRoot, block.getTransactionsRoot());
        assertArrayEquals(resultsRoot, block.getResultsRoot());
        assertArrayEquals(data, block.getData());
    }

    @Test
    public void testTransactionIndexes() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, data);
        MainBlock block = new MainBlock(header, transactions, results);

        Pair<byte[], List<Integer>> indexes = block.getEncodedTransactionsAndIndices();
        assertEquals(1, indexes.getRight().size());

        Integer index = indexes.getRight().get(0);
        SimpleDecoder dec = new SimpleDecoder(block.getEncodedTransactions(), index);
        Transaction tx2 = Transaction.fromBytes(dec.readBytes());
        assertArrayEquals(tx.getHash(), tx2.getHash());
    }

    @Test
    public void testValidateTransactions() {
        BlockHeader previousHeader = new BlockHeader(number - 1, coinbase, prevHash, timestamp - 1, transactionsRoot,
                resultsRoot, data);
        BlockHeader header = new BlockHeader(number, coinbase, previousHeader.getHash(), timestamp, transactionsRoot,
                resultsRoot, data);
        MainBlock block = new MainBlock(header, transactions);

        assertTrue(block.validateHeader(header, previousHeader));
        assertTrue(block.validateTransactions(previousHeader, transactions, Network.DEVNET));
        assertTrue(block.validateResults(previousHeader, results));
    }

    @Test
    public void testValidateTransactionsSparse() {
        BlockHeader previousHeader = new BlockHeader(number - 1, coinbase, prevHash, timestamp - 1, transactionsRoot,
                resultsRoot, data);
        BlockHeader header = new BlockHeader(number, coinbase, previousHeader.getHash(), timestamp, transactionsRoot,
                resultsRoot, data);
        MainBlock block = new MainBlock(header, transactions);

        assertTrue(block.validateHeader(header, previousHeader));
        assertTrue(block.validateTransactions(previousHeader, Collections.singleton(transactions.get(0)), transactions,
                Network.DEVNET));
        assertTrue(block.validateResults(previousHeader, results));
    }
}
