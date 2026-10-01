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

import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLong;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.MainnetConfig;
import io.xdag.core.XdagField.FieldType;
import io.xdag.consensus.RandomX;
import io.xdag.crypto.core.CryptoProvider;
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PublicKey;
import io.xdag.crypto.keys.Signature;
import io.xdag.crypto.keys.Signer;
import io.xdag.db.*;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.db.rocksdb.SnapshotStoreImpl;
import io.xdag.listener.BlockMessage;
import io.xdag.listener.Listener;
import io.xdag.listener.PretopMessage;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static io.xdag.config.Constants.*;
import static io.xdag.config.Constants.MessageType.NEW_LINK;
import static io.xdag.config.Constants.MessageType.PRE_TOP;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.BytesUtils.*;
import static io.xdag.utils.BytesUtils.equalBytes;
import static io.xdag.utils.WalletUtils.checkAddress;

@Slf4j
@Getter
public class BlockchainImpl implements Blockchain {

    // Static gas fee accumulator
    private static XAmount sumGas = XAmount.ZERO;
    private static final long MAX_ORPHAN_SIZE = 3750;
    public static final String IGNORE_INTERRUPTED_MAIN_UPDATE = "xdagj.ignoreInterruptedMainUpdate";

    // Thread factory for main chain checking
    private static final ThreadFactory factory = BasicThreadFactory.builder()
            .namingPattern("check-main-%d")
            .daemon(true)
            .build();

    // Wallet instance
    private final Wallet wallet;

    // Storage components
    private final AddressStore addressStore;
    private final BlockStore blockStore;
    private final TransactionHistoryStore txHistoryStore;

    // Store for non-Extra orphan blocks
    private final OrphanBlockStore orphanBlockStore;

    // In-memory pools and maps.
    // The pool is read by rpc / p2p threads (getBlockByHash) while the import thread changes it, so it has to be
    // a synchronized map; iteration additionally locks the map itself.
    private final Map<Bytes, Block> memOrphanPool = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Bytes, Integer> memOurBlocks = new ConcurrentHashMap<>();

    // Stats and status tracking
    private final XdagStats xdagStats;
    private final Kernel kernel;
    private final XdagTopStatus xdagTopStatus;

    // Main chain checking components
    private final ScheduledExecutorService checkLoop;
    private final RandomX randomx;
    private final List<Listener> listeners = Lists.newArrayList();
    private ScheduledFuture<?> checkLoopFuture;

    // Snapshot related fields
    private final long snapshotHeight;
    private SnapshotStore snapshotStore;
    private SnapshotStore snapshotAddressStore;
    private final XdagExtStats xdagExtStats;

    // roll back transaction
    @Getter
    private final Map<Bytes32, Bytes32> mBlockTx = new ConcurrentHashMap<>();
    @Getter
    private final Map<Bytes32, Long> mBlockTimedOut = new ConcurrentHashMap<>();
    private final ScheduledExecutorService rollBackLoop = Executors.newSingleThreadScheduledExecutor();

    private List<Block> rollTxList = new LinkedList<>();

    private final Cache<Bytes32, Byte> syncTxStatusCache = CacheBuilder.newBuilder()
            .maximumSize(500000)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    private static final Logger execLog = LogManager.getLogger("ExecutionStatusLog");

    @Getter
    private byte[] preSeed;

    /**
     * Open-network hardening fork (see docs/OPEN_NETWORK.md). Blocks whose epoch is at or after this epoch are
     * validated, scored and executed by the hardened rules.
     */
    private final long openNetForkEpoch;

    /**
     * Set once the main chain of this node contains a main block at or after {@link #openNetForkEpoch} and never
     * cleared. From then on every newly imported block is handled by the hardened rules whatever its timestamp,
     * so a block that claims an old timestamp cannot opt back into the legacy rules. The network layer only
     * admits arbitrary peers while this is set.
     */
    private volatile boolean openNetLatched;

    // Constructor initializes all components and starts main chain checking
    public BlockchainImpl(Kernel kernel) {
        // Initialize core components
        this.kernel = kernel;
        this.wallet = kernel.getWallet();
        this.xdagExtStats = new XdagExtStats();

        // Initialize storage components
        this.addressStore = kernel.getAddressStore();
        this.blockStore = kernel.getBlockStore();
        this.orphanBlockStore = kernel.getOrphanBlockStore();
        this.txHistoryStore = kernel.getTxHistoryStore();
        snapshotHeight = kernel.getConfig().getSnapshotSpec().getSnapshotHeight();

        // Initialize snapshot if enabled
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()
                && kernel.getConfig().getSnapshotSpec().getSnapshotHeight() > 0
                && !blockStore.isSnapshotBoot()) {

            this.xdagStats = new XdagStats();
            this.xdagTopStatus = new XdagTopStatus();

            if (kernel.getConfig().getSnapshotSpec().isSnapshotJ()) {
                initSnapshotJ();
            }

            // Save latest snapshot state
            blockStore.saveXdagTopStatus(xdagTopStatus);
            blockStore.saveXdagStatus(xdagStats);

        } else {
            // Load existing state
            XdagStats storedStats = blockStore.getXdagStatus();
            XdagTopStatus storedTopStatus = blockStore.getXdagTopStatus();

            if (storedStats != null) {
                storedStats.setNwaitsync(0);
                this.xdagStats = storedStats;
                this.xdagStats.nextra = 0;
            } else {
                this.xdagStats = new XdagStats();
            }

            this.xdagTopStatus = Objects.requireNonNullElseGet(storedTopStatus, XdagTopStatus::new);

            Block lastBlock = getBlockByHeight(xdagStats.nmain);
            if (lastBlock != null) {
                xdagStats.setMaxdifficulty(lastBlock.getInfo().getDifficulty());
                xdagStats.setDifficulty(lastBlock.getInfo().getDifficulty());
                xdagTopStatus.setTop(lastBlock.getHashLow().toArray());
                xdagTopStatus.setTopDiff(lastBlock.getInfo().getDifficulty());
            }
            preSeed = blockStore.getPreSeed();
        }

        if (blockStore.isMainUpdateInProgress()) {
            String message = "The node was killed while it was confirming or unwinding a main block. Balances and "
                    + "block flags in " + kernel.getConfig().getNodeSpec().getStoreDir() + " may be half updated, and a "
                    + "node in that state silently disagrees with the network. Restore the database (snapshot) and "
                    + "sync again. To start anyway, set -D" + IGNORE_INTERRUPTED_MAIN_UPDATE + "=true.";
            if (!Boolean.getBoolean(IGNORE_INTERRUPTED_MAIN_UPDATE)) {
                throw new IllegalStateException(message);
            }
            log.error(message);
            blockStore.setMainUpdateInProgress(false);
        }

        // Open-network hardening fork
        this.openNetForkEpoch = kernel.getConfig().getOpenNetForkEpoch();
        Block latestMain = xdagStats.nmain > 0 ? blockStore.getBlockByHeight(xdagStats.nmain) : null;
        this.openNetLatched = openNetForkEpoch == 0
                || blockStore.isOpenNetForkLatched()
                || (latestMain != null && XdagTime.getEpoch(latestMain.getTimestamp()) >= openNetForkEpoch);

        // Initialize RandomX
        randomx = kernel.getRandomx();
        if (randomx != null) {
            randomx.setBlockchain(this);
        }

        // Start main chain checking
        checkLoop = new ScheduledThreadPoolExecutor(1, factory);
        this.startCheckMain(1024);

