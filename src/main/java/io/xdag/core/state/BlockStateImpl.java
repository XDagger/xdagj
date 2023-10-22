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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.xdag.core.BlockHeader;
import io.xdag.core.MainBlock;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.db.Database;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleDecoder;

public class BlockStateImpl implements BlockState {

    protected static final byte TYPE_LATEST_BLOCK_NUMBER = 0x00;
    protected static final byte TYPE_ACTIVATED_FORKS = 0x06;
    protected static final byte TYPE_DATABASE_VERSION = (byte) 0xff;

    protected static final byte TYPE_BLOCK_NUMBER_BY_HASH = 0x10;
    protected static final byte TYPE_BLOCK_COINBASE_BY_NUMBER = 0x11;

    protected static final byte TYPE_TRANSACTION_INDEX_BY_HASH = 0x20;
    protected static final byte TYPE_TRANSACTION_COUNT_BY_ADDRESS = 0x21;
    protected static final byte TYPE_TRANSACTION_HASH_BY_ADDRESS_AND_INDEX = 0x21;

    protected static final byte TYPE_BLOCK_HEADER_BY_NUMBER = 0x30;
    protected static final byte TYPE_BLOCK_TRANSACTIONS_HASH_BY_NUMBER = 0x31;
    protected static final byte TYPE_BLOCK_TRANSACTIONS_BY_NUMBER = 0x32;
    protected static final byte TYPE_BLOCK_RESULTS_BY_NUMBER = 0x33;

    protected Database blockDB;
    protected Database indexDB;

    protected final Map<ByteArray, byte[]> blockUpdates = new ConcurrentHashMap<>();
    protected final Map<ByteArray, byte[]> indexUpdates = new ConcurrentHashMap<>();

    public BlockStateImpl(Database indexDB, Database blockDB) {
        this.indexDB = indexDB;
        this.blockDB = blockDB;
    }

