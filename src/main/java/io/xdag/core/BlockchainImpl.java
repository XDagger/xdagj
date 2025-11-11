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
import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.MainnetConfig;
import io.xdag.core.XdagField.FieldType;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Keys;
import io.xdag.crypto.RandomX;
import io.xdag.crypto.Sign;
import io.xdag.db.*;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.db.rocksdb.SnapshotStoreImpl;
import io.xdag.listener.BlockMessage;
import io.xdag.listener.Listener;
import io.xdag.listener.PretopMessage;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.WalletUtils;
import io.xdag.utils.XdagRandomUtils;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

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
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.BytesUtils.*;
import static io.xdag.utils.BytesUtils.equalBytes;
import static io.xdag.utils.WalletUtils.checkAddress;
import static io.xdag.utils.WalletUtils.toBase58;
import static io.xdag.consensus.XdagPow.isChange;

@Slf4j
@Getter
public class BlockchainImpl implements Blockchain {

    // Static gas fee accumulator
    private static XAmount sumGas = XAmount.ZERO;
    
    // Thread factory for main chain checking
    private static final ThreadFactory factory = new BasicThreadFactory.Builder()
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

    // In-memory pools and maps
    private final LinkedHashMap<Bytes, Block> memOrphanPool = new LinkedHashMap<>();
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

    private final List<Address> txList = new CopyOnWriteArrayList<>();
    private List<Block> rollTxList = new LinkedList<>();

    @Getter
    private byte[] preSeed;

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

