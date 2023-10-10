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

import static io.xdag.core.Fork.APOLLO_FORK;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import com.google.common.collect.Lists;

import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.spec.DagSpec;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.AccountStateImpl;
import io.xdag.db.Database;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.LeveldbDatabase;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import io.xdag.utils.TimeUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * DAG chain implementation.
 *
 * <pre>
 * index DB structure:
 *
 * [0] => [latest_block_number]
 *
 * [3, block_hash] => [block_number]
 * [4, transaction_hash] => [block_number, from, to] | [coinbase_transaction]
 * [5, address, n] => [transaction_hash]
 * [7] => [activated forks]
 *
 * [0xff] => [database version]
 * </pre>
 *
 * <pre>
 * block DB structure:
 *
 * [0, block_number] => [block_header]
 * [1, block_number] => [block_transactions]
 * [2, block_number] => [block_results]
 * </pre>
 */
@Slf4j
@Getter
@Setter
public class DagchainImpl implements Dagchain {

    protected static final int DATABASE_VERSION = 1;

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

    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    private final List<DagchainListener> listeners = new ArrayList<>();
    private Config config;
    private DagSpec dagSpec;
    private Genesis genesis;

    private Database indexDB;
    private Database blockDB;

    private AccountState accountState;

    private MainBlock latestMainBlock;

    private ActivatedForks forks;

    private PendingManager pendingManager;

    public DagchainImpl(Config config, PendingManager pendingMgr, DatabaseFactory dbFactory) {
        this(config, pendingMgr, Genesis.load(config.getNodeSpec().getNetwork()), dbFactory);
    }

    public DagchainImpl(Config config, PendingManager pendingManager, Genesis genesis, DatabaseFactory dbFactory) {
        this.config = config;
        this.dagSpec = config.getDagSpec();
        this.genesis = genesis;
        this.pendingManager = pendingManager;
        openDb(config, dbFactory);
    }

    private synchronized void openDb(Config config, DatabaseFactory dbFactory) {
        // upgrade if possible
        upgradeDatabase(config, dbFactory);

        this.indexDB = dbFactory.getDB(DatabaseName.INDEX);
        this.blockDB = dbFactory.getDB(DatabaseName.BLOCK);

        this.accountState = new AccountStateImpl(dbFactory.getDB(DatabaseName.ACCOUNT));

        // checks if the database needs to be initialized
        byte[] number = indexDB.get(BytesUtils.of(TYPE_LATEST_BLOCK_NUMBER));

        // load the activate forks from database
        forks = new ActivatedForks(this, config, getActivatedForks());

        if (number == null || number.length == 0) {
            // initialize the database for the first time
            initializeDb();
        } else {
            // load the latest block
            latestMainBlock = getMainBlockByNumber(BytesUtils.toLong(number));
        }
    }

    private void initializeDb() {
        // initialize database version
        indexDB.put(BytesUtils.of(TYPE_DATABASE_VERSION), BytesUtils.of(DATABASE_VERSION));

        // snapshot
        for (Genesis.XSnapshot s : genesis.getSnapshots().values()) {
            accountState.adjustAvailable(s.getAddress(), s.getAmount());
        }
        accountState.commit();

        // add block
        addMainBlock(genesis);
    }

    @Override
    public AccountState getAccountState() {
        return accountState;
    }

    @Override
    public MainBlock getLatestMainBlock() {
        return latestMainBlock;
    }

    @Override
    public long getLatestMainBlockNumber() {
        return latestMainBlock.getNumber();
    }

    @Override
    public byte[] getLatestMainBlockHash() {
        return latestMainBlock.getHash();
    }

    @Override
    public long getMainBlockNumber(byte[] hash) {
        byte[] number = indexDB.get(BytesUtils.merge(TYPE_BLOCK_NUMBER_BY_HASH, hash));
        return (number == null) ? -1 : BytesUtils.toLong(number);
    }

    @Override
    public MainBlock getMainBlockByNumber(long number) {
        return getBlock(blockDB, number, false);
    }

    @Override
    public MainBlock getMainBlockByHash(byte[] hash) {
        long number = getMainBlockNumber(hash);
        return (number == -1) ? null : getMainBlockByNumber(number);
    }

