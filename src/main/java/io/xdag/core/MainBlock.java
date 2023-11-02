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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import com.google.common.collect.Lists;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class MainBlock {

    /**
     * The block header.
     */
    private BlockHeader header;

    /**
     * The transactions hash list.
     */
    private List<Bytes32> txHashs;

    private List<TransactionResult> results;

    private List<Transaction> transactions;


    public MainBlock(BlockHeader header, List<Transaction> transactions, List<TransactionResult> results) {
        this.header = header;
        this.transactions = transactions;
        this.results = results;

        this.txHashs = Lists.newArrayList();
        transactions.forEach( tx -> txHashs.add(Bytes32.wrap(tx.getHash())));
    }

    public MainBlock(BlockHeader header, List<Transaction> transactions) {
        this(header, transactions, Lists.newArrayList());
    }

    private MainBlock(BlockHeader header, List<Bytes32> txHashs, List<TransactionResult> results, boolean noTransactions) {
        this.header = header;
        this.txHashs = txHashs;
        this.results = results;
        this.transactions = Lists.newArrayList();
    }

    public MainBlock(BlockHeader header, List<Transaction> transactions, List<Bytes32> txHashs, List<TransactionResult> results) {
        this.header = header;
        this.transactions = transactions;
        this.txHashs = txHashs;
        this.results = results;
    }

    /**
     * Validates block header.
     */
    public boolean validateHeader(BlockHeader header, BlockHeader parentHeader) {
        if (header == null) {
            log.warn("Header was null.");
            return false;
        }

        if (!header.validate()) {
            log.warn("Header was invalid.");
            return false;
        }

        if (header.getNumber() != parentHeader.getNumber() + 1) {
            log.warn("Header number was not one greater than previous block.");
            return false;
        }

        if (!Arrays.equals(header.getParentHash(), parentHeader.getHash())) {
            log.warn("Header parent hash was not equal to previous block hash.");
            return false;
        }

        if (header.getTimestamp() <= parentHeader.getTimestamp()) {
            log.warn("Header timestamp was before previous block.");
            return false;
        }

        if (Arrays.equals(header.getCoinbase(), Constants.COINBASE_ADDRESS)) {
            log.warn("Header coinbase was a reserved address");
            return false;
        }

        // check Proof Of Work
        if(!header.checkProofOfWork()) {
            BigInteger target = header.getDifficultyTargetAsInteger();
            log.warn("Header Proof Of Work is invalid, Nonce {}, , Hash is higher than target {} vs {}", header.getNonce(), Bytes.wrap(header.getHash()), target.toString(16));
            return false;
        }

        return true;
    }

    /**
     * Validates transactions in parallel.
     */
    public boolean validateTransactions(BlockHeader header, List<Transaction> transactions, Network network) {
        return validateTransactions(header, transactions, transactions, network);
    }

    /**
     * Validates transactions in parallel, only doing those that have not already
     * been calculated.
     *
     * @param header
     *            block header
     * @param unvalidatedTransactions
     *            transactions needing validating
     * @param allTransactions
     *            all transactions within the block
     * @param network
     *            network
     */
    public boolean validateTransactions(BlockHeader header, Collection<Transaction> unvalidatedTransactions,
            List<Transaction> allTransactions, Network network) {

        // validate transactions
        if (!unvalidatedTransactions.parallelStream().allMatch(tx -> tx.validate(network))) {
            return false;
        }

        // validate transactions root
        byte[] root = MerkleUtils.computeTransactionsRoot(allTransactions);
        return Arrays.equals(root, header.getTransactionsRoot());
    }

    /**
     * Validates results.
     */
    public boolean validateResults(BlockHeader header, List<TransactionResult> results) {
        long number = header.getNumber();

        // validate results
        for (int i = 0; i < results.size(); i++) {
            TransactionResult result = results.get(i);
            if (result.getCode().isRejected()) {
                log.warn("Transaction #{} in block #{} rejected: code = {}", i, number, result.getCode());
                return false;
            }
        }

        // validate results root
        byte[] root = MerkleUtils.computeResultsRoot(results);
        boolean rootMatches = Arrays.equals(root, header.getResultsRoot());
        if (!rootMatches) {
            log.warn("Transaction result root doesn't match in block #{}", number);
        }

        return rootMatches;
    }

    public static XAmount getBlockReward(MainBlock block, Config config) {
        XAmount txsReward = block.getTransactions().stream().map(Transaction::getFee).reduce(ZERO, XAmount::sum);

        return config.getDagSpec().getMainBlockReward(block.getNumber()).add(txsReward);
    }

    /**
     * Returns the block hash.
     */
    public byte[] getHash() {
        return header.getHash();
    }

    /**
     * Returns the block number.
     */
    public long getNumber() {
        return header.getNumber();
    }

    /**
     * Returns the coinbase
     */
    public byte[] getCoinbase() {
        return header.getCoinbase();
    }

    /**
     * Returns the difficulty target
     */
    public long getDifficultyTarget() {
        return header.getDifficultyTarget();
    }

    /**
     * Returns the nonce
     */
    public long getNonce() {
        return header.getNonce();
    }

    /**
     * Returns the hash of the parent block
     */
    public byte[] getParentHash() {
        return header.getParentHash();
    }

    /**
     * Returns the block timestamp.
     */
    public long getTimestamp() {
        return header.getTimestamp();
    }

    /**
     * Returns the merkle root of all transactions.
     */
    public byte[] getTransactionsRoot() {
        return header.getTransactionsRoot();
    }

    /**
     * Returns the merkle root of all transaction results.
     */
    public byte[] getResultsRoot() {
        return header.getResultsRoot();
    }

    /**
     * Returns the extra data.
     */
    public byte[] getData() {
        return header.getData();
    }

    /**
     * Serializes the block header into byte array.
     */
    public byte[] getEncodedHeader() {
        return header.toBytes();
    }

    /**
     * Serializes the block transactions into byte array.
     */
    public byte[] getEncodedTransactions() {
        return getEncodedTransactionsAndIndices().getLeft();
    }

    public Pair<byte[], List<Integer>> getEncodedTransactionsAndIndices() {
        List<Integer> indices = new ArrayList<>();

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeInt(transactions.size());
        for (Transaction transaction : transactions) {
            int index = enc.getWriteIndex();
            enc.writeBytes(transaction.toBytes());
            indices.add(index);
        }

        return Pair.of(enc.toBytes(), indices);
    }

    /**
     * Serializes the block transactions hash list into byte array.
     */
    public byte[] getEncodedTxHashs() {
        return getEncodedTxHashsAndIndices().getLeft();
    }

    public Pair<byte[], List<Integer>> getEncodedTxHashsAndIndices() {
        List<Integer> indices = new ArrayList<>();

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeInt(txHashs.size());
        for (Bytes32 hash : txHashs) {
            int index = enc.getWriteIndex();
            enc.writeBytes(hash.toArray());
            indices.add(index);
        }

        return Pair.of(enc.toBytes(), indices);
    }

    /**
     * Serializes the block transactions results into byte array.
     */
    public byte[] getEncodedResults() {
        return getEncodedResultsAndIndices().getLeft();
    }

    public Pair<byte[], List<Integer>> getEncodedResultsAndIndices() {
        List<Integer> indices = new ArrayList<>();

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeInt(results.size());
        for (TransactionResult result : results) {
            int index = enc.getWriteIndex();
            enc.writeBytes(result.toBytes());
            indices.add(index);
        }

        return Pair.of(enc.toBytes(), indices);
    }

    public static MainBlock fromComponents(byte[] h, byte[] txs, byte[] r, boolean noTransactions) {
        if (h == null) {
            throw new IllegalArgumentException("Block header can't be null");
        }

        if (txs == null) {
            throw new IllegalArgumentException("Block tx hash list can't be null");
        }

        BlockHeader header = BlockHeader.fromBytes(h);
        List<Transaction> transactions = Lists.newArrayList();
        List<Bytes32> txHashList = Lists.newArrayList();

        SimpleDecoder dec = new SimpleDecoder(txs);
        int n = dec.readInt();
        for (int i = 0; i < n; i++) {
            if(!noTransactions) {
                Transaction tx = Transaction.fromBytes(dec.readBytes());
                transactions.add(tx);
                txHashList.add(Bytes32.wrap(tx.getHash()));
            } else {
                txHashList.add(Bytes32.wrap(dec.readBytes()));
            }
        }

        List<TransactionResult> results = new ArrayList<>();
        if (r != null) {
            dec = new SimpleDecoder(r);
            n = dec.readInt();
            for (int i = 0; i < n; i++) {
                results.add(TransactionResult.fromBytes(dec.readBytes()));
            }
        }

        MainBlock block;
        if(!noTransactions) {
            block = new MainBlock(header, transactions, results);
        } else {
            block = new MainBlock(header, txHashList, results, true);
        }
        return block;
    }

    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(getEncodedHeader());
        //enc.writeBytes(getEncodedTransactions());
        enc.writeBytes(getEncodedTxHashs());
        enc.writeBytes(getEncodedResults());

        return enc.toBytes();
    }

    public static MainBlock fromBytes(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        byte[] header = dec.readBytes();
//        byte[] transactions = dec.readBytes();
        byte[] txHashs = dec.readBytes();
        byte[] results = dec.readBytes();

        return MainBlock.fromComponents(header, txHashs, results, true);
    }

    /**
     * Get block size in bytes
     */
    public int size() {
        return toBytes().length;
    }

    @Override
    public String toString() {
        return "MainBlock [number = " + getNumber() + ", hash = " + Bytes.wrap(getHash()).toHexString() + ", parent hash = " +Bytes.wrap(getParentHash()).toHexString() + ", # hashs = " + txHashs.size() + " ]";
    }

}