    @Override
    public int getDatabaseVersion() {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_DATABASE_VERSION));
        byte[] v;
        if(indexUpdates.containsKey(k)) {
            v = indexUpdates.get(k);
        } else {
            v = indexDB.get(k.getData());
        }
        return v == null ? 0 : BytesUtils.toInt(v);
    }

    public void putDatabaseVersion(int version) {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_DATABASE_VERSION));
        byte[] v = BytesUtils.of(version);
        indexUpdates.put(k, v);
    }

    @Override
    public void addMainBlock(MainBlock mainBlock) {
        long number = mainBlock.getNumber();
        byte[] hash = mainBlock.getHash();

        blockUpdates.put(ByteArray.of(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number))), mainBlock.getEncodedHeader());
        blockUpdates.put(ByteArray.of(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_HASH_BY_NUMBER, BytesUtils.of(number))), mainBlock.getEncodedTxHashs());
        blockUpdates.put(ByteArray.of(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(number))), mainBlock.getEncodedTransactions());
        blockUpdates.put(ByteArray.of(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(number))), mainBlock.getEncodedResults());

        indexUpdates.put(ByteArray.of(BytesUtils.merge(TYPE_BLOCK_NUMBER_BY_HASH, hash)), BytesUtils.of(number));
    }

    @Override
    public BlockHeader getBlockHeader(long number) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)));
        if(blockUpdates.containsKey(k)) {
            byte[] v = blockUpdates.get(k);
            return v == null ? null : BlockHeader.fromBytes(v);
        } else {
            byte[] header = blockDB.get(k.getData());
            return (header == null) ? null : BlockHeader.fromBytes(header);
        }
    }

    @Override
    public boolean hasMainBlock(long number) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)));
        if (blockUpdates.containsKey(k)) {
            return true;
        } else {
            byte[] v = blockDB.get(k.getData());
            return v != null;
        }
    }

    @Override
    public long getMainBlockNumber(byte[] hash) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_NUMBER_BY_HASH, hash));
        if(indexUpdates.containsKey(k)) {
            byte[] v = indexUpdates.get(k);
            return v == null ? -1 : BytesUtils.toLong(v);
        } else {
            byte[] number = indexDB.get(k.getData());
            return (number == null) ? -1 : BytesUtils.toLong(number);
        }
    }

    @Override
    public MainBlock getMainBlockByNumber(long number) {
        return getBlock(number, false);
    }

    @Override
    public MainBlock getMainBlockByHash(byte[] hash) {
        long number = getMainBlockNumber(hash);
        return (number == -1) ? null : getMainBlockByNumber(number);
    }

    @Override
    public BlockHeader getBlockHeader(byte[] hash) {
        long number = getMainBlockNumber(hash);
        return (number == -1) ? null : getBlockHeader(number);
    }

    @Override
    public long getLatestMainBlockNumber() {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_LATEST_BLOCK_NUMBER));
        byte[] v;
        if(indexUpdates.containsKey(k)) {
            v = indexUpdates.get(k);
        } else {
            v = indexDB.get(k.getData());
        }
        return v == null ? -1 : BytesUtils.toLong(v);
    }

    @Override
    public MainBlock getBlock(long number, boolean skipResults) {
        byte[] header;
        byte[] transactions;
        byte[] results;

        ByteArray headerKey = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)));
        ByteArray transactionsKey = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(number)));
        ByteArray resultsKey = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(number)));

        if (blockUpdates.containsKey(headerKey) && blockUpdates.containsKey(transactionsKey) && blockUpdates.containsKey(resultsKey)) {
            header = blockUpdates.get(headerKey);
            transactions = blockUpdates.get(transactionsKey);
            results = skipResults ? null : blockUpdates.get(resultsKey);
            return  MainBlock.fromComponents(header, transactions, results, false);
        } else {
            header = blockDB.get(headerKey.getData());
            transactions = blockDB.get(transactionsKey.getData());
            results = skipResults ? null : blockDB.get(resultsKey.getData());
            return (header == null) ? null : MainBlock.fromComponents(header, transactions, results, false);
        }
    }

    @Override
    public Transaction getTransaction(byte[] hash) {
        ByteArray indexKey = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));

        byte[] bytes;
        if(indexUpdates.containsKey(indexKey)) {
            bytes = indexUpdates.get(indexKey);
            if (bytes != null) {
                // coinbase transaction
                if (bytes.length > 64) {
                    return Transaction.fromBytes(bytes);
                }

                TransactionIndex index = TransactionIndex.fromBytes(bytes);
                ByteArray blockKey = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(index.blockNumber)));
                byte[] transactions = blockUpdates.get(blockKey);
                SimpleDecoder dec = new SimpleDecoder(transactions, index.transactionOffset);
                return Transaction.fromBytes(dec.readBytes());
            }
        } else {
            bytes = indexDB.get(indexKey.getData());
            if (bytes != null) {
                // coinbase transaction
                if (bytes.length > 64) {
                    return Transaction.fromBytes(bytes);
                }

                TransactionIndex index = TransactionIndex.fromBytes(bytes);
                byte[] transactions = blockDB.get(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(index.blockNumber)));
                SimpleDecoder dec = new SimpleDecoder(transactions, index.transactionOffset);
                return Transaction.fromBytes(dec.readBytes());
            }
        }

        return null;
    }

    @Override
    public Transaction getCoinbaseTransaction(long blockNumber) {
        return blockNumber == 0 ? null : getTransaction(getBlockCoinBaseByNumber(blockNumber));
    }

    @Override
    public void addBlockCoinbaseByNumber(long number, byte[] hash) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_COINBASE_BY_NUMBER, BytesUtils.of(number)));
        indexUpdates.put(k, hash);
    }

    public byte[] getBlockCoinBaseByNumber(long number) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_COINBASE_BY_NUMBER, BytesUtils.of(number)));
        if(indexUpdates.containsKey(k)) {
            return indexUpdates.get(k);
        } else {
            return indexDB.get(k.getData());
        }
    }

    @Override
    public boolean hasTransaction(final byte[] hash) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
        byte[] v;
        if(indexUpdates.containsKey(k)) {
            v = indexUpdates.get(k);
        } else {
            v = indexDB.get(k.getData());
        }
        return v != null;
    }

    @Override
    public int getTransactionCount(byte[] address) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_COUNT_BY_ADDRESS, address));
        byte[] v;
        if(indexUpdates.containsKey(k)) {
            v = indexUpdates.get(k);
        } else {
            v = indexDB.get(k.getData());
        }
        return (v == null) ? 0 : BytesUtils.toInt(v);
    }

    @Override
    public List<Transaction> getTransactions(byte[] address, int from, int to) {
        List<Transaction> list = new ArrayList<>();

        int total = getTransactionCount(address);
        for (int i = from; i < total && i < to; i++) {
            byte[] value = getNthTransactionIndexValue(address, i);
            list.add(getTransaction(value));
        }

        return list;
    }

    public byte[] getNthTransactionIndexValue(byte[] address, int i) {
        ByteArray k = ByteArray.of(getNthTransactionIndexKey(address, i));
        if(indexUpdates.containsKey(k)) {
            return indexUpdates.get(k);
        } else {
            return indexDB.get(k.getData());
        }
    }

    /**
     * Sets the total number of transaction of an account
     */
    @Override
    public void setTransactionCount(byte[] address, int total) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_COUNT_BY_ADDRESS, address));
        indexUpdates.put(k, BytesUtils.of(total));
    }

    /**
     * Adds a transaction to an account.
     */
    @Override
    public void addTransactionToAccount(Transaction tx, byte[] address) {
        int total = getTransactionCount(address);
        ByteArray k = ByteArray.of(getNthTransactionIndexKey(address, total));
        indexUpdates.put(k, tx.getHash());
        setTransactionCount(address, total + 1);
    }

    @Override
    public void addTransactionIndex(byte[] hash, byte[] bytes) {
        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
        indexUpdates.put(k, bytes);
    }

    /**
     * Returns the N-th transaction index key of an account.
     */
    public byte[] getNthTransactionIndexKey(byte[] address, int n) {
        return BytesUtils.merge(BytesUtils.of(TYPE_TRANSACTION_HASH_BY_ADDRESS_AND_INDEX), address, BytesUtils.of(n));
    }

    @Override
    public TransactionResult getTransactionResult(byte[] hash) {
        ByteArray indexKey = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));

        byte[] bytes;
        if(indexUpdates.containsKey(indexKey)) {
            bytes = indexUpdates.get(indexKey);
            if (bytes != null) {
                // coinbase transaction
                if (bytes.length > 64) {
                    return new TransactionResult();
                }

                TransactionIndex index = TransactionIndex.fromBytes(bytes);
                ByteArray blockKey = ByteArray.of(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(index.blockNumber)));
                byte[] results = blockUpdates.get(blockKey);
                SimpleDecoder dec = new SimpleDecoder(results, index.resultOffset);
                return TransactionResult.fromBytes(dec.readBytes());
            }
        } else {
            bytes = indexDB.get(indexKey.getData());
            if (bytes != null) {
                // coinbase transaction
                if (bytes.length > 64) {
                    return new TransactionResult();
                }

                TransactionIndex index = TransactionIndex.fromBytes(bytes);
                byte[] results = blockDB.get(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(index.blockNumber)));
                SimpleDecoder dec = new SimpleDecoder(results, index.resultOffset);
                return TransactionResult.fromBytes(dec.readBytes());
            }
        }

        return null;
    }

    @Override
    public long getTransactionBlockNumber(byte[] hash) {
        Transaction tx = getTransaction(hash);
        if (tx.getType() == TransactionType.COINBASE) {
            return tx.getNonce();
        }

        ByteArray k = ByteArray.of(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
        byte[] bytes;
        if(indexUpdates.containsKey(k)) {
            bytes = indexUpdates.get(k);
            if (bytes != null) {
                SimpleDecoder dec = new SimpleDecoder(bytes);
                return dec.readLong();
            }
        } else {
            bytes = indexDB.get(k.getData());
            if (bytes != null) {
                SimpleDecoder dec = new SimpleDecoder(bytes);
                return dec.readLong();
            }
        }

        return -1;
    }

    @Override
    public void addLatestBlockNumber(long number) {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_LATEST_BLOCK_NUMBER));
        indexUpdates.put(k, BytesUtils.of(number));
    }

    @Override
    public void addActivatedForks(byte[] bytes) {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_ACTIVATED_FORKS));
        indexUpdates.put(k, bytes);
    }

    @Override
    public byte[] getActivatedForks() {
        ByteArray k = ByteArray.of(BytesUtils.of(TYPE_ACTIVATED_FORKS));
        if(indexUpdates.containsKey(k)) {
            return indexUpdates.get(k);
        } else {
            return indexDB.get(k.getData());
        }
    }

    @Override
    public void commit() {
        synchronized (blockUpdates) {
            for (Map.Entry<ByteArray, byte[]> entry : blockUpdates.entrySet()) {
                if (entry.getValue() == null) {
                    blockDB.delete(entry.getKey().getData());
                } else {
                    blockDB.put(entry.getKey().getData(), entry.getValue());
                }
            }

            for (Map.Entry<ByteArray, byte[]> entry : indexUpdates.entrySet()) {
                if (entry.getValue() == null) {
                    indexDB.delete(entry.getKey().getData());
                } else {
                    indexDB.put(entry.getKey().getData(), entry.getValue());
                }
            }

            blockUpdates.clear();
            indexUpdates.clear();
        }
    }

    @Override
    public Map<ByteArray, byte[]> getBlockUpdates() {
        return this.blockUpdates;
    }

    @Override
    public Map<ByteArray, byte[]> getIndexUpdates() {
        return this.indexUpdates;
    }

    @Override
    public void removeUpdates(BlockState otherBs) {
        if(!blockUpdates.isEmpty() && otherBs!= null && !otherBs.getBlockUpdates().isEmpty()) {
            Map<ByteArray, byte[]> otherBlockUpdates = otherBs.getBlockUpdates();
            otherBlockUpdates.forEach((otherK, otherV) ->{
                byte[] v = blockUpdates.get(otherK);
                if(v != null && Arrays.equals(v, otherV)) {
                    blockUpdates.remove(otherK);
                }
            });
        }

        if(!indexUpdates.isEmpty() && otherBs!= null && !otherBs.getIndexUpdates().isEmpty()) {
            Map<ByteArray, byte[]> otherIndexUpdates = otherBs.getIndexUpdates();
            otherIndexUpdates.forEach((otherK, otherV) ->{
                byte[] v = indexUpdates.get(otherK);
                if(v != null && Arrays.equals(v, otherV)) {
                    indexUpdates.remove(otherK);
                }
            });
        }
    }

    @Override
    public void rollback() {
        blockUpdates.clear();
        indexUpdates.clear();
    }

    @Override
    public BlockState clone() {
        BlockStateImpl clone = new BlockStateImpl(indexDB, blockDB);
        clone.blockUpdates.putAll(blockUpdates);
        clone.indexUpdates.putAll(indexUpdates);

        return clone;
    }

}
