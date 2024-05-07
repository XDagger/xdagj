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
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

import java.math.BigInteger;
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
import static io.xdag.utils.BytesUtils.equalBytes;
import static io.xdag.utils.BytesUtils.long2UnsignedLong;
import static io.xdag.utils.WalletUtils.checkAddress;
import static io.xdag.utils.WalletUtils.toBase58;

@Slf4j
@Getter
public class BlockchainImpl implements Blockchain {

    private static XAmount sumGas = XAmount.ZERO;
    private static final ThreadFactory factory = new BasicThreadFactory.Builder()
            .namingPattern("check-main-%d")
            .daemon(true)
            .build();

    private final Wallet wallet;

    private final AddressStore addressStore;
    private final BlockStore blockStore;
    private final TransactionHistoryStore txHistoryStore;
    /**
     * 非Extra orphan存放
     */
    private final OrphanBlockStore orphanBlockStore;

    private final LinkedHashMap<Bytes, Block> memOrphanPool = new LinkedHashMap<>();
    private final Map<Bytes, Integer> memOurBlocks = new ConcurrentHashMap<>();
    private final XdagStats xdagStats;
    private final Kernel kernel;


    private final XdagTopStatus xdagTopStatus;

    private final ScheduledExecutorService checkLoop;
    private final RandomX randomx;
    private final List<Listener> listeners = Lists.newArrayList();
    private ScheduledFuture<?> checkLoopFuture;
    private final long snapshotHeight;
    private SnapshotStore snapshotStore;
    private SnapshotStore snapshotAddressStore;
    private final XdagExtStats xdagExtStats;
    //    public Filter filter;
    @Getter
    private byte[] preSeed;

    public BlockchainImpl(Kernel kernel) {
        this.kernel = kernel;
        this.wallet = kernel.getWallet();
        this.xdagExtStats = new XdagExtStats();
        // 1. init chain state from rocksdb
        this.addressStore = kernel.getAddressStore();
        this.blockStore = kernel.getBlockStore();
        this.orphanBlockStore = kernel.getOrphanBlockStore();
        this.txHistoryStore = kernel.getTxHistoryStore();
        snapshotHeight = kernel.getConfig().getSnapshotSpec().getSnapshotHeight();
//        this.filter = new Filter(blockStore);

        // 2. if enable snapshot, init snapshot from rocksdb
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()
                && kernel.getConfig().getSnapshotSpec().getSnapshotHeight() > 0
                // 没有快照启动过
                && !blockStore.isSnapshotBoot()) {
            this.xdagStats = new XdagStats();
            this.xdagTopStatus = new XdagTopStatus();

            if (kernel.getConfig().getSnapshotSpec().isSnapshotJ()) {
                initSnapshotJ();
            }

            // 保存最新快照的状态
            blockStore.saveXdagTopStatus(xdagTopStatus);
            blockStore.saveXdagStatus(xdagStats);
        } else {
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

        // add randomx utils
        randomx = kernel.getRandomx();
        if (randomx != null) {
            randomx.setBlockchain(this);
        }

        checkLoop = new ScheduledThreadPoolExecutor(1, factory);
        // 检查主块链
        this.startCheckMain(1024);
    }

    public void initSnapshotJ() {
        long start = System.currentTimeMillis();
        System.out.println("init snapshot...");

        RocksdbKVSource snapshotAddressSource = new RocksdbKVSource("SNAPSHOT/ADDRESS");
        snapshotAddressStore = new SnapshotStoreImpl(snapshotAddressSource);
        snapshotAddressSource.setConfig(kernel.getConfig());
        snapshotAddressSource.init();
        snapshotAddressStore.saveAddress(this.blockStore, this.addressStore, this.txHistoryStore, kernel.getWallet().getAccounts(), kernel.getConfig().getSnapshotSpec().getSnapshotTime());

        RocksdbKVSource snapshotSource = new RocksdbKVSource("SNAPSHOT/BLOCKS");
        snapshotStore = new SnapshotStoreImpl(snapshotSource);
        snapshotSource.setConfig(kernel.getConfig());
        snapshotStore.init();
        snapshotStore.saveSnapshotToIndex(this.blockStore, this.txHistoryStore, kernel.getWallet().getAccounts(), kernel.getConfig().getSnapshotSpec().getSnapshotTime());
        Block lastBlock = blockStore.getBlockByHeight(snapshotHeight);

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

        xdagTopStatus.setPreTop(lastBlock.getHashLow().toArray());
        xdagTopStatus.setTop(lastBlock.getHashLow().toArray());
        xdagTopStatus.setTopDiff(lastBlock.getInfo().getDifficulty());
        xdagTopStatus.setPreTopDiff(lastBlock.getInfo().getDifficulty());

        XAmount allBalance = snapshotStore.getAllBalance().add(snapshotAddressStore.getAllBalance()); //block all balance + address all balance

        long end = System.currentTimeMillis();
        System.out.println("init snapshotJ done");
        System.out.println("time：" + (end - start) + "ms");
        System.out.println("Our balance: " + snapshotStore.getOurBalance().toDecimal(9, XUnit.XDAG).toPlainString());
        System.out.printf("All amount: %s%n", allBalance.toDecimal(9, XUnit.XDAG).toPlainString());
    }