    // Try to connect a new block to the chain
    @Override
    public synchronized ImportResult tryToConnect(Block block) {

        // TODO: if current height is snapshot height, we need change logic to process new block

        try {
            ImportResult result = ImportResult.IMPORTED_NOT_BEST;

            // Validate block type
            long type = block.getType() & 0xf;
            if (kernel.getConfig() instanceof MainnetConfig) {
                if (type != XDAG_FIELD_HEAD.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a mainnet block");
                    log.debug("Block type error, is not a mainnet block");
                    return result;
                }
            } else {
                if (type != XDAG_FIELD_HEAD_TEST.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a testnet block");
                    log.debug("Block type error, is not a testnet block");
                    return result;
                }
            }

            // Validate block timestamp
            if (block.getTimestamp() > (XdagTime.getCurrentTimestamp() + MAIN_CHAIN_PERIOD / 4)
                    || block.getTimestamp() < kernel.getConfig().getXdagEra()
            ) {
                result = ImportResult.INVALID_BLOCK;
                result.setErrorInfo("Block's time is illegal");
                log.debug("Block's time is illegal");
                return result;
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
                result = ImportResult.INVALID_BLOCK;
                result.setErrorInfo("There is a problem with the transaction fee of this transaction block");
                log.debug("Block's fee is illegal");
                return result;
            }

            // Validate block references
            List<Address> all = block.getLinks().stream().distinct().toList();
            int inputFieldCounter = 0;

            for (Address ref : all) {
                if (ref != null && !ref.isAddress) {
                    if (ref.getType() == XDAG_FIELD_OUT && !ref.getAmount().isZero()) {
                        result = ImportResult.INVALID_BLOCK;
                        result.setHashlow(ref.getAddress());
                        result.setErrorInfo("Address's amount isn't zero");
                        log.debug("Address's amount isn't zero");
                        return result;
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
                            result = ImportResult.INVALID_BLOCK;
                            result.setHashlow(refBlock.getHashLow());
                            result.setErrorInfo("Ref block's time >= block's time");
                            log.debug("Ref block's time >= block's time");
                            return result;
                        }
                        // Ensure TX block's amount is enough to subtract minGas, Amount must >= 0.1
                        if (ref.getType() == XDAG_FIELD_IN && ref.getAmount().subtract(getTxFee(block)).isNegative()) {
                            result = ImportResult.INVALID_BLOCK;
                            result.setHashlow(ref.getAddress());
                            result.setErrorInfo("Ref block's balance < fee");
                            log.debug("Ref block's balance < fee");
                            return result;
                        }
                    }
                } else {
                    // Ensure that there is only one input.
                    if (ref != null && ref.type == XDAG_FIELD_INPUT) {
                        inputFieldCounter = inputFieldCounter + 1;
                        if (inputFieldCounter > 1) {
                            result = ImportResult.INVALID_BLOCK;
                            result.setErrorInfo("The quantity of the input must be exactly one.");
                            log.debug("The quantity of the input must be exactly one.");
                            return result;
                        }
                    }
                    if (ref != null && ref.type == XDAG_FIELD_INPUT && !addressStore.addressIsExist(BytesUtils.byte32ToArray(ref.getAddress()))) {
                        result = ImportResult.INVALID_BLOCK;
                        result.setErrorInfo("Address isn't exist " + WalletUtils.toBase58(BytesUtils.byte32ToArray(ref.getAddress())));
                        log.debug("Address isn't exist {}",
                                WalletUtils.toBase58(BytesUtils.byte32ToArray(ref.getAddress())));
                        return result;
                    }
                    // Ensure TX block's input's & output's amount is enough to subtract minGas, Amount must >= 0.1
                    if (ref != null && (ref.getType() == XDAG_FIELD_INPUT || ref.getType() == XDAG_FIELD_OUTPUT)) {
                        if (getTxFee(block).isPositive() && outPutLimit(block).isPositive()) {
                            if (ref.getType() == XDAG_FIELD_INPUT && ref.getAmount().subtract(getTxFee(block)).isNegative()) {
                                result = ImportResult.INVALID_BLOCK;
                                result.setHashlow(ref.getAddress());
                                result.setErrorInfo("Ref input amount < Gas");
                                return result;
                            } else if (ref.getType() == XDAG_FIELD_OUTPUT && ref.getAmount().subtract(outPutLimit(block)).isNegative()) {
                                result = ImportResult.INVALID_BLOCK;
                                result.setHashlow(ref.getAddress());
                                result.setErrorInfo("Ref output amount < Gas");
                                return result;
                            }
                        } else {
                            result = ImportResult.INVALID_BLOCK;
                            result.setErrorInfo("When constructing a block, the fee entered is illegal");
                            return result;
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
                    result = ImportResult.INVALID_BLOCK;
                    result.setErrorInfo("Account transaction block must have nonce.");
                    return result;
                }
            } else if (isTxBlock(block)) {
                if(block.getTxNonceField() != null) {
                    result = ImportResult.INVALID_BLOCK;
                    result.setErrorInfo("The main block transaction block should not contain nonce.");
                    return result;
                }
            } else {
                if(block.getTxNonceField() != null) {
                    result = ImportResult.INVALID_BLOCK;
                    result.setErrorInfo("The main block or link block should not contain nonce.");
                    return result;
                }
            }
            
            // Validate block inputs
            if (!canUseInput(block)) {
                result = ImportResult.INVALID_BLOCK;
                result.setHashlow(block.getHashLow());
                result.setErrorInfo("Block's input can't be used");
                log.debug("Block's input can't be used");
                return ImportResult.INVALID_BLOCK;
            }
            
            int id = 0;
            // Remove links
            for (Address ref : all) {
                FieldType fType;
                if (!ref.isAddress) {
                    removeOrphan(ref.getAddress(),
                            (block.getInfo().flags & BI_EXTRA) != 0
                                    ? OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                                    : OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);

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

            // Check current main chain
            checkNewMain();

            // Check if block is ours
            if (checkMineAndAdd(block)) {
                log.debug("A block hash:{} become mine", block.getHashLow().toHexString());
                updateBlockFlag(block, BI_OURS, true);
            }

            // Calculate block difficulty
            BigInteger cuDiff = calculateCurrentBlockDiff(block);
            calculateBlockDiff(block, cuDiff);

            // Process extra blocks
            processExtraBlock();

            // Update main chain based on difficulty
            if (block.getInfo().getDifficulty().compareTo(xdagTopStatus.getTopDiff()) > 0) {
                // Fork chain
                long currentHeight = xdagStats.nmain;
                
                // Find common ancestor
                Block blockRef = findAncestor(block, isSyncFixFork(xdagStats.nmain));
                
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
                synchronized (xdagStats) {
                    xdagStats.nnoref++;
                }
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
                            updateBlockFlag(txBlock, BI_REF, false);
                            synchronized (xdagStats) {
                                xdagStats.nnoref++;
                            }
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
                            updateBlockFlag(txBlock, BI_REF, false);
                            synchronized (xdagStats) {
                                xdagStats.nnoref++;
                            }
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
                        address = BytesUtils.byte32ToArray(txRef.getAddress());
                        nonce = block.getTxNonceField().getTransactionNonce();
                        break;
                    }
                }
            }
            getOrphanBlockStore().addOrphan(block, isTxBlock(block), nonce, fee, address);
        }
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
            if ((block.getInfo().flags & BI_APPLIED) != 0 && equalBytes(block.getInfo().getRef(), mHashLow.toArray())) {
                return outPutNum(block) == -1 ? 0 : outPutNum(block);
            }

        }
        return 0;
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
                return false; // 不允许出现 IN
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
            }
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
            Block reuse = memOrphanPool.entrySet().iterator().next().getValue();
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
                && ((p.getInfo().flags & BI_REF) != 0)
                && i > 1
                && ct >= p.getTimestamp() + 2 * 1024) {
//            log.info("setMain success block:{}", Hex.toHexString(p.getHashLow()));
            setMain(p);
        }
    }

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
            while (CollectionUtils.isNotEmpty(rollTxList)) {
                Block linkBlock = createLinkBlock(null, true);
                linkBlock.signOut(kernel.getWallet().getDefKey());
                ImportResult result = this.tryToConnect(new Block(linkBlock.getXdagBlock()));
                if (result == IMPORTED_NOT_BEST || result == IMPORTED_BEST) {
                    onNewBlock(linkBlock);
                    log.debug("create roll linkBlock: {}", linkBlock.getHashLow());
                }
            }
        }
    }

    private boolean blockEqual(Block block1, Block block2) {
        if (block1 == null) {
            return block2 == null;
        } else {
            return block2.equals(block1);
        }
    }

    /**
     * Execute block and return gas fee
     */
    private XAmount applyBlock(boolean flag, Block block) {
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
        for (Address link : links) {
            if (!link.isAddress) {
                Block ref = getBlockByHash(link.getAddress(), false);
                if ((ref.getInfo().flags & BI_MAIN_REF) != 0) continue;
                ref = getBlockByHash(link.getAddress(), true);
                ref.getInfo().setFee(XAmount.ZERO);

                XAmount childGas = applyBlock(false, ref);
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
                XAmount balance = addressStore.getBalanceByAddress(hash2byte(linkAddress));
                UInt64 executedNonce = addressStore.getExecutedNonceNum(BytesUtils.byte32ToArray(linkAddress));
                UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();

                if (blockNonce.compareTo(executedNonce.add(UInt64.ONE)) > 0) {
                    addressStore.updateTxQuantity(BytesUtils.byte32ToArray(linkAddress), executedNonce);
                    return XAmount.ZERO.subtract(XAmount.ONE);
                }
                if (blockNonce.compareTo(executedNonce) <= 0) {
                    return XAmount.ZERO.subtract(XAmount.ONE);
                }
                if (compareAmountTo(balance, link.amount) < 0) {
                    processNonceAfterTransactionExecution(link);
                    return XAmount.ZERO;
                }
                sumIn = sumIn.add(link.getAmount());

            } else if (link.getType() == XDAG_FIELD_IN) {
                Block ref = getBlockByHash(linkAddress, false);
                if (compareAmountTo(ref.getInfo().getAmount(), link.getAmount()) < 0) {
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
            return XAmount.ZERO;
        }

        // Actual amount processing
        XAmount blockGas = XAmount.ZERO;
        for (Address link : links) {
            MutableBytes32 linkAddress = link.addressHash;
            if (!link.isAddress) {
                Block ref = getBlockByHash(linkAddress, false);
                if (link.getType() == XDAG_FIELD_IN) {
                    subtractAndAccept(ref, link.getAmount());
                    XAmount allBalance = addressStore.getAllBalance();
                    allBalance = allBalance.add(link.getAmount().subtract(getTxFee(block)));
                    addressStore.updateAllBalance(allBalance);
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
                    if (link.getType() == XDAG_FIELD_IN) {
                        // Only input references to the main block transaction block will go through this.
                        addAndAccept(ref, link.getAmount());
                        XAmount allBalance = addressStore.getAllBalance();
                        // allBalance = allBalance.subtract(link.getAmount()); //fix subtract twice.
                        try {
                            allBalance = allBalance.subtract(link.getAmount().subtract(block.getFee()));
                        } catch (Exception e) {
                            log.debug("allBalance rollback");
                        }
                        addressStore.updateAllBalance(allBalance);
                    }
                } else {
                    if (link.getType() == XDAG_FIELD_INPUT) {
                        addAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount(), block);
                        byte[] address = byte32ToArray(link.getAddress());
                        UInt64 exeNonce = addressStore.getExecutedNonceNum(address);
                        addressStore.updateExcutedNonceNum(address, false);
                        addressStore.updateTxQuantity(address, exeNonce.subtract(UInt64.ONE));
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
                if (link.isAddress && link.getType() == XDAG_FIELD_INPUT){
                    byte[] address = byte32ToArray(link.getAddress());
                    UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();
                    UInt64 exeNonce = addressStore.getExecutedNonceNum(address);
                    if (blockNonce.compareTo(exeNonce) == 0) {
                        addressStore.updateExcutedNonceNum(address, false);
                        addressStore.updateTxQuantity(address, exeNonce.subtract(UInt64.ONE));
                        log.debug("The transaction processed quantity of account {} is reduced by one, and the number of transactions processed now is nonce = {}",
                                toBase58(BytesUtils.byte32ToArray(link.getAddress())), addressStore.getExecutedNonceNum(address).intValue()
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
            // Set reward
            long mainNumber = xdagStats.nmain + 1;
            log.debug("mainNumber = {},hash = {}", mainNumber, Hex.toHexString(block.getInfo().getHash()));
            XAmount reward = getReward(mainNumber);
            block.getInfo().setHeight(mainNumber);
            updateBlockFlag(block, BI_MAIN, true);

            // Accept reward
            acceptAmount(block, reward);
            xdagStats.nmain++;

            // Recursively execute blocks referenced by main block and get fees
            XAmount mainBlockFee = applyBlock(true, block); //the mainBlock may have tx, return the fee to itself.
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
        }

    }

    /**
     * Cancel Block main block status
     */
    // TODO: Change to new way to cancel main block reward
    public void unSetMain(Block block) {

        synchronized (this) {

            log.debug("UnSet main,{}, mainnumber = {}", block.getHash().toHexString(), xdagStats.nmain);

            XAmount reward = getReward(block.getInfo().getHeight());
            updateBlockFlag(block, BI_MAIN, false);

            xdagStats.nmain--;

            acceptAmount(block, XAmount.ZERO.subtract(reward));
            unApplyBlock(block, true);

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
        byte[] address = BytesUtils.byte32ToArray(link.getAddress());
        addressStore.updateExcutedNonceNum(address, true);
        UInt64 currentTxNonce = addressStore.getTxQuantity(address);
        UInt64 currentExeNonce = addressStore.getExecutedNonceNum(address);
        addressStore.updateTxQuantity(address, currentTxNonce, currentExeNonce);
    }

    @Override
    public Block createNewBlock(
            Map<Address, KeyPair> pairs,
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
        List<KeyPair> keys = new ArrayList<>(Set.copyOf(pairs.values()));
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

        if (isChange.get()) {
            if (CollectionUtils.isNotEmpty(txList)) {
                for (int i = 16 - res, j = 0; i > 0 && j < txList.size(); i--, j++) {
                    refs.add(txList.get(j));
                    res++;
                    log.debug("isChange:{}, txHash:{}", isChange.get(), txList.get(j).getAddress());
                }
            }
        } else {
            log.debug("isChange:{},txList.size <= (16 - res):{}", isChange.get(), (16 - res) >= txList.size());
            txList.clear();
        }

        List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime);
        if (CollectionUtils.isNotEmpty(orphans)) {
            refs.addAll(orphans);
            txList.addAll(orphans);
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
            List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime);
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
    public List<Address> getBlockFromOrphanPool(int num, long[] sendtime) {
        return orphanBlockStore.getOrphan(num, sendtime);
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
        Bytes32 rxHash = Hash.sha256(block.getXdagBlock().getData().slice(0, 512 - 32));
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
                b = getBlockByHash(b.getHashLow(), true);
                List<Address> in = b.getInputs();
                UInt64 nonce = UInt64.ZERO;
                XAmount fee = getTxFee(b);
                byte[] address = null;
                if (isAccountTx(b)) {
                    for(Address ref : in) {
                        if (ref.getType().equals(XDAG_FIELD_INPUT)) {
                            address = BytesUtils.byte32ToArray(ref.getAddress());
                            nonce = b.getTxNonceField().getTransactionNonce();
                            break;
                        }
                    }
                }

                orphanBlockStore.deleteFromQueue(b, isTxBlock(b), nonce, fee, address);
                orphanBlockStore.deleteByKey(b.getHashLow().toArray(), isTxBlock(b), nonce, fee, address);
                synchronized (xdagStats) {
                    xdagStats.nnoref--;
                }
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
        List<SECPPublicKey> keys = block.verifiedKeys();
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

    private boolean verifyBlockSignature(Address in, List<SECPPublicKey> keys) {
        Bytes pubHash = in.getAddress().mutableCopy().slice(8, 20);
        for (SECPPublicKey key : keys) {
            if (Arrays.areEqual(pubHash.toArray(), Keys.toBytesAddress(key))) return true;
        }
        return false;
    }

    private boolean verifySignature(Address in, List<SECPPublicKey> publicKeys) {
        // TODO: Check if block is in snapshot, get blockinfo with isRaw=false
        Block block = getBlockByHash(in.getAddress(), false);
        boolean isSnapshotBlock = block.getInfo().isSnapshot();
        if (isSnapshotBlock) {
            return verifySignatureFromSnapshot(in, publicKeys);
        } else {
            Block inBlock = getBlockByHash(in.getAddress(), true);
            MutableBytes subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);
//            log.debug("verify encoded:{}", Hex.toHexString(subdata));
            SECPSignature sig = inBlock.getOutsig();
            return verifySignature(subdata, sig, publicKeys, block.getInfo());
        }
    }

    // TODO: When input is a block in snapshot, need to verify snapshot's public key or signature data
    private boolean verifySignatureFromSnapshot(Address in, List<SECPPublicKey> publicKeys) {
        BlockInfo blockInfo = blockStore.getBlockInfoByHash(in.getAddress()).getInfo();
        SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
        if (snapshotInfo.getType()) {
            BigInteger xBn = Bytes.wrap(snapshotInfo.getData()).slice(1, 32).toUnsignedBigInteger();
            boolean yBit = snapshotInfo.getData()[0] == 0x03;
            ECPoint point = Sign.decompressKey(xBn, yBit);
            // Parse uncompressed public key without prefix
            byte[] encodePub = point.getEncoded(false);
            SECPPublicKey targetPublicKey = SECPPublicKey.create(new BigInteger(1, java.util.Arrays.copyOfRange(encodePub, 1, encodePub.length)), Sign.CURVE_NAME);
            for (SECPPublicKey publicKey : publicKeys) {
                if (publicKey.equals(targetPublicKey)) {
                    return true;
                }
            }
            return false;
        } else {
            Block block = getBlockByHash(in.getAddress(), false);
            block.setXdagBlock(new XdagBlock(snapshotInfo.getData()));
            block.setParsed(false);
            block.parse();
            MutableBytes subdata = block.getSubRawData(block.getOutsigIndex() - 2);
            SECPSignature sig = block.getOutsig();
            return verifySignature(subdata, Sign.toCanonical(sig), publicKeys, blockInfo);
        }


    }

    private boolean verifySignature(MutableBytes subdata, SECPSignature sig, List<SECPPublicKey> publicKeys, BlockInfo blockInfo) {
        for (SECPPublicKey publicKey : publicKeys) {
            byte[] publicKeyBytes = publicKey.asEcPoint(Sign.CURVE).getEncoded(true);
            Bytes digest = Bytes.wrap(subdata, Bytes.wrap(publicKeyBytes));
//            log.debug("verify encoded:{}", Hex.toHexString(digest));
            Bytes32 hash = Hash.hashTwice(digest);
            if (Sign.SECP256K1.verify(hash, sig, publicKey)) {
                SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
                byte[] pubkeyBytes = publicKey.asEcPoint(Sign.CURVE).getEncoded(true);
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
        List<KeyPair> ourkeys = wallet.getAccounts();
        // Only one output signature
        SECPSignature signature = block.getOutsig();
        // Iterate through all keys
        for (int i = 0; i < ourkeys.size(); i++) {
            KeyPair ecKey = ourkeys.get(i);
            // TODO: Optimize
            byte[] publicKeyBytes = ecKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
            Bytes digest = Bytes.wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
            Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
            // Use hyperledger besu crypto native secp256k1
            if (Sign.SECP256K1.verify(hash, signature, ecKey.getPublicKey())) {
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
        if (kernel.getConfig().getEnableGenerateBlock() &&
                (kernel.getXdagState() == XdagState.SDST || XdagState.STST == kernel.getXdagState() || XdagState.SYNC == kernel.getXdagState())) {
            checkOrphan();
        }
        checkMain();
    }

    public void checkOrphan() {
        long nblk = xdagStats.nnoref / 11;
        if (nblk > 0) {
            boolean b = (nblk % 61) > XdagRandomUtils.nextLong(61);
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

    private void subtractAmount(byte[] addressHash, XAmount amount, Block block) {
        XAmount balance = addressStore.getBalanceByAddress(addressHash);
        try {
            addressStore.updateBalance(addressHash, balance.subtract(amount));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  addressHsh {}  block {}", balance, amount, toBase58(addressHash), block.getHashLow());
        }
        XAmount finalAmount = addressStore.getBalanceByAddress(addressHash);
        log.debug("Balance checker —— Address:{} [old:{} sub:{} fin:{}]",
                WalletUtils.toBase58(addressHash),
                balance.toDecimal(9, XUnit.XDAG).toPlainString(),
                amount.toDecimal(9, XUnit.XDAG).toPlainString(),
                finalAmount.toDecimal(9, XUnit.XDAG).toPlainString());
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(xdagStats.getBalance().subtract(amount));
        }
    }

    private void addAmount(byte[] addressHash, XAmount amount, Block block) {
        XAmount balance = addressStore.getBalanceByAddress(addressHash);
        try {
            addressStore.updateBalance(addressHash, balance.add(amount));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.debug("balance {}  amount {}  addressHsh {}  block {}", balance, amount, toBase58(addressHash), block.getHashLow());
        }
        XAmount finalAmount = addressStore.getBalanceByAddress(addressHash);
        log.warn("Balance checker —— Address:{} [old:{} add:{} fin:{}]",
                WalletUtils.toBase58(addressHash),
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

    enum OrphanRemoveActions {
        ORPHAN_REMOVE_NORMAL, ORPHAN_REMOVE_REUSE, ORPHAN_REMOVE_EXTRA
    }
}