    @Override
    public BlockHeader getBlockHeader(long number) {
        byte[] header = blockDB.get(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)));
        return (header == null) ? null : BlockHeader.fromBytes(header);
    }

    @Override
    public BlockHeader getBlockHeader(byte[] hash) {
        long number = getMainBlockNumber(hash);
        return (number == -1) ? null : getBlockHeader(number);
    }

    @Override
    public boolean hasMainBlock(long number) {
        return blockDB.get(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number))) != null;
    }

    private static class TransactionIndex {
        long blockNumber;
        int transactionOffset;
        int resultOffset;

        public TransactionIndex(long blockNumber, int transactionOffset, int resultOffset) {
            this.blockNumber = blockNumber;
            this.transactionOffset = transactionOffset;
            this.resultOffset = resultOffset;
        }

        public byte[] toBytes() {
            SimpleEncoder enc = new SimpleEncoder();
            enc.writeLong(blockNumber);
            enc.writeInt(transactionOffset);
            enc.writeInt(resultOffset);
            return enc.toBytes();
        }

        public static TransactionIndex fromBytes(byte[] bytes) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            long number = dec.readLong();
            int transactionOffset = dec.readInt();
            int resultOffset = dec.readInt();
            return new TransactionIndex(number, transactionOffset, resultOffset);
        }
    }

    @Override
    public Transaction getTransaction(byte[] hash) {
        byte[] bytes = indexDB.get(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
        if (bytes != null) {
            // coinbase transaction
            if (bytes.length > 64) {
                return Transaction.fromBytes(bytes);
            }

            TransactionIndex index = TransactionIndex.fromBytes(bytes);
            byte[] transactions = blockDB
                    .get(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(index.blockNumber)));
            SimpleDecoder dec = new SimpleDecoder(transactions, index.transactionOffset);
            return Transaction.fromBytes(dec.readBytes());
        }

        return null;
    }

    @Override
    public Transaction getCoinbaseTransaction(long blockNumber) {
        return blockNumber == 0
                ? null
                : getTransaction(indexDB.get(BytesUtils.merge(TYPE_BLOCK_COINBASE_BY_NUMBER, BytesUtils.of(blockNumber))));
    }

    @Override
    public boolean hasTransaction(final byte[] hash) {
        return indexDB.get(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash)) != null;
    }

    @Override
    public TransactionResult getTransactionResult(byte[] hash) {
        byte[] bytes = indexDB.get(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
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

        return null;
    }

    @Override
    public long getTransactionBlockNumber(byte[] hash) {
        Transaction tx = getTransaction(hash);
        if (tx.getType() == TransactionType.COINBASE) {
            return tx.getNonce();
        }

        byte[] bytes = indexDB.get(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, hash));
        if (bytes != null) {
            SimpleDecoder dec = new SimpleDecoder(bytes);
            return dec.readLong();
        }

        return -1;
    }

    @Override
    public synchronized void addMainBlock(MainBlock block) {
        long number = block.getNumber();
        byte[] hash = block.getHash();

        // TODO support 16 parent block
        if (number != genesis.getNumber() && number != latestMainBlock.getNumber() + 1) {
            log.error("Adding wrong block: number = {}, expected = {}", number, latestMainBlock.getNumber() + 1);
            throw new DagchainException("Blocks can only be added sequentially");
        }

        // [1] update block
        blockDB.put(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)), block.getEncodedHeader());
        blockDB.put(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_HASH_BY_NUMBER, BytesUtils.of(number)), block.getEncodedTxHashs());
        blockDB.put(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(number)), block.getEncodedTransactions());
        blockDB.put(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(number)), block.getEncodedResults());

        indexDB.put(BytesUtils.merge(TYPE_BLOCK_NUMBER_BY_HASH, hash), BytesUtils.of(number));

        // [2] update transaction indices
        List<Transaction> txs = Lists.newArrayList();

        if(CollectionUtils.isEmpty(block.getTransactions()) && CollectionUtils.isNotEmpty(block.getTxHashs())) {
            List<Bytes32> txHashs = block.getTxHashs();
            List<PendingManager.PendingTransaction> txPool = pendingManager.getPendingTransactions();
            Map<Bytes32, Transaction> txPoolMap = new HashMap<>();
            txPool.forEach( ptx -> {
                Bytes32 h = Bytes32.wrap(ptx.getTransaction().getHash());
                txPoolMap.put(h, ptx.getTransaction());
            });
            txHashs.forEach(h -> {
                Transaction tx = txPoolMap.get(h);
                if(tx != null) {
                    txs.add(tx);
                }
            });
        } else {
            txs.addAll(block.getTransactions());
        }

        Pair<byte[], List<Integer>> txHashIndices = block.getEncodedTxHashsAndIndices();
        Pair<byte[], List<Integer>> resultIndices = block.getEncodedResultsAndIndices();
        XAmount reward = MainBlock.getBlockReward(block, config);

        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            //TransactionResult result = block.getResults().get(i);

            TransactionIndex index = new TransactionIndex(number, txHashIndices.getRight().get(i),
                    resultIndices.getRight().get(i));
            indexDB.put(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, tx.getHash()), index.toBytes());

            // [3] update transaction_by_account index
            addTransactionToAccount(tx, tx.getFrom());
            if (!Arrays.equals(tx.getFrom(), tx.getTo())) {
                addTransactionToAccount(tx, tx.getTo());
            }
        }

        if (number != genesis.getNumber()) {
            // [4] coinbase transaction
            Transaction tx = new Transaction(config.getNodeSpec().getNetwork(),
                    TransactionType.COINBASE,
                    block.getCoinbase(),
                    reward,
                    XAmount.ZERO,
                    block.getNumber(),
                    block.getTimestamp(),
                    BytesUtils.EMPTY_BYTES);
            tx.sign(Constants.COINBASE_KEY);
            indexDB.put(BytesUtils.merge(TYPE_TRANSACTION_INDEX_BY_HASH, tx.getHash()), tx.toBytes());
            indexDB.put(BytesUtils.merge(TYPE_BLOCK_COINBASE_BY_NUMBER, BytesUtils.of(block.getNumber())), tx.getHash());
            addTransactionToAccount(tx, block.getCoinbase());
        }

        // [7] update latest_block
        latestMainBlock = block;
        indexDB.put(BytesUtils.of(TYPE_LATEST_BLOCK_NUMBER), BytesUtils.of(number));

        for (DagchainListener listener : listeners) {
            listener.onMainBlockAdded(block);
        }

        activateForks();
    }

    @Override
    public Genesis getGenesis() {
        return genesis;
    }

    @Override
    public void addListener(DagchainListener listener) {
        listeners.add(listener);
    }

    @Override
    public int getTransactionCount(byte[] address) {
        byte[] cnt = indexDB.get(BytesUtils.merge(TYPE_TRANSACTION_COUNT_BY_ADDRESS, address));
        return (cnt == null) ? 0 : BytesUtils.toInt(cnt);
    }

    @Override
    public List<Transaction> getTransactions(byte[] address, int from, int to) {
        List<Transaction> list = new ArrayList<>();

        int total = getTransactionCount(address);
        for (int i = from; i < total && i < to; i++) {
            byte[] key = getNthTransactionIndexKey(address, i);
            byte[] value = indexDB.get(key);
            list.add(getTransaction(value));
        }

        return list;
    }

    /**
     * Sets the total number of transaction of an account
     */
    protected void setTransactionCount(byte[] address, int total) {
        indexDB.put(BytesUtils.merge(TYPE_TRANSACTION_COUNT_BY_ADDRESS, address), BytesUtils.of(total));
    }

    /**
     * Adds a transaction to an account.
     */
    protected void addTransactionToAccount(Transaction tx, byte[] address) {
        int total = getTransactionCount(address);
        indexDB.put(getNthTransactionIndexKey(address, total), tx.getHash());
        setTransactionCount(address, total + 1);
    }

    /**
     * Returns the N-th transaction index key of an account.
     */
    protected byte[] getNthTransactionIndexKey(byte[] address, int n) {
        return BytesUtils.merge(BytesUtils.of(TYPE_TRANSACTION_HASH_BY_ADDRESS_AND_INDEX), address, BytesUtils.of(n));
    }

    /**
     * Returns the version of current database.
     */
    protected int getDatabaseVersion() {
        return getDatabaseVersion(indexDB);
    }

    @Override
    public boolean isForkActivated(Fork fork, long number) {
        return forks.isActivated(fork, number);
    }

    @Override
    public boolean isForkActivated(Fork fork) {
        // TODO
        // the latest block has been imported, we should check the
        // fork status at latest_block + 1.
        return forks.isActivated(fork, getLatestMainBlockNumber());
    }

    @Override
    public byte[] constructBlockHeaderDataField() {
        Set<Fork> set = new HashSet<>();

        if (config.getDagSpec().forkApolloEnabled()) {
            addFork(set, APOLLO_FORK);
        }

        return set.isEmpty() ? new BlockHeaderData().toBytes() : new BlockHeaderData(ForkSignalSet.of(set)).toBytes();
    }

    private void addFork(Set<Fork> set, Fork fork) {
        long[] period = config.getDagSpec().getForkSignalingPeriod(fork);
        long number = getLatestMainBlockNumber() + 1;

        if (/* !this.isForkActivated(fork) && */number >= period[0] && number <= period[1]) {
            set.add(fork);
        }
    }

    @Override
    public ReentrantReadWriteLock getStateLock() {
        return stateLock;
    }

    @Override
    public boolean importBlock(MainBlock block) {
        AccountState asTrack = this.getAccountState().track();
        return validateBlock(block, asTrack) && applyBlock(block, asTrack);
    }

    /**
     * Validate the block.
     */
    protected boolean validateBlock(MainBlock block, AccountState asTrack) {
        try {
            BlockHeader header = block.getHeader();
            List<Transaction> transactions = block.getTransactions();

            // [1] check block header
            // TODO support 16 parent block
            MainBlock latest = this.getLatestMainBlock();
            if (!block.validateHeader(header, latest.getHeader())) {
                log.error("Invalid block header");
                return false;
            }

            // [?] additional checks by block importer
            // - check points
            if (dagSpec.checkpoints().containsKey(header.getNumber()) &&
                    !Arrays.equals(header.getHash(), dagSpec.checkpoints().get(header.getNumber()))) {
                log.error("Checkpoint validation failed, checkpoint is {} => {}, getting {}", header.getNumber(),
                        Bytes.wrap(dagSpec.checkpoints().get(header.getNumber())).toHexString(),
                        Bytes.wrap(header.getHash()).toHexString());
                return false;
            }

            // [2] check transactions
            if (!block.validateTransactions(header, transactions, config.getNodeSpec().getNetwork())) {
                log.error("Invalid transactions");
                return false;
            }
            if (transactions.stream().anyMatch(tx -> this.hasTransaction(tx.getHash()))) {
                log.error("Duplicated transaction hash is not allowed");
                return false;
            }

            // [3] evaluate transactions
            TransactionExecutor transactionExecutor = new TransactionExecutor(config);
            List<TransactionResult> results = transactionExecutor.execute(transactions, asTrack);
            if (!block.validateResults(header, results)) {
                log.error("Invalid transaction results");
                return false;
            }
            block.setResults(results); // overwrite the results

            return true;
        } catch (Exception e) {
            log.error("Unexpected exception during block validation", e);
            return false;
        }
    }

    protected boolean applyBlock(MainBlock block, AccountState asTrack) {
        // [5] apply block reward and tx fees
        XAmount reward = MainBlock.getBlockReward(block, config);

        if (reward.isPositive()) {
            asTrack.adjustAvailable(block.getCoinbase(), reward);
        }

        // [6] commit the updates
        asTrack.commit();

        ReentrantReadWriteLock.WriteLock writeLock = this.stateLock.writeLock();
        writeLock.lock();
        try {
            // [7] flush state to disk
            this.getAccountState().commit();

            // [8] add block to chain
            this.addMainBlock(block);
        } finally {
            writeLock.unlock();
        }

        return true;
    }

    /**
     * Attempt to activate pending forks at current height.
     */
    protected void activateForks() {
        if (config.getDagSpec().forkApolloEnabled() && forks.activateFork(APOLLO_FORK)) {
            setActivatedForks(forks.getActivatedForks());
        }
    }

    /**
     * Returns the set of active forks.
     */
    protected Map<Fork, Fork.Activation> getActivatedForks() {
        Map<Fork, Fork.Activation> activations = new HashMap<>();
        byte[] value = indexDB.get(BytesUtils.of(TYPE_ACTIVATED_FORKS));
        if (value != null) {
            SimpleDecoder simpleDecoder = new SimpleDecoder(value);
            final int numberOfForks = simpleDecoder.readInt();
            for (int i = 0; i < numberOfForks; i++) {
                Fork.Activation activation = Fork.Activation.fromBytes(simpleDecoder.readBytes());
                activations.put(activation.fork, activation);
            }
        }
        return activations;
    }

    /**
     * Sets the set of activate forks.
     */
    protected void setActivatedForks(Map<Fork, Fork.Activation> activatedForks) {
        SimpleEncoder simpleEncoder = new SimpleEncoder();
        simpleEncoder.writeInt(activatedForks.size());
        for (Entry<Fork, Fork.Activation> entry : activatedForks.entrySet()) {
            simpleEncoder.writeBytes(entry.getValue().toBytes());
        }
        indexDB.put(BytesUtils.of(TYPE_ACTIVATED_FORKS), simpleEncoder.toBytes());
    }

    private static void upgradeDatabase(Config config, DatabaseFactory dbFactory) {
        if (getLatestMainBlockNumber(dbFactory.getDB(DatabaseName.INDEX)) != null
                && getDatabaseVersion(dbFactory.getDB(DatabaseName.INDEX)) < DagchainImpl.DATABASE_VERSION) {
            upgrade(config, dbFactory, Long.MAX_VALUE);
        }
    }

    public static void upgrade(Config config, DatabaseFactory dbFactory, long to) {
        try {
            log.info("Upgrading the database... DO NOT CLOSE THE WALLET!");
            Instant begin = Instant.now();

            Path dataDir = dbFactory.getDataDir();
            String dataDirName = dataDir.getFileName().toString();

            // setup temp chain
            Path tempPath = dataDir.resolveSibling(dataDirName + "-temp");
            delete(tempPath);
            LeveldbDatabase.LeveldbFactory tempDbFactory = new LeveldbDatabase.LeveldbFactory(tempPath.toFile());
            DagchainImpl tempChain = new DagchainImpl(config, null, tempDbFactory);

            // import all blocks
            long imported = 0;
            Database indexDB = dbFactory.getDB(DatabaseName.INDEX);
            Database blockDB = dbFactory.getDB(DatabaseName.BLOCK);
            byte[] bytes = getLatestMainBlockNumber(indexDB);
            long latestMainBlockNumber = (bytes == null) ? 0 : BytesUtils.toLong(bytes);
            long target = Math.min(latestMainBlockNumber, to);
            for (long i = 1; i <= target; i++) {
                boolean result = tempChain.importBlock(getBlock(blockDB, i, true));
                if (!result) {
                    break;
                }

                if (i % 1000 == 0) {
                    log.info("Loaded {} / {} blocks", i, target);
                }
                imported++;
            }

            // close both database factory
            dbFactory.close();
            tempDbFactory.close();

            // swap the database folders
            Path backupPath = dataDir.resolveSibling(dataDirName + "-backup");
            dbFactory.moveTo(backupPath);
            tempDbFactory.moveTo(dataDir);
            delete(backupPath); // delete old database to save space.

            Instant end = Instant.now();
            log.info("Database upgraded: found blocks = {}, imported = {}, took = {}", latestMainBlockNumber, imported,
                    TimeUtils.formatDuration(Duration.between(begin, end)));
        } catch (IOException e) {
            log.error("Failed to upgrade database", e);
        }
    }

    // THE FOLLOWING TYPE ID SHOULD NEVER CHANGE

    private static MainBlock getBlock(Database blockDB, long number, boolean skipResults) {
        byte[] header = blockDB.get(BytesUtils.merge(TYPE_BLOCK_HEADER_BY_NUMBER, BytesUtils.of(number)));
        byte[] transactions = blockDB.get(BytesUtils.merge(TYPE_BLOCK_TRANSACTIONS_BY_NUMBER, BytesUtils.of(number)));
        byte[] results = skipResults ? null : blockDB.get(BytesUtils.merge(TYPE_BLOCK_RESULTS_BY_NUMBER, BytesUtils.of(number)));

        return (header == null) ? null : MainBlock.fromComponents(header, transactions, results, false);
    }

    private static byte[] getLatestMainBlockNumber(Database indexDB) {
        return indexDB.get(BytesUtils.of(TYPE_LATEST_BLOCK_NUMBER));
    }

    private static int getDatabaseVersion(Database indexDB) {
        byte[] version = indexDB.get(BytesUtils.of(TYPE_DATABASE_VERSION));
        return version == null ? 0 : BytesUtils.toInt(version);
    }

    private static void delete(Path directory) throws IOException {
        if (!directory.toFile().exists()) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