    @Override
    public void registerListener(Listener listener) {
        this.listeners.add(listener);
    }

    /**
     * 尝试去连接这个块
     */
    @Override
    public synchronized ImportResult tryToConnect(Block block) {

        // TODO: if current height is snapshot height, we need change logic to process new block

        try {
            ImportResult result = ImportResult.IMPORTED_NOT_BEST;

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

            if (block.getTimestamp() > (XdagTime.getCurrentTimestamp() + MAIN_CHAIN_PERIOD / 4)
                    || block.getTimestamp() < kernel.getConfig().getXdagEra()
//                    || (limit && timestamp - tmpNodeBlock.time > limit)
            ) {
                result = ImportResult.INVALID_BLOCK;
                result.setErrorInfo("Block's time is illegal");
                log.debug("Block's time is illegal");
                return result;
            }

            if (isExist(block.getHashLow())) {
                return ImportResult.EXIST;
            }

            if (isExistInMem(block.getHashLow())) {
                return ImportResult.IN_MEM;
            }

            if (isExtraBlock(block)) {
                updateBlockFlag(block, BI_EXTRA, true);
            }

            List<Address> all = block.getLinks().stream().distinct().toList();
            // TODO：新地址转账判断inputs的地址余额是否足够
            // 检查区块的引用区块是否都存在,对所有input和output放入block（可能在pending或db中取出）
            for (Address ref : all) {
                /*
                 Now transactionBlock's outputs are new address so ref.isAddress == false which means no blocks
                 mainBlocks and linkBlocks are same as original
                 */
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
//                        log.debug("No Parent " + Hex.toHexString(ref.getHashLow()));
                        result = ImportResult.NO_PARENT;
                        result.setHashlow(ref.getAddress());
                        result.setErrorInfo("Block have no parent for " + result.getHashlow().toHexString());
                        log.debug("Block have no parent for " + result.getHashlow().toHexString());
                        return result;
                    } else {
                        // ensure ref block's time is earlier than block's time
                        if (refBlock.getTimestamp() >= block.getTimestamp()) {
                            result = ImportResult.INVALID_BLOCK;
                            result.setHashlow(refBlock.getHashLow());
                            result.setErrorInfo("Ref block's time >= block's time");
                            log.debug("Ref block's time >= block's time");
                            return result;
                        }
                        // ensure TX block's amount is enough to subtract minGas, Amount must >= 0.1;
                        if (ref.getType() == XDAG_FIELD_IN && ref.getAmount().subtract(MIN_GAS).isNegative()) {
                            result = ImportResult.INVALID_BLOCK;
                            result.setHashlow(ref.getAddress());
                            result.setErrorInfo("Ref block's balance < minGas");
                            log.debug("Ref block's balance < minGas");
                            return result;
                        }
                    }
                } else {
                    if (ref != null && ref.type == XDAG_FIELD_INPUT && !addressStore.addressIsExist(BytesUtils.byte32ToArray(ref.getAddress()))) {
                        result = ImportResult.INVALID_BLOCK;
                        result.setErrorInfo("Address isn't exist " + WalletUtils.toBase58(BytesUtils.byte32ToArray(ref.getAddress())));
                        log.debug("Address isn't exist " + WalletUtils.toBase58(BytesUtils.byte32ToArray(ref.getAddress())));
                        return result;
                    }
                    // ensure TX block's input's & output's amount is enough to subtract minGas, Amount must >= 0.1;
                    if (ref != null && (ref.getType() == XDAG_FIELD_INPUT || ref.getType() == XDAG_FIELD_OUTPUT) && ref.getAmount().subtract(MIN_GAS).isNegative()) {
                        result = ImportResult.INVALID_BLOCK;
                        result.setHashlow(ref.getAddress());
                        result.setErrorInfo("Ref block's balance < minGas");
                        log.debug("Ref block's balance < minGas");
                        return result;
                    }
                }
                /*
                 Determine if ref is a block
                 */
                // TODO: 如果是交易块 不设置extra
                if (ref != null && compareAmountTo(ref.getAmount(), XAmount.ZERO) != 0) {
                    log.debug("Try to connect a tx Block:{}", block.getHash().toHexString());
                    updateBlockFlag(block, BI_EXTRA, false);
                }
            }
            // 检查区块合法性 检查input是否能使用
            if (!canUseInput(block)) {
                result = ImportResult.INVALID_BLOCK;
                result.setHashlow(block.getHashLow());
                result.setErrorInfo("Block's input can't be used");
                log.debug("Block's input can't be used");
                return ImportResult.INVALID_BLOCK;
            }
            int id = 0;
            // remove links
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
                    onNewTxHistory(ref.getAddress(), block.getHashLow(), fType, ref.getAmount(),
                            block.getTimestamp(), block.getInfo().getRemark(), ref.isAddress, id);
                }
                id++;
            }

            // 检查当前主链
            checkNewMain();

            // 如果是自己的区块
            if (checkMineAndAdd(block)) {
                log.debug("A block hash:" + block.getHashLow().toHexString() + " become mine");
                updateBlockFlag(block, BI_OURS, true);
            }

            // calculate block's self difficulty
            BigInteger cuDiff = calculateCurrentBlockDiff(block);
            // calculate block's chain difficulty
            calculateBlockDiff(block, cuDiff);

            // TODO:extra 处理
            processExtraBlock();

            // 根据难度更新主链
            // 判断难度是否是比当前最大，并以此更新topMainChain
            if (block.getInfo().getDifficulty().compareTo(xdagTopStatus.getTopDiff()) > 0) {
                // 切换主链 fork
                long currentHeight = xdagStats.nmain;
                // 找到共同祖先blockref
                Block blockRef = findAncestor(block, isSyncFixFork(xdagStats.nmain));
                // 将主链回退到blockRef
                unWindMain(blockRef);
                // 更新新的链
                updateNewChain(block, isSyncFixFork(xdagStats.nmain));
                // 发生回退
                if (currentHeight - xdagStats.nmain > 1) {
                    log.info("XDAG:Before unwind, height = {}, After unwind, height = {}, unwind number = {}",
                            currentHeight, xdagStats.nmain, currentHeight - xdagStats.nmain);
                }

                Block currentTop = getBlockByHash(xdagTopStatus.getTop() == null ? null :
                        Bytes32.wrap(xdagTopStatus.getTop()), false);
                BigInteger currentTopDiff = xdagTopStatus.getTopDiff();
                log.debug("update top: {}", block.getHashLow());
                // update Top
                xdagTopStatus.setTopDiff(block.getInfo().getDifficulty());
                xdagTopStatus.setTop(block.getHashLow().toArray());
                // update preTop
                setPreTop(currentTop, currentTopDiff);
                // if block's epoch is earlier than current epoch, then notify the PoW thread to regenerate the main block
                if (XdagTime.getEpoch(block.getTimestamp()) < XdagTime.getCurrentEpoch()) {
                    onNewPretop();
                }
                result = ImportResult.IMPORTED_BEST;
                xdagStats.updateMaxDiff(xdagTopStatus.getTopDiff());
                xdagStats.updateDiff(xdagTopStatus.getTopDiff());
            }

            // 新增区块
            xdagStats.nblocks++;
            xdagStats.totalnblocks = Math.max(xdagStats.nblocks, xdagStats.totalnblocks);

            if ((block.getInfo().flags & BI_EXTRA) != 0) {
                memOrphanPool.put(block.getHashLow(), block);
                xdagStats.nextra++;
//                 TODO：设置为返回 IMPORTED_EXTRA
//                result = ImportResult.IMPORTED_EXTRA;
            } else {
                saveBlock(block);
                orphanBlockStore.addOrphan(block);
                xdagStats.nnoref++;
            }
            blockStore.saveXdagStatus(xdagStats);

            // 如果区块输入不为0说明是交易块
            if (!block.getInputs().isEmpty()) {
                if ((block.getInfo().getFlags() & BI_OURS) != 0) {
                    log.info("XDAG:pool transaction(reward). block hash:{}", block.getHash().toHexString());
                }
            }

            // 把过去四个小时每个时间片的diff都记录下来，后面会用这些diff去转换出一个全局hashrate
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

    /**
     * 用于判断是否切换到修复同步问题的分支
     */
    // TODO: 目前syncFixHeight 写死 后续需要修改
    // TODO: paulochen 同步问题改进，切换高度未定
    public boolean isSyncFixFork(long currentHeight) {
        long syncFixHeight = SYNC_FIX_HEIGHT;
        return currentHeight >= syncFixHeight;
    }

    public Block findAncestor(Block block, boolean isFork) {
        // 切换主链 fork
        Block blockRef;
        Block blockRef0 = null;
        // 把当前区块根据最大难度链接块递归查询到不是主链块为止 将这段的区块更新为主链块
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
        // 分叉点
        if (blockRef != null
                && blockRef0 != null
                && !blockRef.equals(blockRef0)
                && XdagTime.getEpoch(blockRef.getTimestamp()) == XdagTime.getEpoch(blockRef0.getTimestamp())) {
            blockRef = getMaxDiffLink(blockRef, false);
        }
        return blockRef;
    }

    public void updateNewChain(Block block, boolean isFork) {
        if (!isFork) {
            return;
        }
        Block blockRef;
        Block blockRef0 = null;
        // 把当前区块根据最大难度链接块递归查询到不是主链块为止 将这段的区块更新为主链块
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
        }
    }

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

    protected void onNewPretop() {
        for (Listener listener : listeners) {
            listener.onMessage(new PretopMessage(Bytes.wrap(xdagTopStatus.getTop()), PRE_TOP));
        }
    }

    protected void onNewBlock(Block block) {
        for (Listener listener : listeners) {
            listener.onMessage(new BlockMessage(Bytes.wrap(block.getXdagBlock().getData()), NEW_LINK));
        }
    }

    /**
     * 检查更新主链 *
     */
    @Override
    public synchronized void checkNewMain() {
        Block p = null;
        int i = 0;
        // TODO: 如果是快照点主块会直接返回，因为快照点前的数据都已经确定好
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
     * 回退到区块block *
     */
    public void unWindMain(Block block) {
        log.debug("Unwind main to block,{}", block == null ? "null" : block.getHashLow().toHexString());
        if (xdagTopStatus.getTop() != null) {
            log.debug("now pretop : {}", xdagTopStatus.getPreTop() == null ? "null" : Bytes32.wrap(xdagTopStatus.getPreTop()).toHexString());
            for (Block tmp = getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), true); tmp != null
                    && !blockEqual(block, tmp); tmp = getMaxDiffLink(tmp, true)) {
                updateBlockFlag(tmp, BI_MAIN_CHAIN, false);
                // 更新对应的flag信息
                if ((tmp.getInfo().flags & BI_MAIN) != 0) {
                    unSetMain(tmp);
                    // Fix: paulochen 这里需要更新你区块在数据库中的信息 比如height 210729
                    blockStore.saveBlockInfo(tmp.getInfo());
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
     * 执行区块并返回手续费 *
     */
    private XAmount applyBlock(boolean flag, Block block) {
        XAmount gas = XAmount.ZERO;
        XAmount sumIn = XAmount.ZERO;
        XAmount sumOut = XAmount.ZERO; // sumOut是用来支付其他区块link自己的手续费 现在先用0
        // 处理过的block
        if ((block.getInfo().flags & BI_MAIN_REF) != 0) {
            return XAmount.ZERO.subtract(XAmount.ONE);
        }
        // the TX block create by wallet or pool will not set fee = minGas, set in this.
        if (!block.getInputs().isEmpty() && block.getFee().equals(XAmount.ZERO)) {
            block.getInfo().setFee(MIN_GAS);
        }
        // 设置为已处理
        MutableBytes32 blockHashLow = block.getHashLow();

        updateBlockFlag(block, BI_MAIN_REF, true);

        List<Address> links = block.getLinks();
        if (links == null || links.isEmpty()) {
            updateBlockFlag(block, BI_APPLIED, true);
            return XAmount.ZERO;
        }

        for (Address link : links) {
            if (!link.isAddress) {
                // 预处理时不需要拿回全部数据
                Block ref = getBlockByHash(link.getAddress(), false);
                XAmount ret;
                // 如果处理过
                if ((ref.getInfo().flags & BI_MAIN_REF) != 0) {
                    ret = XAmount.ZERO.subtract(XAmount.ONE);
                } else {
                    ref = getBlockByHash(link.getAddress(), true);
                    ret = applyBlock(false, ref);
                }
                if (ret.equals(XAmount.ZERO.subtract(XAmount.ONE))) {
                    continue;
                }
                sumGas = sumGas.add(ret);
                updateBlockRef(ref, new Address(block));
                if (flag && sumGas != XAmount.ZERO) {// judge if block is mainBlock, if true: add fee!
                    block.getInfo().setFee(block.getFee().add(sumGas));
                    addAndAccept(block, sumGas);
                    sumGas = XAmount.ZERO;
                }
            }
        }


        for (Address link : links) {
            MutableBytes32 linkAddress = link.getAddress();
            if (link.getType() == XDAG_FIELD_IN) {
                /*
                 * Compatible with two transfer modes.
                 * When the input is a block, the original processing method is used.
                 * When the input is an address, the balance is taken from the database for judgment.
                 */
                if (!link.isAddress) {
                    Block ref = getBlockByHash(linkAddress, false);
                    if (compareAmountTo(ref.getInfo().getAmount(), link.getAmount()) < 0) {
//                if (ref.getInfo().getAmount() < link.getAmount().longValue()) {
                        log.debug("This input ref doesn't have enough amount,hash:{},amount:{},need:{}",
                                Hex.toHexString(ref.getInfo().getHashlow()), ref.getInfo().getAmount(),
                                link.getAmount());
                        return XAmount.ZERO;
                    }
                } else {
                    log.debug("Type error");
                    return XAmount.ZERO;
                }

                // Verify in advance that Address amount is not negative
                if (compareAmountTo(sumIn.add(link.getAmount()), sumIn) < 0) {
                    log.debug("This input ref's amount less than 0");
                    return XAmount.ZERO;
                }
                sumIn = sumIn.add(link.getAmount());
            } else if (link.getType() == XDAG_FIELD_INPUT) {
                XAmount balance = addressStore.getBalanceByAddress(hash2byte(link.getAddress()));
                if (compareAmountTo(balance, link.amount) < 0) {
                    log.debug("This input ref doesn't have enough amount,hash:{},amount:{},need:{}",
                            Hex.toHexString(hash2byte(link.getAddress())), balance,
                            link.getAmount());
                    return XAmount.ZERO;
                }
                // Verify in advance that Address amount is not negative
                if (compareAmountTo(sumIn.add(link.getAmount()), sumIn) < 0) {
                    log.debug("This input ref's:{} amount less than 0", linkAddress.toHexString());
                    return XAmount.ZERO;
                }
                sumIn = sumIn.add(link.getAmount());
            } else {
                ////Verify in advance that Address amount is not negative
                if (compareAmountTo(sumOut.add(link.getAmount()), sumOut) < 0) {
                    log.debug("This output ref's:{} amount less than 0", linkAddress.toHexString());
                    return XAmount.ZERO;
                }
                sumOut = sumOut.add(link.getAmount());
            }
        }
        if (compareAmountTo(block.getInfo().getAmount().add(sumIn), sumOut) < 0 ||
                compareAmountTo(block.getInfo().getAmount().add(sumIn), sumIn) < 0
        ) {
            log.debug("block:{} exec fail!", blockHashLow.toHexString());
            return XAmount.ZERO;
        }

        for (Address link : links) {
            MutableBytes32 linkAddress = link.addressHash;
            if (!link.isAddress) {
                Block ref = getBlockByHash(linkAddress, false);
                if (link.getType() == XDAG_FIELD_IN) {
                    subtractAndAccept(ref, link.getAmount());
                    XAmount allBalance = addressStore.getAllBalance();
                    allBalance = allBalance.add(link.getAmount().subtract(block.getFee()));
                    addressStore.updateAllBalance(allBalance);
                } else if (!flag) {// 递归返回到第一层时，ref上一个主块（output）类型，此时不允许扣款
                    addAndAccept(ref, link.getAmount().subtract(block.getFee()));
                    gas = gas.add(block.getFee()); // Mark the output for Fee
                }
//            blockStore.saveBlockInfo(ref.getInfo()); // TODO：acceptAmount时已经保存了 这里还需要保存吗
            } else {
                if (link.getType() == XDAG_FIELD_INPUT) {
                    subtractAmount(BasicUtils.hash2byte(linkAddress), link.getAmount(), block);
                } else if (link.getType() == XDAG_FIELD_OUTPUT) {
                    addAmount(BasicUtils.hash2byte(linkAddress), link.getAmount().subtract(block.getFee()), block);
                    gas = gas.add(block.getFee()); // Mark the output for Fee
                }
            }
        }

        // 不一定大于0 因为可能部分金额扣除
        // TODO:need determine what is data;
        updateBlockFlag(block, BI_APPLIED, true);
        return gas;
    }

    // TODO: unapply block which in snapshot
    public XAmount unApplyBlock(Block block) {
        List<Address> links = block.getLinks();
        Collections.reverse(links); // must be reverse
        if ((block.getInfo().flags & BI_APPLIED) != 0) {
            // the TX block create by wallet or pool will not set fee = minGas, set in this.
            if (!block.getInputs().isEmpty() && block.getFee().equals(XAmount.ZERO)) {
                block.getInfo().setFee(MIN_GAS);
            }
            XAmount sum = XAmount.ZERO;
            for (Address link : links) {
                if (!link.isAddress) {
                    Block ref = getBlockByHash(link.getAddress(), false);
                    if (link.getType() == XDAG_FIELD_IN) {
                        addAndAccept(ref, link.getAmount());
                        sum = sum.subtract(link.getAmount());
                        XAmount allBalance = addressStore.getAllBalance();
                        // allBalance = allBalance.subtract(link.getAmount()); //fix subtract twice.
                        try {
                            allBalance = allBalance.subtract(link.getAmount().subtract(block.getFee()));
                        } catch (Exception e) {
                            log.debug("allBalance rollback");
                        }
                        addressStore.updateAllBalance(allBalance);
                    } else if (link.getType() == XDAG_FIELD_OUT) {
                        // when add amount in 'Apply' subtract fee, so unApply also subtract fee.
                        subtractAndAccept(ref, link.getAmount().subtract(block.getFee()));
                        sum = sum.add(link.getAmount());
                    }
                } else {
                    if (link.getType() == XDAG_FIELD_INPUT) {
                        addAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount(), block);
                        sum = sum.subtract(link.getAmount());
                    } else {
                        // when add amount in 'Apply' subtract fee, so unApply also subtract fee.
                        subtractAmount(BasicUtils.hash2byte(link.getAddress()), link.getAmount().subtract(block.getFee()), block);
                        sum = sum.add(link.getAmount());
                    }
                }

            }
            updateBlockFlag(block, BI_APPLIED, false);
        }
        updateBlockFlag(block, BI_MAIN_REF, false);
        updateBlockRef(block, null);

        for (Address link : links) {
            if (!link.isAddress) {
                Block ref = getBlockByHash(link.getAddress(), false);
                //even mainBlock duplicate link the TX_block which other mainBlock is handled, we could check the TX ref if this mainBlock.
                if (ref.getInfo().getRef() != null
                        && equalBytes(ref.getInfo().getRef(), block.getHashLow().toArray())
                        && ((ref.getInfo().flags & BI_MAIN_REF) != 0)) {
                    addAndAccept(block, unApplyBlock(getBlockByHash(ref.getHashLow(), true)));
                }
            }
        }
        return XAmount.ZERO;
    }

    /**
     * 设置以block为主块的主链 要么分叉 要么延长 *
     */
    public void setMain(Block block) {

        synchronized (this) {
            // 设置奖励
            long mainNumber = xdagStats.nmain + 1;
            log.debug("mainNumber = {},hash = {}", mainNumber, Hex.toHexString(block.getInfo().getHash()));
            XAmount reward = getReward(mainNumber);
            block.getInfo().setHeight(mainNumber);
            updateBlockFlag(block, BI_MAIN, true);

            // 接收奖励
            acceptAmount(block, reward);
            xdagStats.nmain++;

            // 递归执行主块引用的区块 并获取手续费
            XAmount mainBlockFee = applyBlock(true, block); //the mainBlock may have tx, return the fee to itself.
            if (!mainBlockFee.equals(XAmount.ZERO)) {// normal mainBlock will not go into this
                acceptAmount(block, mainBlockFee); //add the fee
                block.getInfo().setFee(mainBlockFee);
            }
            // 主块REF指向自身
            // TODO:补充手续费
            updateBlockRef(block, new Address(block));

            if (randomx != null) {
                randomx.randomXSetForkTime(block);
            }
        }

    }

    /**
     * 取消Block主块身份 *
     */
    // TODO:改为新的撤销主块奖励
    public void unSetMain(Block block) {

        synchronized (this) {

            log.debug("UnSet main,{}, mainnumber = {}", block.getHash().toHexString(), xdagStats.nmain);

            XAmount amount = block.getInfo().getAmount();// mainBlock's balance will have fee, subtract all balance.
            block.getInfo().setFee(XAmount.ZERO);// set the mainBlock's zero.
            updateBlockFlag(block, BI_MAIN, false);

            xdagStats.nmain--;

            // 去掉奖励和引用块的手续费
            acceptAmount(block, XAmount.ZERO.subtract(amount));
            acceptAmount(block, unApplyBlock(block));

            if (randomx != null) {
                randomx.randomXUnsetForkTime(block);
            }
            block.getInfo().setHeight(0);
        }
    }

    @Override
    public Block createNewBlock(Map<Address, KeyPair> pairs, List<Address> to, boolean mining, String remark, XAmount fee) {

        int hasRemark = remark == null ? 0 : 1;

        if (pairs == null && to == null) {
            if (mining) {
                return createMainBlock();
            } else {
                return createLinkBlock(remark);
            }
        }
        int defKeyIndex = -1;

        // 遍历所有key 判断是否有defKey
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

        // TODO: 判断pair是否有重复
        int res = 1 + pairs.size() + to.size() + 3 * keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;

        // TODO : 如果区块字段不足
        if (res > 16) {
            return null;
        }
        long[] sendTime = new long[2];
        sendTime[0] = XdagTime.getCurrentTimestamp();
        List<Address> refs = Lists.newArrayList();

        return new Block(kernel.getConfig(), sendTime[0], all, refs, mining, keys, remark, defKeyIndex, fee);
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
        List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime);
        if (CollectionUtils.isNotEmpty(orphans)) {
            refs.addAll(orphans);
        }
        return new Block(kernel.getConfig(), sendTime[0], null, refs, true, null,
                kernel.getConfig().getNodeSpec().getNodeTag(), -1, XAmount.ZERO);
    }

    public Block createLinkBlock(String remark) {
        // <header + remark + outsig + nonce>
        int hasRemark = remark == null ? 0 : 1;
        int res = 1 + hasRemark + 2;
        long[] sendTime = new long[2];
        sendTime[0] = XdagTime.getCurrentTimestamp();

        List<Address> refs = Lists.newArrayList();
        List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime);
        if (CollectionUtils.isNotEmpty(orphans)) {
            refs.addAll(orphans);
        }
        return new Block(kernel.getConfig(), sendTime[1], null, refs, false, null,
                remark, -1, XAmount.ZERO);
    }

    /**
     * 从orphan中获取一定数量的orphan块用来link
     **/
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
     * update pretop
     *
     * @param target     target
     * @param targetDiff difficulty of block
     */
    public void setPreTop(Block target, BigInteger targetDiff) {
        if (target == null) {
            return;
        }

        // make sure the target's epoch is earlier than current top's epoch
        Block block = getBlockByHash(xdagTopStatus.getTop() == null ? null :
                Bytes32.wrap(xdagTopStatus.getTop()), false);
        if (block != null) {
            if (XdagTime.getEpoch(target.getTimestamp()) >= XdagTime.getEpoch(block.getTimestamp())) {
                return;
            }
        }

        // if pretop is null, then update pretop to target
        if (xdagTopStatus.getPreTop() == null) {
            xdagTopStatus.setPreTop(target.getHashLow().toArray());
            xdagTopStatus.setPreTopDiff(targetDiff);
            target.setPretopCandidate(true);
            target.setPretopCandidateDiff(targetDiff);
            return;
        }

        // if targetDiff greater than pretop diff, then update pretop to target
        if (targetDiff.compareTo(xdagTopStatus.getPreTopDiff()) > 0) {
            log.debug("update pretop:{}", Bytes32.wrap(target.getHashLow()).toHexString());
            xdagTopStatus.setPreTop(target.getHashLow().toArray());
            xdagTopStatus.setPreTopDiff(targetDiff);
            target.setPretopCandidate(true);
            target.setPretopCandidateDiff(targetDiff);
        }
    }

    /**
     * 计算当前区块难度
     */
    public BigInteger calculateCurrentBlockDiff(Block block) {
        if (block == null) {
            return BigInteger.ZERO;
        }
        if (block.getInfo().getDifficulty() != null) {
            return block.getInfo().getDifficulty();
        }
        BigInteger blockDiff;
        // 初始区块自身难度设置
        if (randomx != null && randomx.isRandomxFork(XdagTime.getEpoch(block.getTimestamp()))
                && XdagTime.isEndOfEpoch(block.getTimestamp())) {
            blockDiff = getDiffByRandomXHash(block);
        } else {
            blockDiff = getDiffByRawHash(block.getHash());
        }

        return blockDiff;
    }

    /**
     * 设置区块难度 和 最大难度连接 并返回区块难度 *
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

        // 临时区块
        Block tmpBlock;
        if (block.getLinks().isEmpty()) {
            return cuDiff;
        }

        // 遍历所有link 找maxLink
        List<Address> links = block.getLinks();
        for (Address ref : links) {
            /*
             * only Blocks has difficult;
             */
            if (!ref.isAddress) {
                Block refBlock = getBlockByHash(ref.getAddress(), false);
                if (refBlock == null) {
                    break;
                }
                // 如果引用的那个快的epoch 小于当前这个块的回合
                if (XdagTime.getEpoch(refBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp())) {
                    // 如果难度大于当前最大难度
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
                    // 计算出来的diff
                    // 1. 不在同一epoch的maxDiff+diff0
                    // 2. 同一epoch的maxDiff
                    tmpBlock = refBlock; // tmpBlock是link中的
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
        byte[] blockHash = randomx.randomXBlockHash(data.toArray(), data.size(), epoch);
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

    // ADD: 新版本-通过高度获取区块
    public Block getBlockByHeightNew(long height) {
        // TODO: if snapshto enabled, need height > snapshotHeight - 128
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled() && (height < snapshotHeight - 128)
                && !kernel.getConfig().getSnapshotSpec().isSnapshotJ()) {
            return null;
        }
        // 补充高度低于0时不返回
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
        // ensure that hashlow is hashlow
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
            // 如果removeBlock是BI_EXTRA
            if ((b.getInfo().flags & BI_EXTRA) != 0) {
//                log.debug("移除Extra");
                // 那removeBlockInfo就是完整的
                // 从MemOrphanPool中去除
                Bytes key = b.getHashLow();
                Block removeBlockRaw = memOrphanPool.get(key);
                memOrphanPool.remove(key);
                if (action != OrphanRemoveActions.ORPHAN_REMOVE_REUSE) {
                    // 将区块保存
                    saveBlock(removeBlockRaw);
                    // 移除所有EXTRA块链接的块
                    if (removeBlockRaw != null) {
                        List<Address> all = removeBlockRaw.getLinks();
                        for (Address addr : all) {
                            removeOrphan(addr.getAddress(), OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                        }
                    }
                }
                // 更新removeBlockRaw的flag
                // nextra减1
                updateBlockFlag(removeBlockRaw, BI_EXTRA, false);
                xdagStats.nextra--;
            } else {
                orphanBlockStore.deleteByHash(b.getHashLow().toArray());
                xdagStats.nnoref--;
            }
            // 更新这个块的flag
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
        // 如果是自己的账户
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

    public boolean isMainBlock(Block block) {
        return ((block.getTimestamp() & 0xffff) == 0xffff && block.getNonce() != null);
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
         * while "in" isn't address , need to verifySignature.
         */
        // TODO：
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
        // TODO: 判断in是不是snapshot中的块, 使用isRaw为false的方式获取blockinfo
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

    // TODO: 当输入是snapshot中的区块时，需要验证snapshot的公钥或签名数据
    private boolean verifySignatureFromSnapshot(Address in, List<SECPPublicKey> publicKeys) {
        BlockInfo blockInfo = blockStore.getBlockInfoByHash(in.getAddress()).getInfo();
        SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
        if (snapshotInfo.getType()) {
            BigInteger xBn = Bytes.wrap(snapshotInfo.getData()).slice(1, 32).toUnsignedBigInteger();
            boolean yBit = snapshotInfo.getData()[0] == 0x03;
            ECPoint point = Sign.decompressKey(xBn, yBit);
            // 解析成非压缩去前缀 公钥
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
        // 输出签名只有一个
        SECPSignature signature = block.getOutsig();
        // 遍历所有key
        for (int i = 0; i < ourkeys.size(); i++) {
            KeyPair ecKey = ourkeys.get(i);
            // TODO: 优化
            byte[] publicKeyBytes = ecKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
            Bytes digest = Bytes.wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
            Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
            // use hyperledger besu crypto native secp256k1
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
            // add before apollo amount
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
        if (kernel.getXdagState() == XdagState.SDST || XdagState.STST == kernel.getXdagState() || XdagState.SYNC == kernel.getXdagState()) {
            checkOrphan();
        }
        checkMain();
    }

    public void checkOrphan() {
        long nblk = xdagStats.nnoref / 11;
        if (nblk > 0) {
            boolean b = (nblk % 61) > (RandomUtils.nextLong() % 61);
            nblk = nblk / 61 + (b ? 1 : 0);
        }
        while (nblk-- > 0) {
            Block linkBlock = createNewBlock(null, null, false, kernel.getConfig().getNodeSpec().getNodeTag(), XAmount.ZERO);
            linkBlock.signOut(kernel.getWallet().getDefKey());
            ImportResult result = this.tryToConnect(linkBlock);
            if (result == IMPORTED_NOT_BEST || result == IMPORTED_BEST) {
                onNewBlock(linkBlock);
            }
        }
    }

    public void checkMain() {
        try {
            checkNewMain();
            // checkNewMain后xdagStats状态会发生改变
            blockStore.saveXdagStatus(xdagStats);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    public SECPPublicKey getBlockPubKey(Block block) {
        List<SECPPublicKey> keys = block.verifiedKeys();
        MutableBytes subData = block.getSubRawData(block.getOutsigIndex() - 2);
//            log.debug("verify encoded:{}", Hex.toHexString(subdata));
        SECPSignature sig = block.getOutsig();
        for (SECPPublicKey publicKey : keys) {
            byte[] publicKeyBytes = publicKey.asEcPoint(Sign.CURVE).getEncoded(true);
            Bytes digest = Bytes.wrap(subData, Bytes.wrap(publicKeyBytes));
//            log.debug("verify encoded:{}", Hex.toHexString(digest));
            Bytes32 hash = Hash.hashTwice(digest);
            if (Sign.SECP256K1.verify(hash, sig, publicKey)) {
                return publicKey;
            }
        }
        return null;
    }

    @Override
    public void stopCheckMain() {
        try {

            if (checkLoopFuture != null) {
                checkLoopFuture.cancel(true);
            }
            // 关闭线程池
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
     * 为区块block添加amount金额 *
     */
    // TODO : accept amount to block which in snapshot
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

    // TODO : accept amount to block which in snapshot
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
     * 判断是否已经接收过区块 *
     */
    public boolean isExist(Bytes32 hashlow) {
        return blockStore.hasBlock(hashlow) || isExitInSnapshot(hashlow);
    }

    public boolean isExistInMem(Bytes32 hashlow) {
        return memOrphanPool.containsKey(hashlow);
    }

    /**
     * 判断是否存在于snapshot
     **/
    public boolean isExitInSnapshot(Bytes32 hashlow) {
        if (kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()) {
            // 从公钥快照与签名快照中查询该块
            return blockStore.hasBlockInfo(hashlow);
        } else {
            return false;
        }
    }


    // ADD: 使用新版本方法获取主块
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

    @Override
    public List<Block> listMainBlocks(int count) {
        return listMainBlocksByHeight(count);
    }

    // TODO: 列出本矿池生成的主块，如果本矿池只在前期产块或者从未产块，会导致需要遍历所有的区块数据，这部分应该需要优化
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