        this.mBlockTx.clear();
        this.mBlockTimedOut.clear();
        this.startCleaner();
        List<Block> blocks = listMainBlocksByHeight(10);
        if (blocks != null) {
            blocks = blocks.reversed();
            this.saveMBlockTx(blocks);

        }
    }

    // Initialize snapshot data
    public void initSnapshotJ() {
        long start = System.currentTimeMillis();
        System.out.println("init snapshot...");

        // Initialize address snapshot store
        RocksdbKVSource snapshotAddressSource = new RocksdbKVSource("SNAPSHOT/ADDRESS");
        snapshotAddressStore = new SnapshotStoreImpl(snapshotAddressSource);
        snapshotAddressSource.setConfig(kernel.getConfig());
        snapshotAddressSource.init();
        snapshotAddressStore.saveAddress(this.blockStore, this.addressStore, this.txHistoryStore, kernel.getWallet().getAccounts(), kernel.getConfig().getSnapshotSpec().getSnapshotTime());

        // Initialize block snapshot store
        RocksdbKVSource snapshotSource = new RocksdbKVSource("SNAPSHOT/BLOCKS");
        snapshotStore = new SnapshotStoreImpl(snapshotSource);
        snapshotSource.setConfig(kernel.getConfig());
        snapshotStore.init();
        snapshotStore.saveSnapshotToIndex(this.blockStore, this.txHistoryStore, kernel.getWallet().getAccounts(), kernel.getConfig().getSnapshotSpec().getSnapshotTime());
        Block lastBlock = blockStore.getBlockByHeight(snapshotHeight);

        // Initialize stats
        xdagStats.balance = snapshotStore.getOurBalance();
        xdagStats.setNwaitsync(0);
        xdagStats.setNnoref(0);
        xdagStats.setNextra(0);
        xdagStats.setTotalnblocks(0);
        xdagStats.setNblocks(0);
        xdagStats.setTotalnmain(snapshotHeight);
        xdagStats.setNmain(snapshotHeight);
        xdagStats.setMaxdifficulty(lastBlock.getInfo().getDifficulty());
        xdagStats.setDifficulty(lastBlock.getInfo().getDifficulty());

        // Initialize top status
        xdagTopStatus.setPreTop(lastBlock.getHashLow().toArray());
        xdagTopStatus.setTop(lastBlock.getHashLow().toArray());
        xdagTopStatus.setTopDiff(lastBlock.getInfo().getDifficulty());
        xdagTopStatus.setPreTopDiff(lastBlock.getInfo().getDifficulty());

        // Calculate total balance
        XAmount allBalance = snapshotStore.getAllBalance().add(snapshotAddressStore.getAllBalance());

        long end = System.currentTimeMillis();
        System.out.println("init snapshotJ done");
        System.out.println("time：" + (end - start) + "ms");
        System.out.println("Our balance: " + snapshotStore.getOurBalance().toDecimal(9, XUnit.XDAG).toPlainString());
        System.out.printf("All amount: %s%n", allBalance.toDecimal(9, XUnit.XDAG).toPlainString());
    }

    // Register event listener
    @Override
    public void registerListener(Listener listener) {
        this.listeners.add(listener);
    }

    /**
     * Whether the open-network hardening fork is latched on this node's chain, i.e. whether the node runs an
     * open (permissionless) network. See {@link #openNetLatched}.
     */
    @Override
    public boolean isOpenNetLatched() {
        return openNetLatched;
    }

    /**
     * Whether a block is validated and scored by the hardened rules: its own epoch is at or after the fork, or
     * the chain has already passed the fork (a block that claims an older timestamp gets no legacy treatment).
     */
    boolean isHardenedBlock(Block block) {
        return openNetLatched || XdagTime.getEpoch(block.getTimestamp()) >= openNetForkEpoch;
    }

    /**
     * Whether the transactions confirmed by a main block are executed by the hardened rules. This depends only
     * on the main block, so every node executes a given main block in the same way.
     */
    boolean isHardenedExecution(Block mainBlock) {
        return XdagTime.getEpoch(mainBlock.getTimestamp()) >= openNetForkEpoch;
    }

    private void latchOpenNet(Block mainBlock) {
        if (!openNetLatched && isHardenedExecution(mainBlock)) {
            blockStore.setOpenNetForkLatched();
            openNetLatched = true;
            log.info("Open-network hardening fork is now in force (main block {} at epoch {})",
                    mainBlock.getHashLow().toHexString(), XdagTime.getEpoch(mainBlock.getTimestamp()));
        }
    }

    /**
     * Start time of the snapshot this node was booted from (0 if it was not). Everything before it is final.
     */
    private long snapshotFloorTime() {
        return kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()
                ? kernel.getConfig().getSnapshotSpec().getSnapshotTime() : 0;
    }

    /**
     * Hardened rule: a node that was booted from a snapshot never reorganizes below the top of that snapshot.
     * The blocks of the snapshot carry no data, so they cannot be unwound; all nodes of a release start from
     * the same snapshot and therefore agree on this.
     *
     * @param ancestor the main-chain block a heavier chain branches off from (null: it does not join our chain)
     */
    private boolean mayReorganizeTo(Block ancestor) {
        if (!openNetLatched || snapshotHeight <= 0 || !kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()) {
            return true;
        }
        if (ancestor == null) {
            return false;
        }
        BlockInfo info = ancestor.getInfo();
        return !info.isSnapshot() || info.getHeight() >= snapshotHeight;
    }

    private ImportResult invalid(String errorInfo, boolean misbehavior) {
        ImportResult result = ImportResult.INVALID_BLOCK;
        result.setErrorInfo(errorInfo);
        result.setMisbehavior(misbehavior);
        log.debug(errorInfo);
        return result;
    }

    private ImportResult invalid(MutableBytes32 hashlow, String errorInfo, boolean misbehavior) {
        ImportResult result = invalid(errorInfo, misbehavior);
        result.setHashlow(hashlow);
        return result;
    }

    // Try to connect a new block to the chain
    @Override
    public synchronized ImportResult tryToConnect(Block block) {

        // TODO: if current height is snapshot height, we need change logic to process new block

        try {
            ImportResult result = ImportResult.IMPORTED_NOT_BEST;
            ImportResult.INVALID_BLOCK.setHashlow(null);
            ImportResult.INVALID_BLOCK.setErrorInfo(null);
            ImportResult.INVALID_BLOCK.setMisbehavior(false);
            ImportResult.ERROR.setErrorInfo(null);
            ImportResult.ERROR.setMisbehavior(false);

            // Rules of the open-network hardening fork apply to this block (see docs/OPEN_NETWORK.md)
            final boolean hardened = isHardenedBlock(block);

            // Validate block type
            long type = block.getType() & 0xf;
            if (kernel.getConfig() instanceof MainnetConfig) {
                if (type != XDAG_FIELD_HEAD.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a mainnet block");
                    result.setMisbehavior(true);
                    log.debug("Block type error, is not a mainnet block");
                    return result;
                }
            } else {
                if (type != XDAG_FIELD_HEAD_TEST.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a testnet block");
                    result.setMisbehavior(true);
                    log.debug("Block type error, is not a testnet block");
                    return result;
                }
            }

            // Validate block timestamp
            if (block.getTimestamp() > (XdagTime.getCurrentTimestamp() + MAIN_CHAIN_PERIOD / 4)) {
                // depends on the local clock: not a proof of misbehavior
                return invalid("Block's time is illegal", false);
            }
            if (block.getTimestamp() < kernel.getConfig().getXdagEra()) {
                return invalid("Block's time is illegal", true);
            }
            if (hardened && block.getTimestamp() < snapshotFloorTime()) {
                // Everything before the snapshot is final. A node that was booted from the snapshot no longer
                // knows which of the older blocks were executed, so it must not accept any of them again.
                return invalid("Block's time is before the snapshot", false);
            }

            if (!hardened && isAccountTx(block) && orphanBlockStore.getOrphanSize() >= MAX_ORPHAN_SIZE) {
                // Legacy rule. Under the hardened rules a full pool is not a reason to call a block invalid
                // (validity must not depend on local state); the block is just not queued for our own blocks.
                return invalid("Orphan block pool is full", false);
            }

            // Check if block already exists
            if (isExist(block.getHashLow())) {
                return ImportResult.EXIST;
            }

            if (isExistInMem(block.getHashLow())) {
                return ImportResult.IN_MEM;
            }

            // Check if extra block
            if (isExtraBlock(block)) {
                updateBlockFlag(block, BI_EXTRA, true);
            }

            if (isTxBlock(block) && XAmount.ZERO.compareTo(getTxFee(block)) == 0) {
                return invalid("There is a problem with the transaction fee of this transaction block", true);
            }
            if (isAccountTx(block) && block.getOutputs().isEmpty()) {
                // outPutLimit() divides by the number of outputs; the legacy code ran into that division below
                // and refused the block with an ERROR result
                return invalid("An account transaction needs at least one output", true);
            }

            // Validate block references
            List<Address> all = block.getLinks().stream().distinct().toList();
            int inputFieldCounter = 0;

            for (Address ref : all) {
                if (ref != null && !ref.isAddress) {
                    if (ref.getType() == XDAG_FIELD_OUT && !ref.getAmount().isZero()) {
                        return invalid(ref.getAddress(), "Address's amount isn't zero", true);
                    }
                    Block refBlock = getBlockByHash(ref.getAddress(), false);
                    if (refBlock == null) {
                        result = ImportResult.NO_PARENT;
                        result.setHashlow(ref.getAddress());
                        result.setErrorInfo("Block have no parent for " + result.getHashlow().toHexString());
                        log.debug("Block have no parent for {}", result.getHashlow().toHexString());
                        return result;
                    } else {
                        // Ensure ref block's time is earlier than block's time
                        if (refBlock.getTimestamp() >= block.getTimestamp()) {
                            return invalid(refBlock.getHashLow(), "Ref block's time >= block's time", true);
                        }
                        // Ensure TX block's amount is enough to subtract minGas, Amount must >= 0.1
                        if (ref.getType() == XDAG_FIELD_IN && ref.getAmount().subtract(getTxFee(block)).isNegative()) {
                            return invalid(ref.getAddress(), "Ref block's balance < fee", true);
                        }
                    }
                } else {
                    // Ensure that there is only one input.
                    if (ref != null && ref.type == XDAG_FIELD_INPUT) {
                        inputFieldCounter = inputFieldCounter + 1;
                        if (inputFieldCounter > 1) {
                            return invalid("The quantity of the input must be exactly one.", true);
                        }
                    }
                    // Legacy rule: the spending address must already have a record. Whether it has one depends
                    // on what this node has executed (and even on rollbacks it went through), so nodes can
                    // disagree about the very same block. The hardened rules do not look at the state here:
                    // a transfer from an address without funds simply fails when it is executed.
                    if (!hardened && ref != null && ref.type == XDAG_FIELD_INPUT
                            && !addressStore.addressIsExist(BytesUtils.byte32ToArray(ref.getAddress()).toArray())) {
                        return invalid("Address isn't exist " + Base58.encodeCheck(
                                BytesUtils.byte32ToArray(ref.getAddress())), false);
                    }
                    // Ensure TX block's input's & output's amount is enough to subtract minGas, Amount must >= 0.1
                    if (ref != null && (ref.getType() == XDAG_FIELD_INPUT || ref.getType() == XDAG_FIELD_OUTPUT)) {
                        if (getTxFee(block).isPositive() && outPutLimit(block).isPositive()) {
                            if (ref.getType() == XDAG_FIELD_INPUT && ref.getAmount().subtract(getTxFee(block)).isNegative()) {
                                return invalid(ref.getAddress(), "Ref input amount < Gas", true);
                            } else if (ref.getType() == XDAG_FIELD_OUTPUT && ref.getAmount().subtract(outPutLimit(block)).isNegative()) {
                                return invalid(ref.getAddress(), "Ref output amount < Gas", true);
                            }
                        } else {
                            return invalid("When constructing a block, the fee entered is illegal", true);
                        }
                    }
                }

                // Determine if ref is a block
                if (ref != null && compareAmountTo(ref.getAmount(), XAmount.ZERO) != 0) {
                    log.debug("Try to connect a tx Block:{}", block.getHash().toHexString());
                    updateBlockFlag(block, BI_EXTRA, false);
                }
            }

            if (isAccountTx(block)) {
                if(block.getTxNonceField() == null) {
                    return invalid("Account transaction block must have nonce.", true);
                }
            } else if (isTxBlock(block)) {
                if(block.getTxNonceField() != null) {
                    return invalid("The main block transaction block should not contain nonce.", true);
                }
            } else {
                if(block.getTxNonceField() != null) {
                    return invalid("The main block or link block should not contain nonce.", true);
                }
            }

            // Every block carries an output signature. The legacy code dereferenced it without a check, after
            // it had already changed the state (links removed from the orphan pool, history written): a block
            // without one ended in an exception and was dropped, but the damage was done.
            if (block.getOutsig() == null) {
                return invalid(block.getHashLow(), "Block has no output signature", true);
            }

            // Validate block inputs
            if (!canUseInput(block)) {
                return invalid(block.getHashLow(), "Block's input can't be used", true);
            }

            // Proof of work of the block itself. It only depends on the block (and on the RandomX seeds), so it
            // is computed before anything is changed: if it cannot be computed the block is refused untouched.
            BigInteger cuDiff = calculateCurrentBlockDiff(block);

            // ------------------------------------------------------------------------------------------------
            // The block is valid. Everything below changes the state and must not fail.
            // ------------------------------------------------------------------------------------------------

            // Remove links
            for (Address ref : all) {
                if (!ref.isAddress) {
                    removeOrphan(ref.getAddress(),
                            (block.getInfo().flags & BI_EXTRA) != 0
                                    ? OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                                    : OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                }
            }
            if (!hardened) {
                // 0.8.x wrote the transaction history when a block arrived, whether or not it was ever executed
                // and without taking it back on a rollback. Under the hardened rules it is written when the
                // transaction executes and removed when that execution is undone.
                recordTxHistory(block);
            }

            // Check current main chain
            checkNewMain();

            // Check if block is ours
            if (checkMineAndAdd(block)) {
                log.debug("A block hash:{} become mine", block.getHashLow().toHexString());
                updateBlockFlag(block, BI_OURS, true);
            }

            // Calculate block difficulty
            calculateBlockDiff(block, cuDiff);

            // Process extra blocks
            processExtraBlock();

            // Update main chain based on difficulty
            Block blockRef = null;
            boolean heavier = block.getInfo().getDifficulty().compareTo(xdagTopStatus.getTopDiff()) > 0;
            if (heavier) {
                // Find common ancestor
                blockRef = findAncestor(block, isSyncFixFork(xdagStats.nmain));
                if (!mayReorganizeTo(blockRef)) {
                    log.warn("Block {} is heavier than our chain but branches off below the snapshot; not following it",
                            block.getHashLow().toHexString());
                    heavier = false;
                }
            }
            if (heavier) {
                // Fork chain
                long currentHeight = xdagStats.nmain;

                // Unwind main chain to ancestor
                unWindMain(blockRef);

                // Update new chain
                updateNewChain(block, isSyncFixFork(xdagStats.nmain));

                // Log unwind info
                if (currentHeight - xdagStats.nmain > 1) {
                    log.info("XDAG:Before unwind, height = {}, After unwind, height = {}, unwind number = {}",
                            currentHeight, xdagStats.nmain, currentHeight - xdagStats.nmain);
                }

                Block currentTop = getBlockByHash(xdagTopStatus.getTop() == null ? null :
                        Bytes32.wrap(xdagTopStatus.getTop()), false);
                BigInteger currentTopDiff = xdagTopStatus.getTopDiff();
                log.debug("update top: {}", block.getHashLow());

                // Update top status
                xdagTopStatus.setTopDiff(block.getInfo().getDifficulty());
                xdagTopStatus.setTop(block.getHashLow().toArray());

                // Update pre-top
                setPreTop(currentTop, currentTopDiff);

                // Notify PoW thread if needed
                if (XdagTime.getEpoch(block.getTimestamp()) < XdagTime.getCurrentEpoch()) {
                    onNewPretop();
                }

                result = ImportResult.IMPORTED_BEST;
                xdagStats.updateMaxDiff(xdagTopStatus.getTopDiff());
                xdagStats.updateDiff(xdagTopStatus.getTopDiff());
            }

            // Update block stats
            xdagStats.nblocks++;
            xdagStats.totalnblocks = Math.max(xdagStats.nblocks, xdagStats.totalnblocks);

            if ((block.getInfo().flags & BI_EXTRA) != 0) {
                block.getInfo().setFee(XAmount.ZERO);
                memOrphanPool.put(block.getHashLow(), block);
                xdagStats.nextra++;
            } else {
                saveBlock(block);
                dealOrphan(block);
                incrementNnoref();
            }
            blockStore.saveXdagStatus(xdagStats);

            // Log transaction info
            if (!block.getInputs().isEmpty()) {
                if ((block.getInfo().getFlags() & BI_OURS) != 0) {
                    log.info("XDAG:pool transaction(reward). block hash:{}", block.getHash().toHexString());
                }
            }

            // Update hashrate stats
            int i = (int) (XdagTime.getEpoch(block.getTimestamp()) & (HASH_RATE_LAST_MAX_TIME - 1));
            if (XdagTime.getEpoch(block.getTimestamp()) > XdagTime.getEpoch(xdagExtStats.getHashrate_last_time())) {
                xdagExtStats.getHashRateTotal()[i] = BigInteger.ZERO;
                xdagExtStats.getHashRateOurs()[i] = BigInteger.ZERO;
                xdagExtStats.setHashrate_last_time(block.getTimestamp());
            }

            if (cuDiff.compareTo(xdagExtStats.getHashRateTotal()[i]) > 0) {
                xdagExtStats.getHashRateTotal()[i] = cuDiff;
            }

            if ((block.getInfo().getFlags() & BI_OURS) != 0
                    && cuDiff.compareTo(xdagExtStats.getHashRateOurs()[i]) > 0) {
                xdagExtStats.getHashRateOurs()[i] = cuDiff;
            }

            return result;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            ImportResult.ERROR.setErrorInfo(e.getClass().getSimpleName() + ": " + e.getMessage());
            return ImportResult.ERROR;
        }
    }

    /**
     * Get the transaction block packaged from the main block of the forked chain.
     */
    public void rollTx(Block block) {
        List<Address> links = block.getLinks().reversed();

        for (Address link : links) {
            if (!link.isAddress && !link.getType().equals(XDAG_FIELD_IN)) {
                Block txBlock = getBlockByHash(link.getAddress(), true);
                if (block.getHashLow().equals(mBlockTx.get(link.addressHash)) || (txBlock.getInfo().getRef() != null && equalBytes(txBlock.getInfo().getRef(), block.getHashLow().toArray()))) {
                    if ((txBlock.getInfo().flags & BI_MAIN_CHAIN) == 0) {
                        rollTxList.add(txBlock);
                        if ((txBlock.getInfo().flags & BI_REF) != 0) {
                            txBlock=getBlockByHash(link.getAddress(),false);
                            updateBlockFlag(txBlock, BI_REF, false);
                            incrementNnoref();
                            blockStore.saveXdagStatus(xdagStats);
                        }
                        mBlockTx.remove(link.addressHash);
                        mBlockTimedOut.remove(link.addressHash);
                        log.debug("roll main block :{} , txBlock :{} , mBlockTx size :{}", block.getHashLow(), link.addressHash, mBlockTx.size());
                        continue;
                    }
                    List<Address> mTXs = txBlock.getLinks();
                    for (Address mTX : mTXs) {
                        if (mTX.getType().equals(XDAG_FIELD_IN)) {
                            mBlockTx.remove(link.addressHash);
                            mBlockTimedOut.remove(link.addressHash);
                            rollTxList.add(txBlock);
                            if ((txBlock.getInfo().flags & BI_REF) == 0) continue;
                            txBlock=getBlockByHash(link.getAddress(),false);
                            updateBlockFlag(txBlock, BI_REF, false);
                            incrementNnoref();
                            blockStore.saveXdagStatus(xdagStats);
                            log.debug("roll main txBlock :{} , txBlock :{} , mBlockTx size :{}", block.getHashLow(), link.addressHash, mBlockTx.size());
                            break;
                        }
                    }
                }
            }
        }
    }

    public void dealOrphan(Block block) {
        if (kernel.getConfig().getEnableGenerateBlock() && kernel.getPow() != null) {
            UInt64 nonce = UInt64.ZERO;
            XAmount fee = getTxFee(block);
            byte[] address = null;
            if (isAccountTx(block)) {
                List<Address> refs = block.getLinks();
                for (Address txRef : refs) {
                    if (txRef.getType().equals(XDAG_FIELD_INPUT)) {
                        address = BytesUtils.byte32ToArray(txRef.getAddress()).toArray();
                        nonce = block.getTxNonceField().getTransactionNonce();
                        break;
                    }
                }
            }
            if (isHardenedBlock(block) && !admitToPool(block, address, nonce)) {
                log.debug("Transaction {} is valid but not queued for our own blocks", block.getHashLow().toHexString());
                return;
            }
            getOrphanBlockStore().addOrphan(block, isTxBlock(block), nonce, fee, address);
        }
    }

    // Highest nonce, counted from the last executed one, that is still queued for an account
    private static final long MAX_POOL_NONCE_GAP = 64;

    /**
     * Which transactions this node queues for the blocks it produces itself. This is local policy, not a consensus
     * rule: a transaction that is not queued here is still a valid block, other blocks may refer to it, and it is
     * executed like any other once a main block reaches it.
     * <p>
     * A transaction that cannot be paid for costs its sender nothing - it is rejected when executed, without a
     * fee. Queuing such transactions would let anyone fill the pool (and, with a high declared fee, the front of
     * the queue) for free, so only transactions that are covered right now are queued:
     * <ul>
     * <li>account transaction: the pool is not full, the nonce is one of the next few, and the balance covers this
     *     transfer plus everything already queued for the account;</li>
     * <li>transfer of block balances: every spent block currently holds what is taken from it.</li>
     * </ul>
     * Link blocks are always queued: they may refer to transactions that are waiting, which a block of ours has
     * to reach.
     */
    private boolean admitToPool(Block block, byte[] account, UInt64 nonce) {
        if (isAccountTx(block) && account != null) {
            if (orphanBlockStore.getOrphanSize() >= MAX_ORPHAN_SIZE) {
                return false;
            }
            UInt64 executed = addressStore.getExecutedNonceNum(account);
            if (nonce.compareTo(executed) <= 0
                    || nonce.toBigInteger().subtract(executed.toBigInteger()).compareTo(BigInteger.valueOf(MAX_POOL_NONCE_GAP)) > 0) {
                return false;
            }
            XAmount needed = XAmount.ZERO;
            List<Block> spending = new ArrayList<>();
            spending.add(block);
            for (Bytes32 queued : orphanBlockStore.getAccountOrphans(account)) {
                Block queuedBlock = queued.equals(block.getHashLow()) ? null : getBlockByHash(queued, true);
                if (queuedBlock != null) {
                    spending.add(queuedBlock);
                }
            }
            for (Block b : spending) {
                for (Address in : b.getInputs()) {
                    if (in.getType() == XDAG_FIELD_INPUT) {
                        needed = addOrNull(needed, in.getAmount());
                        if (needed == null) {
                            return false;
                        }
                    }
                }
            }
            return compareAmountTo(addressStore.getBalanceByAddress(account), needed) >= 0;
        }
        if (isMainTxBlock(block)) {
            Map<Bytes32, XAmount> taken = new HashMap<>();
            for (Address in : block.getInputs()) {
                Block ref = getBlockByHash(in.getAddress(), false);
                XAmount sum = addOrNull(taken.getOrDefault(Bytes32.wrap(in.getAddress()), XAmount.ZERO), in.getAmount());
                if (ref == null || sum == null || compareAmountTo(ref.getInfo().getAmount(), sum) < 0) {
                    return false;
                }
                taken.put(Bytes32.wrap(in.getAddress()), sum);
            }
        }
        return true;
    }

    public XAmount getTxFee(Block block) {
        if (!isTxBlock(block)) {
            return XAmount.ZERO;
        }
        XdagBlock xdagBlock = block.getXdagBlock();
        if (xdagBlock == null) {
            return XAmount.ZERO;
        } else {
            Bytes32 header = Bytes32.wrap(xdagBlock.getField(0).getData());
            XAmount fee = XAmount.of(header.getLong(24, ByteOrder.LITTLE_ENDIAN), XUnit.NANO_XDAG);
            if (fee.compareTo(XAmount.ZERO) == 0) {
                return MIN_GAS.multiply(outPutNum(block));
            } else if (fee.isNegative()) {
                return XAmount.ZERO;
            } else {
                return fee.add(MIN_GAS.multiply(outPutNum(block)));
            }
        }
    }

    /**
     * Get the number of transactions executed in the main block package
     * @param refHashLow The hash of the transaction packaged in the main block
     * @param mHashLow The hash of the main block
     * @return Number of transactions executed
     */
    public int txNumber(Bytes32 refHashLow, Bytes32 mHashLow) {
        int sum = 0;
        if (getBlockByHash(refHashLow, true) != null) {
            Block block = getBlockByHash(refHashLow, true);
            if (!isTxBlock(block) && (block.getInfo().flags & BI_MAIN_CHAIN) == 0) {
                for (Address link : block.getLinks()) {
                    if (equalBytes(block.getInfo().getRef(), mHashLow.toArray())) {
                        sum += txNumber(link.getAddress(), block.getHashLow());
                    }
                }
                return sum;
            }
            if ((block.getInfo().flags & BI_APPLIED) != 0 && (block.getInfo().getRef() != null && equalBytes(block.getInfo().getRef(), mHashLow.toArray()))) {
                return outPutNum(block) == -1 ? 0 : outPutNum(block);
            }

        }
        return 0;
    }

    /**
     * Remember the execution status a peer attached to a block it served during sync.
     * <p>
     * The legacy rules let a syncing node skip a transaction because a peer said it had been rejected. That is
     * only tolerable while every peer is one the operator chose (closed network before the fork). Once the fork
     * is in force nothing a peer claims about the state is used: the hint is not even stored, and the hardened
     * execution never reads it.
     */
    public void putSyncTxStatus(Bytes32 txHash, byte executionStatus){
        if (openNetLatched) {
            return;
        }
        if(executionStatus != 0){
            syncTxStatusCache.put(txHash, executionStatus);
        }
    }

    public Byte getSyncTxStatus(Bytes32 txHash){
        Byte status = syncTxStatusCache.getIfPresent(txHash);
//        if (status != null) {
//            syncTxStatusCache.invalidate(txHash);
//        }
        return status;
    }

    public void clearAllSyncTxStatus() {
        syncTxStatusCache.invalidateAll();
        syncTxStatusCache.cleanUp();
    }

    public boolean isTxBlock(Block block) {
        return isAccountTx(block) || isMainTxBlock(block);
    }

    public boolean isAccountTx(Block block) {
        List<Address> inputs = block.getInputs();
        if (inputs == null) return false;

        int inputCount = 0;
        for (Address ref : inputs) {
            if (ref.getType() == XDAG_FIELD_IN) {
                return false; // an IN link is not allowed here
            } else if (ref.getType() == XDAG_FIELD_INPUT) {
                inputCount++;
            }
        }
        return inputCount == 1;
    }

    public boolean isMainTxBlock(Block block) {
        List<Address> inputs = block.getInputs();
        if (inputs == null) return false;

        for (Address ref : inputs) {
            if (ref.getType() == XDAG_FIELD_INPUT) {
                return false; // no INPUT
            }
        }

        // At least one XDAG_FIELD_IN
        return inputs.stream().anyMatch(ref -> ref.getType() == XDAG_FIELD_IN);
    }

    public int outPutNum(Block block) {
        if (isTxBlock(block)) {
            return block.getOutputs().size();
        }
        return -1;
    }

    public XAmount outPutLimit(Block block) {
        if (!isTxBlock(block)) {
            return XAmount.ZERO;
        }
        XAmount allFee = getTxFee(block);
        int num = outPutNum(block);
        if (num == -1) {
            return XAmount.ZERO;
        } else if (MIN_GAS.compareTo(allFee.divide(num)) > 0) {
            return MIN_GAS;
        } else {
            return allFee.divide(num);
        }
    }

    // Record transaction history
    /** The history entries of a block: one per link that carries an amount, from the linked side's point of view. */
    private void recordTxHistory(Block block) {
        if (txHistoryStore == null) {
            return;
        }
        int id = 0;
        for (Address ref : block.getLinks()) {
            FieldType fType;
            if (!ref.isAddress) {
                fType = ref.getType().equals(XDAG_FIELD_IN) ? XDAG_FIELD_OUT : XDAG_FIELD_IN;
            } else {
                fType = ref.getType().equals(XDAG_FIELD_INPUT) ? XDAG_FIELD_OUTPUT : XDAG_FIELD_INPUT;
            }
            if (compareAmountTo(ref.getAmount(), XAmount.ZERO) != 0) {
                if (fType.equals(XDAG_FIELD_OUT) || fType.equals(XDAG_FIELD_OUTPUT)) {
                    onNewTxHistory(ref.getAddress(), block.getHashLow(), fType, ref.getAmount(),
                            block.getTimestamp(), block.getInfo().getRemark(), ref.isAddress, id);
                } else {
                    XAmount singleOutputFee = outPutLimit(block);
                    onNewTxHistory(ref.getAddress(), block.getHashLow(), fType, ref.getAmount().subtract(singleOutputFee),
                            block.getTimestamp(), block.getInfo().getRemark(), ref.isAddress, id);
                }
            }
            id++;
        }
    }

    /** Takes the history entries of a block back (its execution was undone). */
    private void forgetTxHistory(Block block) {
        if (txHistoryStore != null) {
            try {
                txHistoryStore.deleteTxHistoryByHash(BasicUtils.hash2Address(block.getHashLow()));
            } catch (RuntimeException e) {
                log.warn("Cannot remove the history of {}: {}", block.getHashLow().toHexString(), e.toString());
            }
        }
    }

    public void onNewTxHistory(Bytes32 addressHashlow, Bytes32 txHashlow, XdagField.FieldType type,
                               XAmount amount, long time, byte[] remark, boolean isAddress, int id) {
        if (txHistoryStore != null) {
            Address address = new Address(addressHashlow, type, amount, isAddress);
            TxHistory txHistory = new TxHistory();
            txHistory.setAddress(address);
            txHistory.setHash(BasicUtils.hash2Address(txHashlow));
            if (remark != null) {
                txHistory.setRemark(new String(remark, StandardCharsets.UTF_8));
            }
            txHistory.setTimestamp(time);
            try {
                if (kernel.getXdagState() == XdagState.CDST || kernel.getXdagState() == XdagState.CTST || kernel.getXdagState() == XdagState.CONN
                        || kernel.getXdagState() == XdagState.CDSTP || kernel.getXdagState() == XdagState.CTSTP || kernel.getXdagState() == XdagState.CONNP) {
                    txHistoryStore.batchSaveTxHistory(txHistory);
                } else {
                    if (!txHistoryStore.saveTxHistory(txHistory)) {
                        log.warn("tx history write to mysql fail:{}", txHistory);
                        // Mysql exception, transaction history transferred to Rocksdb
                        blockStore.saveTxHistoryToRocksdb(txHistory, id);
                    } else {
                        List<TxHistory> txHistoriesInRocksdb = blockStore.getAllTxHistoryFromRocksdb();
                        if (!txHistoriesInRocksdb.isEmpty()) {
                            for (TxHistory txHistoryInRocksdb : txHistoriesInRocksdb) {
                                txHistoryStore.batchSaveTxHistory(txHistoryInRocksdb, txHistoriesInRocksdb.size());
                            }
                            if (txHistoryStore.batchSaveTxHistory(null)) {
                                blockStore.deleteAllTxHistoryFromRocksdb();
                            }
                        }
                    }
                }

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    // Get transaction history by address
    public List<TxHistory> getBlockTxHistoryByAddress(Bytes32 addressHashlow, int page, Object... parameters) {
        List<TxHistory> txHistory = Lists.newArrayList();
        if (txHistoryStore != null) {
            try {
                txHistory.addAll(txHistoryStore.listTxHistoryByAddress(checkAddress(addressHashlow) ?
                        BasicUtils.hash2PubAddress(addressHashlow) : BasicUtils.hash2Address(addressHashlow), page, parameters));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return txHistory;
    }

    // Check if should use sync fix fork
    public boolean isSyncFixFork(long currentHeight) {
        long syncFixHeight = SYNC_FIX_HEIGHT;
        return currentHeight >= syncFixHeight;
    }

    // Find common ancestor block
    public Block findAncestor(Block block, boolean isFork) {
        Block blockRef;
        Block blockRef0 = null;

        // Find highest difficulty non-main chain block
        for (blockRef = block;
             blockRef != null && ((blockRef.getInfo().flags & BI_MAIN_CHAIN) == 0);
             blockRef = getMaxDiffLink(blockRef, false)) {
            Block tmpRef = getMaxDiffLink(blockRef, false);
            if (
                    (tmpRef == null
                            || blockRef.getInfo().getDifficulty().compareTo(calculateBlockDiff(tmpRef, calculateCurrentBlockDiff(tmpRef))) > 0) &&
                            (blockRef0 == null || XdagTime.getEpoch(blockRef0.getTimestamp()) > XdagTime
                                    .getEpoch(blockRef.getTimestamp()))
            ) {
                if (!isFork) {
                    updateBlockFlag(blockRef, BI_MAIN_CHAIN, true);
                }
                blockRef0 = blockRef;
            }
        }

        // Handle fork point
        if (blockRef != null
                && blockRef0 != null
                && !blockRef.equals(blockRef0)
                && XdagTime.getEpoch(blockRef.getTimestamp()) == XdagTime.getEpoch(blockRef0.getTimestamp())) {
            blockRef = getMaxDiffLink(blockRef, false);
        }
        return blockRef;
    }

    // Update new chain after fork
    public void updateNewChain(Block block, boolean isFork) {
        if (!isFork) {
            return;
        }
        Block blockRef;
        Block blockRef0 = null;
        List<Block> blocks = new ArrayList<>();

        // Update main chain flags
        for (blockRef = block;
             blockRef != null && ((blockRef.getInfo().flags & BI_MAIN_CHAIN) == 0);
             blockRef = getMaxDiffLink(blockRef, false)) {
            Block tmpRef = getMaxDiffLink(blockRef, false);
            if (
                    (tmpRef == null
                            || blockRef.getInfo().getDifficulty().compareTo(calculateBlockDiff(tmpRef, calculateCurrentBlockDiff(tmpRef))) > 0) &&
                            (blockRef0 == null || XdagTime.getEpoch(blockRef0.getTimestamp()) > XdagTime
                                    .getEpoch(blockRef.getTimestamp()))
            ) {
                updateBlockFlag(blockRef, BI_MAIN_CHAIN, true);
                blockRef0 = blockRef;
                blocks.add(blockRef);
            }
        }
        if (!blocks.isEmpty()) {
            blocks = blocks.reversed();
            if (blocks.size() > 1) {
                blocks.removeLast();
                for (Block b : blocks) {
                    b = getBlockByHash(b.getHashLow(), true);
                    if (b == null) continue;
                    for(Address link : b.getLinks()){
                        if (link.isAddress) continue;
                        Block tx = getBlockByHash(link.getAddress(), false);
                        if((tx.getInfo().flags & BI_REF) == 0){
                            removeOrphan(link.getAddress(), OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                        }
                    }
                }
                saveMBlockTx(blocks);
            } else {
                Block currentBlock = blocks.getFirst();
                Block txBlock = getMaxDiffLink(currentBlock, false);
                if (txBlock != null) {
                    blocks.set(0, txBlock);
                    saveMBlockTx(blocks);
                }
            }
        }
    }

    // Process extra blocks
    public void processExtraBlock() {
        if (memOrphanPool.size() > MAX_ALLOWED_EXTRA) {
            Block reuse;
            synchronized (memOrphanPool) {
                reuse = memOrphanPool.entrySet().iterator().next().getValue();
            }
            log.debug("Remove when extra too big");
            removeOrphan(reuse.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_REUSE);
            xdagStats.nblocks--;
            xdagStats.totalnblocks = Math.max(xdagStats.nblocks, xdagStats.totalnblocks);

            if ((reuse.getInfo().flags & BI_OURS) != 0) {
                removeOurBlock(reuse);
            }
        }
    }

    // Notify listeners of new pretop
    protected void onNewPretop() {
        for (Listener listener : listeners) {
            listener.onMessage(new PretopMessage(Bytes.wrap(xdagTopStatus.getTop()), PRE_TOP));
        }
    }

    // Notify listeners of new block
    protected void onNewBlock(Block block) {
        for (Listener listener : listeners) {
            listener.onMessage(new BlockMessage(Bytes.wrap(block.getXdagBlock().getData()), NEW_LINK));
        }
    }

    // Check and update main chain
    @Override
    public synchronized void checkNewMain() {
        Block p = null;
        int i = 0;

        // If it's a snapshot point main block, return directly since data before snapshot is already determined
        if (xdagTopStatus.getTop() != null) {
            for (Block block = getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), false); block != null
                    && ((block.getInfo().flags & BI_MAIN) == 0);
                 block = getMaxDiffLink(getBlockByHash(block.getHashLow(), true), true)) {

                if ((block.getInfo().flags & BI_MAIN_CHAIN) != 0) {
                    p = block;
                    ++i;
                }
            }
        }
        long ct = XdagTime.getCurrentTimestamp();
        if (p != null
                && (((p.getInfo().flags & BI_REF) != 0) || isHardenedBlock(p))
                && i > 1
                && ct >= p.getTimestamp() + 2 * 1024) {
//            log.info("setMain success block:{}", Hex.toHexString(p.getHashLow()));
            setMain(p);
        }
    }
    // Why BI_REF is not asked for under the hardened rules: with i > 1 there is a block of the main chain above p
    // whose link of maximum difficulty is p, so p is referred to by construction and the flag adds nothing. It does
    // take something away, though. rollTx() clears the flag of every block that an unwound main block had executed,
    // to have it linked again by a block of our own - also when that block is itself part of the chain we are
    // switching to. The flag then only comes back when the block above it is linked by a later block, so a node that
    // went through the reorganisation confirms p one block later than a node that did not (and a node that does not
    // produce blocks waits for somebody else to do it). Which main blocks are confirmed must only depend on the
    // blocks a node has, not on the order in which it received them.

    @Override
    public long getLatestMainBlockNumber() {
        return xdagStats.nmain;
    }

    /**
     * Rollback to specified block
     */
    public void unWindMain(Block block) {
        log.debug("Unwind main to block,{}", block == null ? "null" : block.getHashLow().toHexString());
        if (xdagTopStatus.getTop() != null) {
            log.debug("now pretop : {}", xdagTopStatus.getPreTop() == null ? "null" : Bytes32.wrap(xdagTopStatus.getPreTop()).toHexString());
            for (Block tmp = getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), true); tmp != null
                    && !blockEqual(block, tmp); tmp = getMaxDiffLink(tmp, true)) {
                BlockInfo info = blockStore.getBlockInfo(tmp.getHashLow());
                if (info != null) {
                    tmp.getInfo().setFee(info.getFee());
                }
                updateBlockFlag(tmp, BI_MAIN_CHAIN, false);
                log.debug("roll main block: {}", tmp.getHashLow());
                if ((tmp.getInfo().flags & BI_EXTRA) == 0) rollTx(tmp);
                // Update corresponding flag information
                if ((tmp.getInfo().flags & BI_MAIN) != 0) {
                    unSetMain(tmp);
                    // Fix: Need to update block info in database like height 210729
                    blockStore.saveBlockInfo(tmp.getInfo());
                }
            }
            rollTxList = rollTxList.reversed();
            for (Block txBlock : rollTxList) {
                dealOrphan(txBlock);
                log.debug("roll txBlock:{}", txBlock.getHashLow());
            }
            rollTxList.clear();
        }
    }

    private boolean blockEqual(Block block1, Block block2) {
        if (block1 == null) {
            return block2 == null;
        } else {
            return block2.equals(block1);
        }
    }

    // applyBlock results that are not fees: the block was executed before / its nonce did not fit
    private static final XAmount MINUS_ONE = XAmount.ZERO.subtract(XAmount.ONE);
    private static final XAmount SKIPPED = XAmount.of(-2);

    /**
     * a + b, or null if the sum does not fit (XAmount arithmetic is exact and throws on overflow).
     */
    private static XAmount addOrNull(XAmount a, XAmount b) {
        try {
            return a.add(b);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Execute a block under the open-network hardening fork and return the fee it collected
     * (-1: not executed by this main block, 0: nothing collected / transaction rejected).
     * <p>
     * It follows {@link #applyBlockLegacy} step by step - same order of execution, same nonce rules, same
     * distribution of fees - and differs in exactly these points:
     * <ol>
     * <li>All IN links of a transaction that spend the same block are added up before they are compared with the
     *     balance of that block. The legacy code compares every link with the untouched balance, so a block listed
     *     twice is debited twice and ends with a negative balance: money from nothing.</li>
     * <li>Amounts are added without overflow; a transaction whose amounts do not fit is rejected like any other
     *     transaction whose inputs and outputs do not match. The legacy code throws out of the middle of setMain.</li>
     * <li>A block without inputs moves no value, whatever is written in its amount fields. The legacy code throws
     *     out of setMain if they are not zero.</li>
     * <li>Blocks inherited from a snapshot are final and carry no data; they are never executed again. The legacy
     *     code dereferences their missing data.</li>
     * <li>The execution status a peer attached to a block during sync is not consulted.</li>
     * <li>A transaction whose nonce does not fit is left unexecuted for good, as before, but the block that reached
     *     it is recorded ({@link BlockStore#setTxSkippedBy}). The legacy code keeps no such record, so when it
     *     unwinds <em>any</em> block that refers to such a transaction it puts the transaction back to "pending" -
     *     also when a different main block, one that stays on the chain, was the one that skipped it. A node that
     *     went through the reorganisation then executes the transaction later while a node that did not never
     *     does. (It also forgets to undo the blocks that were executed below the skipped transaction.)</li>
     * </ol>
     * Nothing in here can throw for a block that passed {@link #tryToConnect}; the value is only moved after the
     * whole transaction has been checked.
     */
    private XAmount applyBlockHardened(boolean flag, Block block) {
        // Block already processed
        if ((block.getInfo().flags & BI_MAIN_REF) != 0) {
            return MINUS_ONE;
        }

        updateBlockFlag(block, BI_MAIN_REF, true);

        List<Address> links = block.getLinks();
        if (links == null || links.isEmpty()) {
            updateBlockFlag(block, BI_APPLIED, true);
            return XAmount.ZERO;
        }

        XAmount gasCollected = XAmount.ZERO;
        if (flag) {
            execLog.info("========== Main Block: {} ==========", block.getHashLow().toHexString());
        }
        for (Address link : links) {
            if (!link.isAddress) {
                Block ref = getBlockByHash(link.getAddress(), false);
                if (ref == null || (ref.getInfo().flags & BI_MAIN_REF) != 0 || ref.getInfo().isSnapshot()) {
                    continue;
                }
                ref = getBlockByHash(link.getAddress(), true);
                if (ref == null) {
                    continue;
                }
                ref.getInfo().setFee(XAmount.ZERO);

                XAmount childGas = applyBlockHardened(false, ref);

                int refFlag = ref.getInfo().getFlags() & ~(BI_OURS | BI_REMARK);
                int executionState = 0;
                if (refFlag == (BI_REF | BI_MAIN_REF | BI_APPLIED)) {
                    executionState = 1; // 1C: applied
                } else if (refFlag == (BI_REF | BI_MAIN_REF)) {
                    executionState = 2; // 18: rejected
                }
                String blockType = isTxBlock(ref) ? "TxBlock  " : "LinkBlock";
                execLog.info("{} | Hash: {} | State: {}", blockType, ref.getHashLow().toHexString(), executionState);

                if (childGas.equals(SKIPPED)) {
                    blockStore.setTxSkippedBy(ref.getHashLow(), block.getHashLow());
                } else if (!childGas.equals(MINUS_ONE)) {
                    XAmount sum = addOrNull(gasCollected, childGas);
                    // fees are real money that was paid: their sum is bounded by the supply
                    gasCollected = sum == null ? gasCollected : sum;
                    updateBlockRef(ref, new Address(block));
                }
            }
        }

        if (!isTxBlock(block)) {
            // main block candidate or link block: no value of its own to move
            updateBlockFlag(block, BI_APPLIED, true);
            if (!flag) {
                block.getInfo().setFee(gasCollected);
                blockStore.saveBlockInfo(block.getInfo());
            }
            return gasCollected;
        }

        // ---- transaction block: check everything first ----
        Address accountInput = null;
        XAmount sumIn = XAmount.ZERO;
        XAmount sumOut = XAmount.ZERO;
        boolean fits = true;
        Map<Bytes32, XAmount> takenFromBlock = new HashMap<>();
        for (Address link : links) {
            MutableBytes32 linkAddress = link.getAddress();

            if (link.getType() == XDAG_FIELD_INPUT) {
                accountInput = link;
                byte[] account = BasicUtils.hash2byte(linkAddress).toArray();
                XAmount balance = addressStore.getBalanceByAddress(account);
                UInt64 executedNonce = addressStore.getExecutedNonceNum(account);
                if (block.getTxNonceField() == null) {
                    // cannot happen: an account transaction without a nonce field is refused on import
                    return XAmount.ZERO;
                }
                UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();

                if (blockNonce.compareTo(executedNonce.add(UInt64.ONE)) > 0) {
                    log.info("tx nonce error, tx nonce: {}, executed nonce: {},hash:{}", blockNonce, executedNonce, block.getHashLow().toHexString());
                    addressStore.updateTxQuantity(account, executedNonce);
                    return SKIPPED;
                }
                if (blockNonce.compareTo(executedNonce) <= 0) {
                    log.info("tx nonce is less than executed nonce,hash:{}", block.getHashLow().toHexString());
                    return SKIPPED;
                }
                if (compareAmountTo(balance, link.getAmount()) < 0) {
                    log.info("balance is less than amount,hash:{}", block.getHashLow().toHexString());
                    processNonceAfterTransactionExecution(link);
                    return XAmount.ZERO;
                }
                XAmount sum = addOrNull(sumIn, link.getAmount());
                fits &= sum != null;
                sumIn = sum == null ? sumIn : sum;

            } else if (link.getType() == XDAG_FIELD_IN) {
                Block ref = getBlockByHash(linkAddress, false);
                if (ref == null) {
                    return XAmount.ZERO;
                }
                // everything this transaction takes from that block, not just this one link
                XAmount taken = addOrNull(takenFromBlock.getOrDefault(Bytes32.wrap(linkAddress), XAmount.ZERO), link.getAmount());
                if (taken == null || compareAmountTo(ref.getInfo().getAmount(), taken) < 0) {
                    log.info("ref balance is less than amount");
                    return XAmount.ZERO;
                }
                takenFromBlock.put(Bytes32.wrap(linkAddress), taken);
                XAmount sum = addOrNull(sumIn, link.getAmount());
                fits &= sum != null;
                sumIn = sum == null ? sumIn : sum;

            } else {
                XAmount sum = addOrNull(sumOut, link.getAmount());
                fits &= sum != null;
                sumOut = sum == null ? sumOut : sum;
            }
        }

        XAmount available = addOrNull(block.getInfo().getAmount(), sumIn);
        if (!fits || available == null
                || compareAmountTo(available, sumOut) < 0
                || compareAmountTo(block.getInfo().getAmount(), XAmount.ZERO) < 0
                || compareAmountTo(sumIn, sumOut) != 0) {
            if (accountInput != null) {
                processNonceAfterTransactionExecution(accountInput);
            }
            log.info("block amount is not equal to sumIn - sumOut");
            return XAmount.ZERO;
        }

        // ---- move the value ----
        XAmount blockGas = XAmount.ZERO;
        XAmount outputFee = outPutLimit(block);
        for (Address link : links) {
            MutableBytes32 linkAddress = link.addressHash;
            if (!link.isAddress) {
                if (link.getType() == XDAG_FIELD_IN) {
                    Block ref = getBlockByHash(linkAddress, false);
                    subtractAndAccept(ref, link.getAmount());
                }
            } else {
                if (link.getType() == XDAG_FIELD_INPUT) {
                    subtractAmount(BasicUtils.hash2byte(linkAddress), link.getAmount(), block);
                    processNonceAfterTransactionExecution(link);
                } else if (link.getType() == XDAG_FIELD_OUTPUT) {
                    addAmount(BasicUtils.hash2byte(linkAddress), link.getAmount().subtract(outputFee), block);
                    blockGas = blockGas.add(outputFee);
                }
            }
        }

        updateBlockFlag(block, BI_APPLIED, true);
        recordTxHistory(block);

        if (!flag) {
            block.getInfo().setFee(blockGas);
            blockStore.saveBlockInfo(block.getInfo());
            return blockGas;
        } else {
            // If the transaction block has become the main block, then get blockGas; otherwise, return gasCollected.
            return ((gasCollected.compareTo(XAmount.ZERO) == 0) && (blockGas.compareTo(XAmount.ZERO) > 0)) ? blockGas : gasCollected;
        }
    }

    /**
     * Execute block and return gas fee (rules of xdagj 0.8.x, kept as they are for main blocks before the
     * open-network hardening fork so that the history of the network is reproduced exactly).
     */
    private XAmount applyBlockLegacy(boolean flag, Block block) {
        // Block already processed
        if ((block.getInfo().flags & BI_MAIN_REF) != 0) {
            return XAmount.ZERO.subtract(XAmount.ONE);
        }

        updateBlockFlag(block, BI_MAIN_REF, true);

        List<Address> links = block.getLinks();
        if (links == null || links.isEmpty()) {
            updateBlockFlag(block, BI_APPLIED, true);
            return XAmount.ZERO;
        }

        XAmount gasCollected = XAmount.ZERO;
        if (flag) {
            execLog.info("========== Main Block: {} ==========", block.getHashLow().toHexString());
        }
        for (Address link : links) {
            if (!link.isAddress) {
                Block ref = getBlockByHash(link.getAddress(), false);
                if ((ref.getInfo().flags & BI_MAIN_REF) != 0) continue;
                ref = getBlockByHash(link.getAddress(), true);
                ref.getInfo().setFee(XAmount.ZERO);

                XAmount childGas = applyBlockLegacy(false, ref);

                int refFlag = ref.getInfo().getFlags() & ~(BI_OURS | BI_REMARK);
                int executionState = 0;
                if (refFlag == (BI_REF | BI_MAIN_REF | BI_APPLIED)) {
                    executionState = 1; // 1C: applied
                } else if (refFlag == (BI_REF | BI_MAIN_REF)) {
                    executionState = 2; // 18: rejected
                }
                String blockType = isTxBlock(ref) ? "TxBlock  " : "LinkBlock";
                execLog.info("{} | Hash: {} | State: {}", blockType, ref.getHashLow().toHexString(), executionState);

                if (!childGas.equals(XAmount.ZERO.subtract(XAmount.ONE))) {
                    gasCollected = gasCollected.add(childGas);
                    updateBlockRef(ref, new Address(block));
                }
            }
        }

        // Input/output processing
        XAmount sumIn = XAmount.ZERO;
        XAmount sumOut = XAmount.ZERO;
        for (Address link : links) {
            MutableBytes32 linkAddress = link.getAddress();

            if (link.getType() == XDAG_FIELD_INPUT) {
                XAmount balance = addressStore.getBalanceByAddress(BasicUtils.hash2byte(linkAddress).toArray());
                UInt64 executedNonce = addressStore.getExecutedNonceNum(BasicUtils.hash2byte(linkAddress).toArray());
                UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();

                if (blockNonce.compareTo(executedNonce.add(UInt64.ONE)) > 0) {
                    log.info("tx nonce error, tx nonce: {}, executed nonce: {},hash:{}", blockNonce, executedNonce,block.getHashLow().toHexString());
                    addressStore.updateTxQuantity(BasicUtils.hash2byte(linkAddress).toArray(), executedNonce);
                    return XAmount.ZERO.subtract(XAmount.ONE);
                }
                if (blockNonce.compareTo(executedNonce) <= 0) {
                    log.info("tx nonce is less than executed nonce,hash:{}",block.getHashLow().toHexString());
                    return XAmount.ZERO.subtract(XAmount.ONE);
                }
                if (compareAmountTo(balance, link.amount) < 0) {
                    log.info("balance is less than amount,hash:{}",block.getHashLow().toHexString());
                    processNonceAfterTransactionExecution(link);
                    return XAmount.ZERO;
                }
                sumIn = sumIn.add(link.getAmount());

            } else if (link.getType() == XDAG_FIELD_IN) {
                Block ref = getBlockByHash(linkAddress, false);
                if (compareAmountTo(ref.getInfo().getAmount(), link.getAmount()) < 0) {
                    log.info("ref balance is less than amount");
                    return XAmount.ZERO;
                }
                sumIn = sumIn.add(link.getAmount());

            } else {
                sumOut = sumOut.add(link.getAmount());
            }
        }

        if (compareAmountTo(block.getInfo().getAmount().add(sumIn), sumOut) < 0 ||
                compareAmountTo(block.getInfo().getAmount(), XAmount.ZERO) < 0 ||
                compareAmountTo(sumIn, sumOut) != 0) {
            if (block.getInputs() != null) processNonceAfterTransactionExecution(block.getInputs().get(0));
            log.info("block amount is not equal to sumIn - sumOut");
            return XAmount.ZERO;
        }

        if(kernel.getSyncMgr() != null && (kernel.getSyncMgr().isSyncOld() || kernel.getSyncMgr().isSync()) && isTxBlock(block)){
            Byte executionStatus = getSyncTxStatus(block.getHashLow());
            if (executionStatus != null && executionStatus == 2){
                log.debug("Execute Synchronization of Node Transaction Status：{}",block.getHashLow().toHexString());
                return XAmount.ZERO.subtract(XAmount.ONE);
            }
        }else if(kernel.getSyncMgr() != null && !kernel.getSyncMgr().isSyncOld() && syncTxStatusCache.size() >0){
            clearAllSyncTxStatus();
        }

        // Actual amount processing
        XAmount blockGas = XAmount.ZERO;
        for (Address link : links) {
            MutableBytes32 linkAddress = link.addressHash;
            if (!link.isAddress) {
                Block ref = getBlockByHash(linkAddress, false);
                if (link.getType() == XDAG_FIELD_IN) {
                    subtractAndAccept(ref, link.getAmount());
                }
            } else {
                if (link.getType() == XDAG_FIELD_INPUT) {
                    subtractAmount(BasicUtils.hash2byte(linkAddress), link.getAmount(), block);
                    processNonceAfterTransactionExecution(link);
                } else if (link.getType() == XDAG_FIELD_OUTPUT) {
                    addAmount(BasicUtils.hash2byte(linkAddress), link.getAmount().subtract(outPutLimit(block)), block);
                    blockGas = blockGas.add(outPutLimit(block));
                }
            }
        }



        updateBlockFlag(block, BI_APPLIED, true);

//        XAmount totalFee = gasCollected.add(blockGas);
//        block.getInfo().setFee(totalFee);
        if (!flag && isTxBlock(block)) {
            block.getInfo().setFee(blockGas);
            blockStore.saveBlockInfo(block.getInfo());
            return blockGas;
        } else if (!flag && !isTxBlock(block)) {
            block.getInfo().setFee(gasCollected);
            blockStore.saveBlockInfo(block.getInfo());
            return gasCollected;
        } else {
            // If the transaction block has become the main block, then get blockGas; otherwise, return gasCollected.
            return ((gasCollected.compareTo(XAmount.ZERO) == 0) && (blockGas.compareTo(XAmount.ZERO) > 0)) ? blockGas : gasCollected;
        }
    }

    /**
     * Undo {@link #applyBlockHardened} exactly, in reverse: first what the block itself did, then the blocks that
     * were executed below it, last one first.
     *
     * @param flag true for the main block itself (its flags and fee are reset by unSetMain)
     */
    private void unApplyBlockHardened(Block block, boolean flag) {
        if ((block.getInfo().flags & BI_MAIN_REF) == 0) {
            return;
        }
        List<Address> links = block.getLinks();
        Collections.reverse(links); // must be reverse
        boolean skipped = blockStore.getTxSkippedBy(block.getHashLow()) != null;

        if ((block.getInfo().flags & BI_APPLIED) != 0) {
            if (isTxBlock(block)) {
                XAmount outputFee = outPutLimit(block);
                for (Address link : links) {
                    if (!link.isAddress) {
                        if (link.getType() == XDAG_FIELD_IN) {
                            Block ref = getBlockByHash(link.getAddress(), false);
                            if (ref != null) {
                                addAndAccept(ref, link.getAmount());
                            }
                        }
                    } else if (link.getType() == XDAG_FIELD_INPUT) {
                        addAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount(), block);
                        undoNonce(link);
                    } else if (link.getType() == XDAG_FIELD_OUTPUT) {
                        subtractAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount().subtract(outputFee), block);
                    }
                }
                forgetTxHistory(block);
            }
            updateBlockFlag(block, BI_APPLIED, false);
        } else if (!skipped && isAccountTx(block)) {
            // a rejected account transaction used up its nonce
            for (Address link : links) {
                if (link.isAddress && link.getType() == XDAG_FIELD_INPUT) {
                    undoNonce(link);
                }
            }
        }

        if (!flag) {
            block.getInfo().setFee(XAmount.ZERO);
            updateBlockFlag(block, BI_MAIN_REF, false);
            updateBlockRef(block, null);
            if (skipped) {
                blockStore.setTxSkippedBy(block.getHashLow(), null);
            }
        }

        for (Address link : links) {
            if (link.isAddress) {
                continue;
            }
            Block ref = getBlockByHash(link.getAddress(), false);
            if (ref == null || ref.getInfo().isSnapshot() || (ref.getInfo().flags & BI_MAIN_REF) == 0) {
                continue;
            }
            // only what was reached through this very block: executed below it, or skipped by it
            boolean executedHere = ref.getInfo().getRef() != null
                    && equalBytes(ref.getInfo().getRef(), block.getHashLow().toArray());
            boolean skippedHere = ref.getInfo().getRef() == null
                    && block.getHashLow().equals(blockStore.getTxSkippedBy(ref.getHashLow()));
            if (executedHere || skippedHere) {
                XAmount fee = ref.getFee();
                ref = getBlockByHash(ref.getHashLow(), true);
                if (ref == null) {
                    continue;
                }
                ref.getInfo().setFee(fee);
                unApplyBlockHardened(ref, false);
            }
        }
    }

    private void undoNonce(Address input) {
        byte[] address = BytesUtils.byte32ToArray(input.getAddress()).toArray();
        UInt64 exeNonce = addressStore.getExecutedNonceNum(address);
        if (exeNonce.isZero()) {
            return;
        }
        addressStore.updateExcutedNonceNum(address, false);
        addressStore.updateTxQuantity(address, exeNonce.subtract(UInt64.ONE));
    }

    // TODO: unapply block which in snapshot
    public void unApplyBlock(Block block, boolean flag) {
        if((block.getInfo().flags & BI_MAIN_REF) == 0 || block.getInfo().getRef() == null) {
            return;
        }
        List<Address> links = block.getLinks();
        Collections.reverse(links); // must be reverse
        if ((block.getInfo().flags & BI_APPLIED) != 0) {
            // TX block created by wallet or pool will not set fee = minGas, set here
//            if (!block.getInputs().isEmpty() && block.getFee().equals(XAmount.ZERO)) {
//                block.getInfo().setFee(getTxFee(block));
//            }
            for (Address link : links) {
                if (!link.isAddress) {
                    Block ref = getBlockByHash(link.getAddress(), false);
                    if (link.getType() == XDAG_FIELD_IN && ref != null) {
                        // Only input references to the main block transaction block will go through this.
                        addAndAccept(ref, link.getAmount());
                    }
                } else {
                    if (link.getType() == XDAG_FIELD_INPUT) {
                        addAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount(), block);
                        byte[] address = BytesUtils.byte32ToArray(link.getAddress()).toArray();
                        UInt64 exeNonce = addressStore.getExecutedNonceNum(address);
                        addressStore.updateExcutedNonceNum(address, false);
                        addressStore.updateTxQuantity(address, exeNonce.subtract(UInt64.ONE));
                        log.info("current nonce subtract one");
                    } else if (link.getType() == XDAG_FIELD_OUTPUT) {
                        // When add amount in 'Apply' subtract fee, so unApply also subtract fee
                        subtractAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount().subtract(block.getFee().divide(outPutNum(block))), block);
                    }
                }

            }

            updateBlockFlag(block, BI_APPLIED, false);
        } else {
            //When rolling back, the unaccepted transactions in the main block need to be processed, which is the number of confirmed transactions sent corresponding to their account addresses, nonce, needs to be reduced by one
            for(Address link : links) {
                if (link.isAddress && link.getType() == XDAG_FIELD_INPUT && block.getTxNonceField() != null){
                    Bytes address = byte32ToArray(link.getAddress());
                    UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();
                    UInt64 exeNonce = addressStore.getExecutedNonceNum(address.toArray());
                    if (blockNonce.compareTo(exeNonce) == 0) {
                        addressStore.updateExcutedNonceNum(address.toArray(), false);
                        addressStore.updateTxQuantity(address.toArray(), exeNonce.subtract(UInt64.ONE));
                        log.debug("The transaction processed quantity of account {} is reduced by one, and the number of transactions processed now is nonce = {}",
                                Base58.encodeCheck(BytesUtils.byte32ToArray(link.getAddress())), addressStore.getExecutedNonceNum(address.toArray()).intValue()
                        );
                    }

                }
            }
        }

        if (!flag) {
            block.getInfo().setFee(XAmount.ZERO);
            updateBlockFlag(block, BI_MAIN_REF, false);
            updateBlockRef(block, null);
        }

        for (Address link : links) {
            if (!link.isAddress) {
                Block ref = getBlockByHash(link.getAddress(), false);
                // A block inherited from a snapshot has no data (and was not executed by this main block): there
                // is nothing to undo below it. The code used to dereference the missing data, so unwinding the
                // first main blocks after a snapshot - which link to the top of the snapshot - blew up half-way.
                if (ref == null || getBlockByHash(ref.getHashLow(), true) == null) {
                    continue;
                }
                XAmount fee;
                // Even if mainBlock duplicate links the TX_block which other mainBlock handled, we can check if this TX ref is this mainBlock
                if (ref.getInfo().getRef() != null
                        && equalBytes(ref.getInfo().getRef(), block.getHashLow().toArray())
                        && ((ref.getInfo().flags & BI_MAIN_REF) != 0)) {
//                    addAndAccept(block, unApplyBlock(getBlockByHash(ref.getHashLow(), true)));
                    fee = ref.getFee();
                    ref = getBlockByHash(ref.getHashLow(), true);
                    ref.getInfo().setFee(fee);
                    unApplyBlock(ref, false);
                }
                // Remove the flag that was set for the transaction block with the nonce error, and restore it to the Pending state.
                fee = ref.getFee();
                ref = getBlockByHash(ref.getHashLow(), true);
                ref.getInfo().setFee(fee);
                if (isTxBlock(ref) && ref.getInfo().getRef() == null && (ref.getInfo().flags & BI_MAIN_REF) != 0) {
                    updateBlockFlag(ref, BI_MAIN_REF, false);
                }
            }
        }
    }

    /**
     * Set the main chain with block as the main block - either fork or extend
     */
    public void setMain(Block block) {
        synchronized (this) {
            // see BlockStore#setMainUpdateInProgress: a process killed in here leaves a detectable mark
            blockStore.setMainUpdateInProgress(true);
            try {
                doSetMain(block);
            } finally {
                blockStore.setMainUpdateInProgress(false);
            }
        }
    }

    private void doSetMain(Block block) {

        synchronized (this) {
            // Set reward
            long mainNumber = xdagStats.nmain + 1;
            log.debug("mainNumber = {},hash = {}", mainNumber, Hex.toHexString(block.getInfo().getHash()));
            XAmount reward = getReward(mainNumber);
            block.getInfo().setHeight(mainNumber);
            updateBlockFlag(block, BI_MAIN, true);

            // Accept reward
            acceptAmount(block, reward);
            xdagStats.nmain++;

            // Recursively execute blocks referenced by main block and get fees.
            // Which rules apply is decided by the main block alone (its epoch), so it is the same on every node.
            XAmount mainBlockFee = isHardenedExecution(block)
                    ? applyBlockHardened(true, block)
                    : applyBlockLegacy(true, block); //the mainBlock may have tx, return the fee to itself.
            if (mainBlockFee.compareTo(XAmount.ZERO) < 0) {// normal mainBlock will not go into this
                return;
            } else {
                acceptAmount(block, mainBlockFee); //add the fee
                block.getInfo().setFee(mainBlockFee);
                blockStore.saveBlockInfo(block.getInfo());
            }
            // Main block REF points to itself
            // TODO: Add fee
            updateBlockRef(block, new Address(block));

            if (randomx != null) {
                randomx.randomXSetForkTime(block);
            }

            latchOpenNet(block);
        }

    }

    /**
     * Cancel Block main block status
     */
    // TODO: Change to new way to cancel main block reward
    public void unSetMain(Block block) {
        synchronized (this) {
            blockStore.setMainUpdateInProgress(true);
            try {
                doUnSetMain(block);
            } finally {
                blockStore.setMainUpdateInProgress(false);
            }
        }
    }

    private void doUnSetMain(Block block) {

        synchronized (this) {

            log.debug("UnSet main,{}, mainnumber = {}", block.getHash().toHexString(), xdagStats.nmain);

            XAmount reward = getReward(block.getInfo().getHeight());
            updateBlockFlag(block, BI_MAIN, false);

            xdagStats.nmain--;

            acceptAmount(block, XAmount.ZERO.subtract(reward));
            if (isHardenedExecution(block)) {
                unApplyBlockHardened(block, true);
            } else {
                unApplyBlock(block, true);
            }

            acceptAmount(block, XAmount.ZERO.subtract(block.getFee()));
            if (randomx != null) {
                randomx.randomXUnsetForkTime(block);
            }
            block.getInfo().setFee(XAmount.ZERO);
            block.getInfo().setHeight(0);
            updateBlockFlag(block, BI_MAIN_REF, false);
            updateBlockRef(block, null);
        }
    }

    public void processNonceAfterTransactionExecution(Address link) {
        if (link.getType() != XDAG_FIELD_INPUT) {
            return;
        }
        Bytes address = BytesUtils.byte32ToArray(link.getAddress());
        addressStore.updateExcutedNonceNum(address.toArray(), true);
        UInt64 currentTxNonce = addressStore.getTxQuantity(address.toArray());
        UInt64 currentExeNonce = addressStore.getExecutedNonceNum(address.toArray());
        addressStore.updateTxQuantity(address.toArray(), currentTxNonce, currentExeNonce);
    }

    @Override
    public Block createNewBlock(
            Map<Address, ECKeyPair> pairs,
            List<Address> to,
            boolean mining,
            String remark,
            XAmount fee,
            UInt64 txNonce
    ) {

        int hasRemark = remark == null ? 0 : 1;

        if (pairs == null && to == null) {
            if (mining) {
                return createMainBlock();
            } else {
                return createLinkBlock(remark, false);
            }
        }
        int defKeyIndex = -1;

        // Check all keys to see if there is a default key
        assert pairs != null;
        List<ECKeyPair> keys = new ArrayList<>(Set.copyOf(pairs.values()));
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(wallet.getDefKey())) {
                defKeyIndex = i;
            }
        }

        List<Address> all = Lists.newArrayList();
        all.addAll(pairs.keySet());
        all.addAll(to);

        // TODO: Check if pairs have duplicates
        int res;
        if (txNonce != null) {
            res = 1 + 1 + pairs.size() + to.size() + 3 * keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;
        } else {
            res = 1 + pairs.size() + to.size() + 3 * keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;
        }

        // TODO: If block fields are insufficient
        if (res > 16) {
            return null;
        }
        long[] sendTime = new long[2];
        sendTime[0] = XdagTime.getCurrentTimestamp();
        List<Address> refs = Lists.newArrayList();

        return new Block(kernel.getConfig(), sendTime[0], all, refs, mining, keys, remark, defKeyIndex, fee, txNonce);
    }

    public Block createMainBlock() {
        // <header + remark + outsig + nonce>
        int res = 1 + 1 + 2 + 1;
        long[] sendTime = new long[2];
        sendTime[0] = XdagTime.getMainTime();
        Address preTop = null;
        Bytes32 pretopHash = getPreTopMainBlockForLink(sendTime[0]);
        if (pretopHash != null) {
            preTop = new Address(Bytes32.wrap(pretopHash), XdagField.FieldType.XDAG_FIELD_OUT, false);
            res++;
        }
        // The coinbase address of the block defaults to the default address of the node wallet
        Address coinbase = new Address(keyPair2Hash(wallet.getDefKey()),
                FieldType.XDAG_FIELD_COINBASE,
                true);
        List<Address> refs = Lists.newArrayList();
        if (preTop != null) {
            refs.add(preTop);
        }

        if (coinbase == null) {
            throw new ArithmeticException("Invalidate main block!");
        }
        refs.add(coinbase);
        res++;

        List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime, true);
        if (CollectionUtils.isNotEmpty(orphans)) {
            refs.addAll(orphans);
        }
        return new Block(kernel.getConfig(), sendTime[0], null, refs, true, null,
                kernel.getConfig().getNodeSpec().getNodeTag(), -1, XAmount.ZERO, null);
    }

    public Block createLinkBlock(String remark, boolean isRoll) {
        // <header + remark + outsig + nonce>
        int hasRemark = remark == null ? 0 : 1;
        int res = 1 + hasRemark + 2;
        long[] sendTime = new long[2];
        sendTime[0] = XdagTime.getCurrentTimestamp();

        List<Address> refs = Lists.newArrayList();
        if (isRoll) {
            for (int i = 16 - res; i > 0 && CollectionUtils.isNotEmpty(rollTxList); i--) {
                refs.add(new Address(rollTxList.getFirst().getHashLow(), FieldType.XDAG_FIELD_OUT, false));
                sendTime[1] = Math.max(sendTime[1], rollTxList.getFirst().getTimestamp());
                rollTxList.removeFirst();
            }
            sendTime[1] = Math.min(sendTime[1] + 1, sendTime[0]);
            log.debug("rollTxList.size:{}", rollTxList.size());
        } else {
            List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime, false);
            if (CollectionUtils.isNotEmpty(orphans)) {
                refs.addAll(orphans);
            }
        }

        return new Block(kernel.getConfig(), sendTime[1], null, refs, false, null,
                remark, -1, XAmount.ZERO, null);
    }

    /**
     * Get a certain number of orphan blocks from orphan pool for linking
     */
    public List<Address> getBlockFromOrphanPool(int num, long[] sendtime, boolean isMain) {
        return orphanBlockStore.getOrphan(num, sendtime, isMain);
    }

    public Bytes32 getPreTopMainBlockForLink(long sendTime) {
        long mainTime = XdagTime.getEpoch(sendTime);
        Block topInfo;
        if (xdagTopStatus.getTop() == null) {
            return null;
        }

        topInfo = getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), false);
        if (topInfo == null) {
            return null;
        }
        if (XdagTime.getEpoch(topInfo.getTimestamp()) == mainTime) {
            log.debug("use pretop:{}", Bytes32.wrap(xdagTopStatus.getPreTop()).toHexString());
            return Bytes32.wrap(xdagTopStatus.getPreTop());
        } else {
            log.debug("use top:{}", Bytes32.wrap(xdagTopStatus.getTop()).toHexString());
            return Bytes32.wrap(xdagTopStatus.getTop());
        }
    }

    /**
     * Update pretop
     *
     * @param target     target block
     * @param targetDiff difficulty of block
     */
    public void setPreTop(Block target, BigInteger targetDiff) {
        if (target == null) {
            return;
        }

        // Make sure the target's epoch is earlier than current top's epoch
        Block block = getBlockByHash(xdagTopStatus.getTop() == null ? null :
                Bytes32.wrap(xdagTopStatus.getTop()), false);
        if (block != null) {
            if (XdagTime.getEpoch(target.getTimestamp()) >= XdagTime.getEpoch(block.getTimestamp())) {
                return;
            }
        }

        // If pretop is null, then update pretop to target
        if (xdagTopStatus.getPreTop() == null) {
            xdagTopStatus.setPreTop(target.getHashLow().toArray());
            xdagTopStatus.setPreTopDiff(targetDiff);
            target.setPretopCandidate(true);
            target.setPretopCandidateDiff(targetDiff);
            return;
        }

        // If targetDiff greater than pretop diff, then update pretop to target
        if (targetDiff.compareTo(xdagTopStatus.getPreTopDiff()) > 0) {
            log.debug("update pretop:{}", Bytes32.wrap(target.getHashLow()).toHexString());
            xdagTopStatus.setPreTop(target.getHashLow().toArray());
            xdagTopStatus.setPreTopDiff(targetDiff);
            target.setPretopCandidate(true);
            target.setPretopCandidateDiff(targetDiff);
        }
    }

    /**
     * Calculate current block difficulty
     */
    public BigInteger calculateCurrentBlockDiff(Block block) {
        if (block == null) {
            return BigInteger.ZERO;
        }
        if (block.getInfo().getDifficulty() != null) {
            return block.getInfo().getDifficulty();
        }
        if (isHardenedBlock(block)) {
            return calculateHardenedBlockDiff(block);
        }

        //TX block would not set diff, fix a diff = 1;
        if (!block.getInputs().isEmpty()) {
            return BigInteger.ONE;
        }

        BigInteger blockDiff;
        // Set initial block difficulty
        if (randomx != null && randomx.isRandomxFork(XdagTime.getEpoch(block.getTimestamp()))
                && XdagTime.isEndOfEpoch(block.getTimestamp())) {
            blockDiff = getDiffByRandomXHash(block);
        } else {
            blockDiff = getDiffByRawHash(block.getHash());
        }

        return blockDiff;
    }

    /**
     * Own difficulty of a block under the open-network hardening fork: only proof of work counts.
     * <p>
     * The legacy rule gives every block without inputs the difficulty of its sha256d hash, and every transaction
     * block a difficulty of 1. Since the RandomX fork only the block at the end of an epoch is mined with
     * RandomX, so anyone with SHA-256 hardware could grind an ordinary block in the middle of an epoch (or one
     * with a timestamp from before the RandomX fork) until it outweighs the RandomX main blocks: it would take the
     * main block of its epoch, or - attached to an old main block - unwind the chain. A transaction block linked
     * to the top becomes the main block of an epoch nobody mined in. Only the closed set of peers prevented this.
     * <p>
     * Hardened rule: a block has a difficulty of its own only if it is a main block candidate (timestamp at the
     * end of an epoch, no inputs) and then only for the proof of work that is valid for its epoch.
     */
    private BigInteger calculateHardenedBlockDiff(Block block) {
        if (!block.getInputs().isEmpty() || !XdagTime.isEndOfEpoch(block.getTimestamp())) {
            return BigInteger.ZERO;
        }
        if (randomx == null) {
            // RandomX is not part of this chain at all (unit tests)
            return getDiffByRawHash(block.getHash());
        }
        long epoch = XdagTime.getEpoch(block.getTimestamp());
        return switch (randomx.powOf(epoch, latestMainEpoch())) {
            case RANDOMX -> {
                BigInteger diff = getRandomXDiff(block, epoch);
                yield diff == null ? BigInteger.ZERO : diff;
            }
            case SHA256D -> getDiffByRawHash(block.getHash());
            case NONE -> BigInteger.ZERO;
        };
    }

    @Override
    public boolean usesRandomX(long epoch) {
        if (randomx == null) {
            return false;
        }
        if (openNetLatched || epoch >= openNetForkEpoch) {
            return randomx.powOf(epoch, latestMainEpoch()) == RandomX.Pow.RANDOMX;
        }
        return randomx.isRandomxFork(epoch);
    }

    /**
     * Epoch of the latest main block of this node, 0 if there is none.
     */
    private long latestMainEpoch() {
        if (xdagStats.nmain <= 0) {
            return 0;
        }
        Block main = blockStore.getBlockByHeight(xdagStats.nmain);
        return main == null ? 0 : XdagTime.getEpoch(main.getTimestamp());
    }

    /**
     * RandomX difficulty of a main block candidate, or null if this node has no seed for the epoch.
     */
    private BigInteger getRandomXDiff(Block block, long epoch) {
        MutableBytes data = MutableBytes.create(64);
        Bytes32 rxHash = HashUtils.sha256(block.getXdagBlock().getData().slice(0, 512 - 32));
        data.set(0, rxHash);
        data.set(32, block.getXdagBlock().getField(15).getData());
        byte[] blockHash = randomx.randomXBlockHash(data.toArray(), epoch);
        return blockHash == null ? null : getDiffByRawHash(Bytes32.wrap(Arrays.reverse(blockHash)));
    }

    /**
     * Set block difficulty and max difficulty connection and return block difficulty
     */
    public BigInteger calculateBlockDiff(Block block, BigInteger cuDiff) {
        if (block == null) {
            return BigInteger.ZERO;
        }
        if (block.getInfo().getDifficulty() != null) {
            return block.getInfo().getDifficulty();
        }

        block.getInfo().setDifficulty(cuDiff);

        BigInteger maxDiff = cuDiff;
        Address maxDiffLink = null;

        // Temporary block
        Block tmpBlock;
        if (block.getLinks().isEmpty()) {
            return cuDiff;
        }

        // Traverse all links to find maxLink
        List<Address> links = block.getLinks();
        for (Address ref : links) {
            /*
             * Only Blocks have difficulty
             */
            if (!ref.isAddress) {
                Block refBlock = getBlockByHash(ref.getAddress(), false);
                if (refBlock == null) {
                    break;
                }
                // If the referenced block's epoch is less than current block's round
                if (XdagTime.getEpoch(refBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp())) {
                    // If difficulty is greater than current max difficulty
                    BigInteger refDifficulty = refBlock.getInfo().getDifficulty();
                    if (refDifficulty == null) {
                        refDifficulty = BigInteger.ZERO;
                    }
                    BigInteger curDiff = refDifficulty.add(cuDiff);
                    if (curDiff.compareTo(maxDiff) > 0) {
                        maxDiff = curDiff;
                        maxDiffLink = ref;
                    }
                } else {
                    // Calculated diff
                    // 1. maxDiff+diff0 for different epochs
                    // 2. maxDiff for same epoch
                    tmpBlock = refBlock; // tmpBlock is from link
                    BigInteger curDiff = refBlock.getInfo().getDifficulty();
                    while ((tmpBlock != null)
                            && XdagTime.getEpoch(tmpBlock.getTimestamp()) == XdagTime.getEpoch(block.getTimestamp())) {
                        tmpBlock = getMaxDiffLink(tmpBlock, false);
                    }
                    if (tmpBlock != null
                            && (XdagTime.getEpoch(tmpBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp()))
                            && tmpBlock.getInfo().getDifficulty().add(cuDiff).compareTo(curDiff) > 0
                    ) {
                        curDiff = tmpBlock.getInfo().getDifficulty().add(cuDiff);
                    }
                    if (curDiff == null) {
                        curDiff = BigInteger.ZERO;
                    }
                    if (curDiff.compareTo(maxDiff) > 0) {
                        maxDiff = curDiff;
                        maxDiffLink = ref;
                    }
                }
            }
        }

        block.getInfo().setDifficulty(maxDiff);

        if (maxDiffLink != null) {
            block.getInfo().setMaxDiffLink(maxDiffLink.getAddress().toArray());
        }
        return maxDiff;
    }

    public BigInteger getDiffByRandomXHash(Block block) {
        long epoch = XdagTime.getEpoch(block.getTimestamp());
        MutableBytes data = MutableBytes.create(64);
        Bytes32 rxHash = HashUtils.sha256(block.getXdagBlock().getData().slice(0, 512 - 32));
        data.set(0, rxHash);
        data.set(32, block.getXdagBlock().getField(15).getData());
        byte[] blockHash = randomx.randomXBlockHash(data.toArray(), epoch);
        BigInteger diff;
        if (blockHash != null) {
            Bytes32 hash = Bytes32.wrap(Arrays.reverse(blockHash));
            diff = getDiffByRawHash(hash);
        } else {
            diff = getDiffByRawHash(block.getHash());
        }
        log.debug("block diff:{}, ", diff);
        return diff;
    }

    public BigInteger getDiffByRawHash(Bytes32 hash) {
        return getDiffByHash(hash);
    }

    // ADD: Get block by height using new version
    public Block getBlockByHeightNew(long height) {
        // TODO: if snapshot enabled, need height > snapshotHeight - 128
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled() && (height < snapshotHeight - 128)
                && !kernel.getConfig().getSnapshotSpec().isSnapshotJ()) {
            return null;
        }
        // Return null if height is less than 0
        if (height > xdagStats.nmain || height <= 0) {
            return null;
        }
        return blockStore.getBlockByHeight(height);
    }

    @Override
    public Block getBlockByHeight(long height) {
        return getBlockByHeightNew(height);
    }

    @Override
    public Block getBlockByHash(Bytes32 hashlow, boolean isRaw) {
        if (hashlow == null) {
            return null;
        }
        // Ensure that hashlow is hashlow
        MutableBytes32 keyHashlow = MutableBytes32.create();
        keyHashlow.set(8, Objects.requireNonNull(hashlow).slice(8, 24));

        Block b = memOrphanPool.get(Bytes32.wrap(keyHashlow));
        if (b == null) {
            b = blockStore.getBlockByHash(keyHashlow, isRaw);
        }
        return b;
    }

    public Block getMaxDiffLink(Block block, boolean isRaw) {
        if (block.getInfo().getMaxDiffLink() != null) {
            return getBlockByHash(Bytes32.wrap(block.getInfo().getMaxDiffLink()), isRaw);
        }
        return null;
    }

    public void removeOrphan(Bytes32 hashlow, OrphanRemoveActions action) {
        Block b = getBlockByHash(hashlow, false);
        // TODO: snapshot
        if (b != null && b.getInfo() != null && b.getInfo().isSnapshot()) {
            return;
        }
        if (b != null && ((b.getInfo().flags & BI_REF) == 0) && (action != OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                || (b.getInfo().flags & BI_EXTRA) != 0)) {
            // If removeBlock is BI_EXTRA
            if ((b.getInfo().flags & BI_EXTRA) != 0) {
                // Then removeBlockInfo is complete
                // Remove from MemOrphanPool
                Bytes key = b.getHashLow();
                Block removeBlockRaw = memOrphanPool.get(key);
                memOrphanPool.remove(key);
                if (action != OrphanRemoveActions.ORPHAN_REMOVE_REUSE) {
                    // Save block
                    saveBlock(removeBlockRaw);
                    // Remove all blocks linked by EXTRA block
                    if (removeBlockRaw != null) {
                        List<Address> all = removeBlockRaw.getLinks();
                        for (Address addr : all) {
                            removeOrphan(addr.getAddress(), OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                        }
                    }
                }
                // Update removeBlockRaw flag
                // Decrement nextra
                updateBlockFlag(removeBlockRaw, BI_EXTRA, false);
                xdagStats.nextra--;
            } else {
                // The block with its data is only needed to find it in the pool. The flag below is set on the
                // stored info: a block parsed from its data carries the fee of its header, not the fee that was
                // recorded when it was executed, and saving that would overwrite the recorded fee. (This happens
                // when a transaction that was already executed is referred to again after rollTx() has cleared
                // its BI_REF flag.)
                Block raw = getBlockByHash(b.getHashLow(), true);
                if (raw != null) {
                    List<Address> in = raw.getInputs();
                    UInt64 nonce = UInt64.ZERO;
                    XAmount fee = getTxFee(raw);
                    byte[] address = null;
                    if (isAccountTx(raw)) {
                        for(Address ref : in) {
                            if (ref.getType().equals(XDAG_FIELD_INPUT)) {
                                address = BytesUtils.byte32ToArray(ref.getAddress()).toArray();
                                nonce = raw.getTxNonceField().getTransactionNonce();
                                break;
                            }
                        }
                    }

                    orphanBlockStore.deleteFromQueue(raw, isTxBlock(raw), nonce, fee, address);
                    orphanBlockStore.deleteByKey(raw.getHashLow().toArray(), isTxBlock(raw), nonce, fee, address);
                }
                decrementNnoref();
            }
            // Update this block's flag
            updateBlockFlag(b, BI_REF, true);
        }
    }

    public void updateBlockFlag(Block block, byte flag, boolean direction) {
        if (block == null) {
            return;
        }
        if (direction) {
            block.getInfo().setFlags(block.getInfo().flags |= flag);
        } else {
            block.getInfo().setFlags(block.getInfo().flags &= ~flag);
        }
        if (block.isSaved) {
            if (!block.getInfo().getFee().equals(XAmount.ZERO)) {
                Block blockInfo = getBlockByHash(block.getHashLow(), false);
                block.getInfo().setFee(blockInfo.getFee());
            }
            blockStore.saveBlockInfo(block.getInfo());
        }
    }

    public void updateBlockRef(Block block, Address ref) {
        if (ref == null) {
            block.getInfo().setRef(null);
        } else {
            block.getInfo().setRef(ref.getAddress().toArray());
        }
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
    }

    public void saveBlock(Block block) {
        if (block == null) {
            return;
        }
        block.isSaved = true;
        blockStore.saveBlock(block);
        // If it's our account
        if (memOurBlocks.containsKey(block.getHash())) {
//            log.info("new account:{}", Hex.toHexString(block.getHash()));
            if (xdagStats.getOurLastBlockHash() == null) {
                blockStore.saveXdagStatus(xdagStats);
            }
            addOurBlock(memOurBlocks.get(block.getHash()), block);
            memOurBlocks.remove(block.getHash());
        }

        if (block.isPretopCandidate()) {
            xdagTopStatus.setPreTop(block.getHashLow().toArray());
            xdagTopStatus.setPreTopDiff(block.getPretopCandidateDiff());
            blockStore.saveXdagTopStatus(xdagTopStatus);
        }

    }

    public boolean isExtraBlock(Block block) {
        return (block.getTimestamp() & 0xffff) == 0xffff && block.getNonce() != null && !block.isSaved();
    }

    @Override
    public XdagStats getXdagStats() {
        return this.xdagStats;
    }

    public boolean canUseInput(Block block) {
        List<PublicKey> keys = block.verifiedKeys();
        List<Address> inputs = block.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return true;
        }
        /*
         * While "in" isn't address, need to verify signature
         */
        // TODO: Verify signature for non-address inputs
        for (Address in : inputs) {
            if (!in.isAddress) {
                if (!verifySignature(in, keys)) {
                    return false;
                }
            } else {
                if (!verifyBlockSignature(in, keys)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean verifyBlockSignature(Address in, List<PublicKey> keys) {
        Bytes pubHash = in.getAddress().mutableCopy().slice(8, 20);
        for (PublicKey key : keys) {
            if (pubHash.equals(toBytesAddress(key))) return true;
        }
        return false;
    }

    private boolean verifySignature(Address in, List<PublicKey> publicKeys) {
        // TODO: Check if block is in snapshot, get blockinfo with isRaw=false
        Block block = getBlockByHash(in.getAddress(), false);
        boolean isSnapshotBlock = block.getInfo().isSnapshot();
        if (isSnapshotBlock) {
            return verifySignatureFromSnapshot(in, publicKeys);
        } else {
            Block inBlock = getBlockByHash(in.getAddress(), true);
            MutableBytes subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);
//            log.debug("verify encoded:{}", Hex.toHexString(subdata));
            Signature sig = inBlock.getOutsig();
            return verifySignature(subdata, sig, publicKeys, block.getInfo());
        }
    }

    // TODO: When input is a block in snapshot, need to verify snapshot's public key or signature data
    private boolean verifySignatureFromSnapshot(Address in, List<PublicKey> publicKeys) {
        BlockInfo blockInfo = blockStore.getBlockInfoByHash(in.getAddress()).getInfo();
        SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
        if (snapshotInfo.getType()) {
            // snapshotInfo.getData() contains 33-byte compressed public key format
            try {
                PublicKey targetPublicKey = PublicKey.fromBytes(snapshotInfo.getData());
                for (PublicKey publicKey : publicKeys) {
                    if (publicKey.equals(targetPublicKey)) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                // If public key parsing fails, verification fails
                return false;
            }
        } else {
            Block block = getBlockByHash(in.getAddress(), false);
            block.setXdagBlock(new XdagBlock(snapshotInfo.getData()));
            block.setParsed(false);
            block.parse();
            MutableBytes subdata = block.getSubRawData(block.getOutsigIndex() - 2);
            Signature sig = block.getOutsig();
            // Check if signature is canonical to prevent signature malleability attacks
            if (!sig.isCanonical()) {
                return false; // Reject non-canonical signatures
            }
            return verifySignature(subdata, sig, publicKeys, blockInfo);
        }


    }

    private boolean verifySignature(MutableBytes subdata, Signature sig, List<PublicKey> publicKeys, BlockInfo blockInfo) {
        for (PublicKey publicKey : publicKeys) {
            byte[] publicKeyBytes = publicKey.toBytes().toArray();
            Bytes digest = Bytes.wrap(subdata, Bytes.wrap(publicKeyBytes));
//            log.debug("verify encoded:{}", Hex.toHexString(digest));
            Bytes32 hash = HashUtils.doubleSha256(digest);
            if (Signer.verify(hash, sig, publicKey)) {
                SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
                byte[] pubkeyBytes = publicKey.toBytes().toArray();
                if (snapshotInfo != null) {
                    snapshotInfo.setData(pubkeyBytes);
                    snapshotInfo.setType(true);
                } else {
                    blockInfo.setSnapshotInfo(new SnapshotInfo(true, pubkeyBytes));
                }
                blockStore.saveBlockInfo(blockInfo);
                return true;
            }
        }
        return false;
    }

    public boolean checkMineAndAdd(Block block) {
        List<ECKeyPair> ourkeys = wallet.getAccounts();
        // Only one output signature
        Signature signature = block.getOutsig();
        // Iterate through all keys
        for (int i = 0; i < ourkeys.size(); i++) {
            ECKeyPair ecKey = ourkeys.get(i);
            // TODO: Optimize
            byte[] publicKeyBytes = ecKey.getPublicKey().toBytes().toArray();
            Bytes digest = Bytes.wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
            Bytes32 hash = HashUtils.doubleSha256(Bytes.wrap(digest));
            // Use hyperledger besu crypto native secp256k1
            if (Signer.verify(hash, signature, ecKey.getPublicKey())) {
                log.debug("verify block success hash={}.", hash.toHexString());
                addOurBlock(i, block);
                return true;
            }
        }
        return false;
    }

    public void addOurBlock(int keyIndex, Block block) {
        xdagStats.setOurLastBlockHash(block.getHash().toArray());
        if (!block.isSaved()) {
            memOurBlocks.put(block.getHash(), keyIndex);
        } else {
            blockStore.saveOurBlock(keyIndex, block.getInfo().getHashlow());
        }
    }

    public void removeOurBlock(Block block) {
        if (!block.isSaved) {
            memOurBlocks.remove(block.getHash());
        } else {
            blockStore.removeOurBlock(block.getHashLow().toArray());
        }
    }

    public XAmount getReward(long nmain) {
        XAmount start = getStartAmount(nmain);
        long nanoAmount = start.toXAmount().toLong();
        return XAmount.ofXAmount(nanoAmount >> (nmain >> MAIN_BIG_PERIOD_LOG));
    }

    @Override
    public XAmount getSupply(long nmain) {
        UnsignedLong res = UnsignedLong.ZERO;
        XAmount amount = getStartAmount(nmain);
        long nanoAmount = amount.toXAmount().toLong();
        long current_nmain = nmain;
        while ((current_nmain >> MAIN_BIG_PERIOD_LOG) > 0) {
            res = res.plus(UnsignedLong.fromLongBits(1L << MAIN_BIG_PERIOD_LOG).times(long2UnsignedLong(nanoAmount)));
            current_nmain -= 1L << MAIN_BIG_PERIOD_LOG;
            nanoAmount >>= 1;
        }
        res = res.plus(long2UnsignedLong(current_nmain).times(long2UnsignedLong(nanoAmount)));
        long fork_height = kernel.getConfig().getApolloForkHeight();
        if (nmain >= fork_height) {
            // Add before apollo amount
            XAmount diff = kernel.getConfig().getMainStartAmount().subtract(kernel.getConfig().getApolloForkAmount());
            long nanoDiffAmount = diff.toXAmount().toLong();
            res = res.plus(long2UnsignedLong(fork_height - 1).times(long2UnsignedLong(nanoDiffAmount)));
        }
        return XAmount.ofXAmount(res.longValue());
    }

    @Override
    public List<Block> getBlocksByTime(long starttime, long endtime) {
        return blockStore.getBlocksUsedTime(starttime, endtime);
    }

    @Override
    public void startCheckMain(long period) {
        if (checkLoop == null) {
            return;
        }
        checkLoopFuture = checkLoop.scheduleAtFixedRate(this::checkState, 0, period, TimeUnit.MILLISECONDS);
    }

    public void checkState() {
        // Prohibit Non-mining nodes generate link blocks
        xdagStats.setNnoref(orphanBlockStore.getOrphanSize());
        if (kernel.getConfig().getEnableGenerateBlock() &&
                (kernel.getXdagState() == XdagState.SDST || XdagState.STST == kernel.getXdagState() || XdagState.SYNC == kernel.getXdagState())) {
            checkOrphan();
        }
        checkMain();
    }

    public void checkOrphan() {
        long nblk = xdagStats.nnoref / 11;
        if (nblk > 0) {
            boolean b = (nblk % 61) > CryptoProvider.nextLong(0, 61);
            nblk = nblk / 61 + (b ? 1 : 0);
        }
        while (nblk-- > 0) {
            Block linkBlock = createNewBlock(null, null, false,
                    kernel.getConfig().getNodeSpec().getNodeTag(), XAmount.ZERO, null);
            linkBlock.signOut(kernel.getWallet().getDefKey());
            ImportResult result = this.tryToConnect(new Block(linkBlock.getXdagBlock()));
            if (result == IMPORTED_NOT_BEST || result == IMPORTED_BEST) {
                onNewBlock(linkBlock);
            }
        }
    }

    public void checkMain() {
        try {
            checkNewMain();
            // xdagStats state will change after checkNewMain
            blockStore.saveXdagStatus(xdagStats);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void stopCheckMain() {
        try {

            if (checkLoopFuture != null) {
                checkLoopFuture.cancel(true);
            }
            // Shutdown thread pool
            checkLoop.shutdownNow();
            checkLoop.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public XAmount getStartAmount(long nmain) {
        XAmount startAmount;
        long forkHeight = kernel.getConfig().getApolloForkHeight();
        if (nmain >= forkHeight) {
            startAmount = kernel.getConfig().getApolloForkAmount();
        } else {
            startAmount = kernel.getConfig().getMainStartAmount();
        }

        return startAmount;
    }

    /**
     * Add amount to block
     */
    // TODO: Accept amount to block which in snapshot
    private void addAndAccept(Block block, XAmount amount) {
        XAmount oldAmount = block.getInfo().getAmount();
        try {
            block.getInfo().setAmount(block.getInfo().getAmount().add(amount));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  block {}", oldAmount, amount, block.getHashLow().toHexString());
        }
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(amount.add(xdagStats.getBalance()));
        }
        XAmount finalAmount = blockStore.getBlockInfoByHash(block.getHashLow()).getInfo().getAmount();
        log.debug("Balance checker —— block:{} [old:{} add:{} fin:{}]",
                block.getHashLow().toHexString(),
                oldAmount.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
    }

    private void subtractAndAccept(Block block, XAmount amount) {
        XAmount oldAmount = block.getInfo().getAmount();
        try {
            block.getInfo().setAmount(block.getInfo().getAmount().subtract(amount));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  block {}", oldAmount, amount, block.getHashLow().toHexString());
        }
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(xdagStats.getBalance().subtract(amount));
        }
        XAmount finalAmount = blockStore.getBlockInfoByHash(block.getHashLow()).getInfo().getAmount();
        log.debug("Balance checker —— block:{} [old:{} sub:{} fin:{}]",
                block.getHashLow().toHexString(),
                oldAmount.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
    }

    /**
     * "XDAG in address" statistic. It is adjusted exactly where an address balance changes, so executing and
     * undoing a transaction cancel out (the two used to be computed by different formulas and drifted apart).
     */
    private void adjustAddressTotal(XAmount delta) {
        try {
            addressStore.updateAllBalance(addressStore.getAllBalance().add(delta));
        } catch (Exception e) {
            log.debug("address total not updated: {}", e.getMessage());
        }
    }

    private void subtractAmount(Bytes addressHash, XAmount amount, Block block) {
        XAmount balance = addressStore.getBalanceByAddress(addressHash.toArray());
        try {
            addressStore.updateBalance(addressHash.toArray(), balance.subtract(amount));
            adjustAddressTotal(amount.negate());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  addressHsh {}  block {}", balance, amount, Base58.encodeCheck(addressHash), block.getHashLow());
        }
        XAmount finalAmount = addressStore.getBalanceByAddress(addressHash.toArray());
        log.debug("Balance checker —— Address:{} [old:{} sub:{} fin:{}]",
                Base58.encodeCheck(addressHash),
                balance.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(xdagStats.getBalance().subtract(amount));
        }
    }

    private void addAmount(Bytes addressHash, XAmount amount, Block block) {
        XAmount balance = addressStore.getBalanceByAddress(addressHash.toArray());
        try {
            addressStore.updateBalance(addressHash.toArray(), balance.add(amount));
            adjustAddressTotal(amount);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  addressHsh {}  block {}", balance, amount, Base58.encodeCheck(addressHash), block.getHashLow());
        }
        XAmount finalAmount = addressStore.getBalanceByAddress(addressHash.toArray());
        log.warn("Balance checker —— Address:{} [old:{} add:{} fin:{}]",
                Base58.encodeCheck(addressHash),
                balance.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(amount.add(xdagStats.getBalance()));
        }
    }

    // TODO: Accept amount to block which in snapshot
    private void acceptAmount(Block block, XAmount amount) {
        XAmount oldAmount = block.getInfo().getAmount();
        block.getInfo().setAmount(block.getInfo().getAmount().add(amount));
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
        XAmount finalAmount = blockStore.getBlockByHash(block.getHashLow(), false).getInfo().getAmount();
        log.warn("Balance checker —— Block:{} [old:{} acc:{} fin:{}]",
                block.getHashLow().toHexString(),
                oldAmount.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(amount.add(xdagStats.getBalance()));
        }
    }

    /**
     * Check if block already exists
     */
    public boolean isExist(Bytes32 hashlow) {
        return blockStore.hasBlock(hashlow) || isExitInSnapshot(hashlow);
    }

    public boolean isExistInMem(Bytes32 hashlow) {
        return memOrphanPool.containsKey(hashlow);
    }

    /**
     * Check if exists in snapshot
     */
    public boolean isExitInSnapshot(Bytes32 hashlow) {
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()) {
            // Query block from public key snapshot and signature snapshot
            return blockStore.hasBlockInfo(hashlow);
        } else {
            return false;
        }
    }


    // ADD: Get main blocks using new version method
    public List<Block> listMainBlocksByHeight(int count) {
        List<Block> res = new ArrayList<>();
        long currentHeight = xdagStats.nmain;
        for (int i = 0; i < count; i++) {
            Block block = getBlockByHeightNew(currentHeight - i);
            if (block != null) {
                res.add(block);
            }
        }
        return res;
    }

    // Save the transaction information packaged in the main block
    public void saveMBlockTx(List<Block> blocks) {
        for (Block block : blocks) {
            long time = System.currentTimeMillis();
            if ((block.getInfo().flags & BI_EXTRA) == 0 && getBlockByHash(block.getHashLow(), true) != null) {
                block = getBlockByHash(block.getHashLow(), true);
            }
            List<Address> links = block.getLinks();
            for (Address link : links) {
                if (link.isAddress) continue;
                Block txBlock = getBlockByHash(link.getAddress(), true);
                if (txBlock != null && mBlockTx.get(link.addressHash) == null) {
                    if ((txBlock.getInfo().flags & BI_MAIN_CHAIN) == 0) {
                        mBlockTx.put(link.addressHash, block.getHashLow());
                        mBlockTimedOut.put(link.addressHash, time);
                        log.debug("Save main block: {} , tx: {} , mBlockTx size :{}", block.getHashLow().toHexString(), link.addressHash, mBlockTx.size());
                        continue;
                    }
                    for (Address txLink : txBlock.getLinks()) {
                        if (txLink.getType().equals(XDAG_FIELD_IN)) {
                            mBlockTx.put(link.addressHash, block.getHashLow());
                            mBlockTimedOut.put(link.addressHash, time);
                            log.debug("Save main txBlock: {} , tx: {} , mBlockTx size :{}", block.getHashLow().toHexString(), link.addressHash, mBlockTx.size());
                            break;
                        }
                    }
                }
            }
        }
    }

    // Regularly delete the data of transactions packaged in the main block.
    private void startCleaner() {
        rollBackLoop.scheduleAtFixedRate(() -> cleanMBlockTimeOut(10 * 60 * 1000L), 10, 5, TimeUnit.SECONDS);
    }

    private void cleanMBlockTimeOut(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Bytes32, Long>> it = mBlockTimedOut.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Bytes32, Long> entry = it.next();
            if (now - entry.getValue() > maxAgeMillis) {
                mBlockTx.remove(entry.getKey());
                it.remove();
                log.debug("Cleaned expired mBlockTX: {} , current mBlockTx size :{}", Hex.toHexString(entry.getKey().toArray()), mBlockTx.size());
            }
        }
    }

    @Override
    public List<Block> listMainBlocks(int count) {
        return listMainBlocksByHeight(count);
    }

    // TODO: List main blocks generated by this pool. If pool only generated blocks early or never generated blocks,
    // need to traverse all block data which needs optimization
    @Override
    public List<Block> listMinedBlocks(int count) {
        Block temp = getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), false);
        if (temp == null) {
            temp = getBlockByHash(Bytes32.wrap(xdagTopStatus.getPreTop()), false);
        }
        List<Block> res = Lists.newArrayList();
        while (count > 0) {
            if (temp == null) {
                break;
            }
            if ((temp.getInfo().flags & BI_MAIN) != 0 && (temp.getInfo().flags & BI_OURS) != 0) {
                count--;
                res.add((Block) temp.clone());
            }
            if (temp.getInfo().getMaxDiffLink() == null) {
                break;
            }
            temp = getBlockByHash(Bytes32.wrap(temp.getInfo().getMaxDiffLink()), false);
        }
        return res;
    }

    public synchronized void decrementNnoref() {
            xdagStats.nnoref--;
    }
    public synchronized void incrementNnoref() {
        xdagStats.nnoref++;
    }

    enum OrphanRemoveActions {
        ORPHAN_REMOVE_NORMAL, ORPHAN_REMOVE_REUSE, ORPHAN_REMOVE_EXTRA
    }
}
