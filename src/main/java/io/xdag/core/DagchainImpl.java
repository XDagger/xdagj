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
import java.math.BigInteger;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.spec.DagSpec;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.AccountStateImpl;
import io.xdag.core.state.BlockState;
import io.xdag.core.state.BlockStateImpl;
import io.xdag.core.state.StateSnapshotKey;
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

    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    private final List<DagchainListener> listeners = new ArrayList<>();
    private Config config;
    private DagSpec dagSpec;
    private Genesis genesis;

    private Map<StateSnapshotKey, BlockState> blockStateMap;
    private Map<StateSnapshotKey, AccountState> accountStateMap;


    private MainBlock latestMainBlock;
    private MainBlock latestCheckPointMainBlock;

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
        BlockState blockState = new BlockStateImpl(dbFactory.getDB(DatabaseName.INDEX), dbFactory.getDB(DatabaseName.BLOCK));
        this.blockStateMap = Maps.newHashMap();

        // upgrade if possible
        upgradeDatabase(blockState, config, dbFactory);

        AccountState accountState = new AccountStateImpl(dbFactory.getDB(DatabaseName.ACCOUNT));
        this.accountStateMap = Maps.newHashMap();


        // checks if the database needs to be initialized
        long number = blockState.getLatestMainBlockNumber();

        // load the activate forks from database
        forks = new ActivatedForks(this, config, getActivatedForks(blockState));

        if (number == -1) {
            // initialize the database for the first time
            initializeDb(blockState, accountState);
        } else {
            // load the latest block
            latestMainBlock = blockState.getMainBlockByNumber(number);

            MainBlock lastParent = latestMainBlock;
            for(long i = Constants.EPOCH_FINALIZE_NUMBER; i > 0 && (lastParent != null && lastParent.getNumber() > 0); i--) {
                this.blockStateMap.put(StateSnapshotKey.of(lastParent.getHash(), lastParent.getNumber()), blockState);
                this.accountStateMap.put(StateSnapshotKey.of(lastParent.getHash(), lastParent.getNumber()), accountState);
                lastParent = blockState.getMainBlockByHash(lastParent.getParentHash());
            }
        }
    }

    private void initializeDb(BlockState blockState, AccountState accountState) {
        // initialize database version
        blockState.putDatabaseVersion(DATABASE_VERSION);

        // snapshot
        for (Genesis.XSnapshot s : genesis.getSnapshots().values()) {
            accountState.adjustAvailable(s.getAddress(), s.getAmount());
        }
        accountState.commit();
        accountStateMap.put(StateSnapshotKey.of(genesis.getHash(), genesis.getNumber()), accountState);

        blockState.commit();
        blockStateMap.put(StateSnapshotKey.of(genesis.getHash(), genesis.getNumber()), blockState);

        // add block
        addMainBlock(genesis, blockState);
    }

    @Override
    public AccountState getAccountState(byte[] hash, long snapshotNumber) {
        return accountStateMap.get(StateSnapshotKey.of(hash, snapshotNumber));
    }

    @Override
    public AccountState getLatestAccountState() {
        return accountStateMap.get(StateSnapshotKey.of(latestMainBlock.getHash(), latestMainBlock.getNumber()));
    }

    @Override
    public BlockState getBlockState(byte[] hash, long snapshotNumber) {
        return blockStateMap.get(StateSnapshotKey.of(hash, snapshotNumber));
    }

    @Override
    public BlockState getLatestBlockState() {
        return blockStateMap.get(StateSnapshotKey.of(latestMainBlock.getHash(), latestMainBlock.getNumber()));
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
        return this.getLatestBlockState().getMainBlockNumber(hash);
    }

    @Override
    public MainBlock getMainBlockByNumber(long number) {
        return this.getLatestBlockState().getMainBlockByNumber(number);
    }

    @Override
    public MainBlock getMainBlockByHash(byte[] hash) {
        return this.getLatestBlockState().getMainBlockByHash(hash);
    }

    @Override
    public BlockHeader getBlockHeader(long number) {
        return this.getLatestBlockState().getBlockHeader(number);
    }

    @Override
    public BlockHeader getBlockHeader(byte[] hash) {
        long number = getMainBlockNumber(hash);
        return (number == -1) ? null : getBlockHeader(number);
    }

    @Override
    public boolean hasMainBlock(long number) {
        return this.getLatestBlockState().hasMainBlock(number);
    }

    @Override
    public Transaction getTransaction(byte[] hash) {
        return this.getLatestBlockState().getTransaction(hash);
    }

    @Override
    public Transaction getCoinbaseTransaction(long blockNumber) {
        return this.getLatestBlockState().getCoinbaseTransaction(blockNumber);
    }

    @Override
    public boolean hasTransaction(final byte[] hash) {
        return this.getLatestBlockState().hasTransaction(hash);
    }

    @Override
    public TransactionResult getTransactionResult(byte[] hash) {
        return this.getLatestBlockState().getTransactionResult(hash);
    }

    @Override
    public long getTransactionBlockNumber(byte[] hash) {
        return this.getLatestBlockState().getTransactionBlockNumber(hash);
    }

    @Override
    public synchronized void addMainBlock(MainBlock block, BlockState bs) {
        long number = block.getNumber();

        // [1] update block
        bs.addMainBlock(block);

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

            BlockState.TransactionIndex index = new BlockState.TransactionIndex(number, txHashIndices.getRight().get(i),
                    resultIndices.getRight().get(i));
            bs.addTransactionIndex(tx.getHash(), index.toBytes());

            // [3] update transaction_by_account index
            bs.addTransactionToAccount(tx, tx.getFrom());
            if (!Arrays.equals(tx.getFrom(), tx.getTo())) {
                bs.addTransactionToAccount(tx, tx.getTo());
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

            bs.addTransactionIndex(tx.getHash(), tx.toBytes());
            bs.addBlockCoinbaseByNumber(block.getNumber(), tx.getHash());
            bs.addTransactionToAccount(tx, block.getCoinbase());
        }

        // [7] check new chain header
        checkNewChain(block, bs);

        for (DagchainListener listener : listeners) {
            listener.onMainBlockAdded(block);
        }

        activateForks();
        blockStateMap.put(StateSnapshotKey.of(block.getHash(), block.getNumber()), bs);
    }

    private void checkNewChain(MainBlock block, BlockState bs) {
        long number = block.getNumber();
        if(latestMainBlock == null) {
            latestMainBlock = block;
            bs.addLatestBlockNumber(number);
        } else if(block.getNumber() > latestMainBlock.getNumber()) {
            latestMainBlock = block;
            bs.addLatestBlockNumber(number);
            log.info("update latest {}", block);
            latestMainBlock = block;
        } else if( block.getNumber() == latestMainBlock.getNumber()) {
            BigInteger latestHash = new BigInteger(1, latestMainBlock.getHash());
            BigInteger blockHash = new BigInteger(1, block.getHash());
            if(blockHash.compareTo(latestHash) < 0 && !Arrays.equals(block.getHash(), latestMainBlock.getHash())) {
                log.warn("reorg chain latest number:{}, header from:{}, to:{}",
                        block.getNumber(),
                        Bytes.wrap(latestMainBlock.getHash()).toHexString(),
                        Bytes.wrap(block.getHash()).toHexString());
                latestMainBlock = block;
                bs.addLatestBlockNumber(number);
            }
        } else if(latestMainBlock.getNumber() - number < Constants.EPOCH_FINALIZE_NUMBER) {
            log.warn("fork chain at number:{}, old:{}, new:{}",
                    block.getNumber(),
                    Bytes.wrap(getMainBlockByNumber(block.getNumber()).getHash()).toHexString(),
                    Bytes.wrap(block.getHash()).toHexString());
        } else {
            log.warn("fork chain is reject, {}", block);
        }
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
        return this.getLatestBlockState().getTransactionCount(address);
    }

    @Override
    public List<Transaction> getTransactions(byte[] address, int from, int to) {
        return this.getLatestBlockState().getTransactions(address, from, to);
    }

    /**
     * Sets the total number of transaction of an account
     */
    protected void setTransactionCount(BlockState blockState, byte[] address, int total) {
        blockState.setTransactionCount(address, total);
    }

    /**
     * Returns the version of current database.
     */
    protected int getDatabaseVersion() {
        return this.getLatestBlockState().getDatabaseVersion();
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
    public boolean importBlock(MainBlock block, AccountState accountState, BlockState blockState) {
        if(Arrays.equals(block.getHash(), latestMainBlock.getHash())) {
            return true;
        }
        return validateBlock(block, accountState, blockState) && applyBlock(block, accountState, blockState);
    }

    /**
     * Validate the block.
     */
    protected boolean validateBlock(MainBlock block, AccountState as, BlockState bs) {
        try {
            BlockHeader header = block.getHeader();
            List<Transaction> transactions = block.getTransactions();

            // [1] check block header
            MainBlock parentMainBlock = bs.getMainBlockByHash(block.getParentHash());
            if(parentMainBlock == null) {
                log.error("Invalid block header, parent MainBlock is null");
                return false;
            }

            if (!block.validateHeader(header, parentMainBlock.getHeader())) {
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
            if (transactions.stream().anyMatch(tx -> bs.hasTransaction(tx.getHash()))) {
                log.error("Duplicated transaction hash is not allowed");
                return false;
            }

            // [3] evaluate transactions
            TransactionExecutor transactionExecutor = new TransactionExecutor(config);
            List<TransactionResult> results = transactionExecutor.execute(transactions, as);
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

    private void removeStateSnapshot(final long number, final byte[] hash) {
        Iterator<Entry<StateSnapshotKey, AccountState>> asiterator = accountStateMap.entrySet().iterator();
        while (asiterator.hasNext()) {
            Entry<StateSnapshotKey, AccountState> entry = asiterator.next();
            StateSnapshotKey k = entry.getKey();
            if(k.getNumber() == number) {
                if(!Arrays.equals(k.getHash().getData(), hash)) {
                    asiterator.remove();
                }
            } else if(k.getNumber() < number) {
                asiterator.remove();
            }
        }

        Iterator<Entry<StateSnapshotKey, BlockState>> bsiterator = blockStateMap.entrySet().iterator();
        while (bsiterator.hasNext()) {
            Entry<StateSnapshotKey, BlockState> entry = bsiterator.next();
            StateSnapshotKey k = entry.getKey();
            if(k.getNumber() == number) {
                if(!Arrays.equals(k.getHash().getData(), hash)) {
                    bsiterator.remove();
                }
            } else if(k.getNumber() < number) {
                bsiterator.remove();
            }
        }
    }

    protected boolean applyBlock(MainBlock block, AccountState as, BlockState bs) {
        // [5] apply block reward and tx fees
        XAmount reward = MainBlock.getBlockReward(block, config);

        if (reward.isPositive()) {
            as.adjustAvailable(block.getCoinbase(), reward);
        }

        // [6] update account state map
        accountStateMap.put(StateSnapshotKey.of(block.getHash(), block.getNumber()), as);

        ReentrantReadWriteLock.WriteLock writeLock = this.stateLock.writeLock();
        writeLock.lock();
        try {
            // [7] finalize xdag main chain and flush AccountState and BlockState to disk
            finalizeMainChain(block, as, bs);

            // [8] add block to chain
            this.addMainBlock(block, bs);
        } finally {
            writeLock.unlock();
        }

        return true;
    }

    private void finalizeMainChain(MainBlock mainBlock, AccountState as, BlockState bs) {
        long localNumber = this.getLatestMainBlockNumber();
        if(mainBlock.getNumber() > Constants.EPOCH_FINALIZE_NUMBER && mainBlock.getNumber() >= localNumber) {
            MainBlock mb = mainBlock;
            MainBlock lastParent = mb;

            for(int i = 0; i < Constants.EPOCH_FINALIZE_NUMBER; i++) {
                lastParent = bs.getMainBlockByHash(mb.getParentHash());
                if(lastParent != null) {
                    mb = lastParent;
                } else {
                    log.trace("finalize forward error from {}.", mb);
                    return;
                }
            }

            // commit before 16 main block state snapshot
            AccountState accountState = accountStateMap.get(StateSnapshotKey.of(mb.getHash(), mb.getNumber()));
            as.removeUpdates(accountState);
            accountState.commit();

            BlockState blockState = blockStateMap.get(StateSnapshotKey.of(mb.getHash(), mb.getNumber()));
            bs.removeUpdates(blockState);
            blockState.commit();

            // remove before 16 main block state snapshots and same number fork snapshots
            removeStateSnapshot(mb.getNumber(), mb.getHash());
            latestCheckPointMainBlock = mb;
            log.trace("finalize at {}, as={}, bs={}.", mb, as, bs);
        }
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
    protected Map<Fork, Fork.Activation> getActivatedForks(BlockState blockState) {
        Map<Fork, Fork.Activation> activations = new HashMap<>();
        byte[] value = blockState.getActivatedForks();
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

        this.getLatestBlockState().addActivatedForks(simpleEncoder.toBytes());
    }

    private void upgradeDatabase(BlockState blockState, Config config, DatabaseFactory dbFactory) {
        if (blockState.getLatestMainBlockNumber() != -1 && blockState.getDatabaseVersion() < DagchainImpl.DATABASE_VERSION) {
            upgrade(config, dbFactory, Long.MAX_VALUE);
        }
    }

    public void upgrade(Config config, DatabaseFactory dbFactory, long to) {
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
            long latestMainBlockNumber = this.getLatestBlockState().getLatestMainBlockNumber();
            long target = Math.min(latestMainBlockNumber, to);
            AccountState latestAccountState = this.getLatestAccountState();
            BlockState latestBlockState = this.getLatestBlockState();
            for (long i = 1; i <= target; i++) {
                boolean result = tempChain.importBlock(getBlock(i, true), latestAccountState, latestBlockState);
                if (!result) {
                    break;
                }

                if (i % 1000 == 0) {
                    log.info("Loaded {} / {} blocks", i, target);
                }
                imported++;
            }
            latestAccountState.commit();
            latestBlockState.commit();

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

    private MainBlock getBlock(long number, boolean skipResults) {
        return  this.getLatestBlockState().getBlock(number, skipResults);
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

    public static void main(String[] args) {
        Random random = new Random();
        byte[] hash = new byte[32];
        random.nextBytes(hash);
        BigInteger latestHash = new BigInteger(1, hash);
        BigInteger blockHash = new BigInteger(1, hash);
        if(blockHash.compareTo(latestHash) < 0) {
            System.out.println("true");
        }
    }
}
