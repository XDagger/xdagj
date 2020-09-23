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

import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_EXTRA;
import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.BI_MAIN_CHAIN;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.MAIN_APOLLO_AMOUNT;
import static io.xdag.config.Constants.MAIN_APOLLO_HEIGHT;
import static io.xdag.config.Constants.MAIN_APOLLO_TESTNET_HEIGHT;
import static io.xdag.config.Constants.MAIN_BIG_PERIOD_LOG;
import static io.xdag.config.Constants.MAIN_START_AMOUNT;
import static io.xdag.config.Constants.MAX_ALLOWED_EXTRA;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.getDiffByHash;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static io.xdag.utils.FastByteComparisons.equalBytes;
import static io.xdag.utils.MapUtils.getHead;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import lombok.Getter;

import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.Sha256Hash;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.net.message.NetStatus;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.KeyInternalItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlockchainImpl implements Blockchain {
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    // private static long g_apollo_fork_time = 0;
    private Wallet wallet;

    private BlockStore blockStore;
    private AccountStore accountStore;
    /** 非Extra orphan存放 */
    private OrphanPool orphanPool;

    private LinkedHashMap<ByteArrayWrapper, Block> memOrphanPool = new LinkedHashMap<>();

    private Map<ByteArrayWrapper, Integer> memAccount = new ConcurrentHashMap<>();
    private BigInteger topDiff;
    /** 存放的是一个可能是最大难度的block hash */
    private byte[] top_main_chain;
    private byte[] pretop;
    private BigInteger pretopDiff;
    @Getter
    private NetStatus netStatus;
    private Kernel kernel;

    public BlockchainImpl(Kernel kernel, DatabaseFactory dbFactory) {
        this.kernel = kernel;
        this.wallet = kernel.getWallet();
        this.accountStore = kernel.getAccountStore();
        this.blockStore = kernel.getBlockStore();
        this.orphanPool = kernel.getOrphanPool();
        this.pretop = this.top_main_chain = blockStore.getPretop();
        this.pretopDiff = this.topDiff = blockStore.getPretopDiff();
        this.netStatus = kernel.getNetStatus();
        this.netStatus.init(pretopDiff, blockStore.getMainNumber(), blockStore.getBlockNumber());
    }

    /** 尝试去连接这个块 */
    @Override
    public ImportResult tryToConnect(Block block) {
        log.debug("======Connect New Block:" + Hex.toHexString(block.getHashLow()) + "======");
        ReentrantReadWriteLock.WriteLock writeLock = this.stateLock.writeLock();
        writeLock.lock();
        try {

            ImportResult result = ImportResult.IMPORTED_NOT_BEST;
            // 如果区块已经存在不处理
            if (isExist(block.getHashLow())) {
                return ImportResult.EXIST;
            }

            if (isExtraBlock(block)) {
                updateBlockFlag(block, BI_EXTRA, true);
            }
            //TODO make sure links uniq
            List<Address> all = block.getLinks().stream().distinct().collect(Collectors.toList());
            // 检查区块的引用区块是否都存在,对所有input和output放入block（可能在pending或db中取出
            for (Address ref : all) {
                if (ref != null) {
                    if (!isExist(ref.getHashLow())) {
                        log.debug("No Parent " + Hex.toHexString(ref.getHashLow()));
                        result = ImportResult.NO_PARENT;
                        result.setHashLow(ref.getHashLow());
                        return result;
                    } else {
                        if (!ref.getAmount().equals(BigInteger.ZERO)) {
                            updateBlockFlag(block, BI_EXTRA, false);
                        }
                    }
                }
            }

            // 检查区块合法性 检查input是否能使用
            if (!canUseInput(block)) {
                return ImportResult.INVALID_BLOCK;
            }

            // 如果是自己的区块
            if (checkMineAndAdd(block)) {
                log.debug("A block hash:" + Hex.toHexString(block.getHashLow()) + " become mine");
                updateBlockFlag(block, BI_OURS, true);
            }

            // 更新区块难度和maxdifflink
            calculateBlockDiff(block);

            // 检查当前主链
            checkNewMain();

            // 更新pretop
            setPretop(block);
            if (top_main_chain != null) {
                setPretop(getBlockByHash(top_main_chain, false));
            }

            // TODO:extra 处理
            if (memOrphanPool.size() > MAX_ALLOWED_EXTRA) {
                Block reuse = getHead(memOrphanPool).getValue();
                log.debug("remove when extra too big");
                removeOrphan(reuse.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_REUSE);
                netStatus.decBlock();
                if ((reuse.getFlags() & BI_OURS) != 0) {
                    removeAccount(reuse);
                }
            }

            // 根据难度更新主链
            // 判断难度是否是比当前最大，并以此更新topmainchain
            if (block.getDifficulty().compareTo(getTopDiff()) > 0) {
                // 切换主链 fork
                Block blockRef = null;
                Block blockRef0 = null;
                // 把当前区块根据最大难度链接块递归查询到不是主链块为止 将这段的区块更新为主链块
                for (blockRef = block; blockRef != null
                        && ((blockRef.flags & BI_MAIN_CHAIN) == 0); blockRef = getMaxDiffLink(blockRef, false)) {
                    Block tmpRef = getMaxDiffLink(blockRef, false);
                    if (tmpRef != null) {
                    }
                    if ((tmpRef == null || blockRef.getDifficulty().compareTo(calculateBlockDiff(tmpRef)) > 0)
                            && (blockRef0 == null
                            || XdagTime.getEpoch(blockRef0.getTimestamp()) > XdagTime
                            .getEpoch(blockRef.getTimestamp()))) {
                        updateBlockFlag(blockRef, BI_MAIN_CHAIN, true);
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
                // 将主链回退到blockref
                unWindMain(blockRef);
                setTopDiff(block.getDifficulty());
                setTopMainchain(block);
                result = ImportResult.IMPORTED_BEST;
            }

            // remove links
            for (Address ref : all) {
                removeOrphan(ref.getHashLow(),
                        (block.flags & BI_EXTRA) != 0
                                ? OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                                : OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                // TODO:add backref
                // if(!all.get(i).getAmount().equals(BigInteger.ZERO)){
                // Block blockRef = getBlockByHash(all.get(i).getHashLow(),false);
                // }
            }

            // 新增区块
            netStatus.incBlock();
            if (netStatus.getTotalnblocks() < netStatus.getNblocks()) {
                netStatus.setTotalnblocks(netStatus.getNblocks());
            }

            log.debug("======New block waiting to link======");
            if ((block.flags & BI_EXTRA) != 0) {
                log.debug(Hex.toHexString(block.getHashLow()) + " into extra");
                memOrphanPool.put(new ByteArrayWrapper(block.getHashLow()), block);
            } else {
                log.debug(Hex.toHexString(block.getHashLow()) + " into orphan");
                saveBlock(block);
                orphanPool.addOrphan(block);
            }
            log.debug("Current diff:" + getTopDiff().toString(16));
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /** 检查更新主链 * */
    @Override
    public void checkNewMain() {
        log.debug("Check New Main...");
        Block p = null;
        int i = 0;
        if (top_main_chain != null) {
            for (Block block = getBlockByHash(top_main_chain, true); block != null
                    && ((block.flags & BI_MAIN) == 0); block = getMaxDiffLink(block, true)) {

                if ((block.flags & BI_MAIN_CHAIN) != 0) {
                    p = block;
                    ++i;
                }
            }
        }
        if (p != null
                && ((p.flags & BI_REF) != 0)
                && i > 1
                && XdagTime.getCurrentTimestamp() >= p.getTimestamp() + 2 * 1024) {
            setMain(p);
        }
    }

    /** 回退到区块block * */
    public void unWindMain(Block block) {
        if (block == null) {
            return;
        }
        if (top_main_chain != null) {
            for (Block tmp = getBlockByHash(top_main_chain, true); tmp != null
                    && !tmp.equals(block); tmp = getMaxDiffLink(tmp, true)) {
                updateBlockFlag(tmp, BI_MAIN_CHAIN, false);
                // 更新对应的flag信息
                if ((tmp.flags & BI_MAIN) != 0) {
                    unSetMain(tmp);
                }
            }
        }
    }

    /** 执行区块并返回手续费 * */
    private long applyBlock(Block block) {
        long sumIn = 0;
        long sumOut = 0; // sumOut是用来支付其他区块link自己的手续费 现在先用0
        // 处理过
        if ((block.flags & BI_MAIN_REF) != 0) {
            return -1;
        }
        // 设置为已处理
        updateBlockFlag(block, BI_MAIN_REF, true);

        List<Address> links = block.getLinks();
        if (links == null || links.size() == 0) {
            updateBlockFlag(block, BI_APPLIED, true);
            return 0;
        }

        for (int i = 0; i < links.size(); i++) {
            Block ref = getBlockByHash(links.get(i).getHashLow(), true);
            long ret = applyBlock(ref);
            if (ret == -1) {
                continue;
            }
            updateBlockRef(ref, new Address(block));
            if (amount2xdag(block.getAmount() + links.get(i).getAmount().longValue()) >= amount2xdag(
                    block.getAmount())) {
                acceptAmount(block, links.get(i).getAmount().longValue());
            }
        }

        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                Block ref = getBlockByHash(links.get(i).getHashLow(), false);

                if (amount2xdag(ref.getAmount()) < amount2xdag(links.get(i).getAmount().longValue())) {
                    return 0;
                }
                if (amount2xdag(sumIn + links.get(i).getAmount().longValue()) < amount2xdag(sumIn)) {
                    return 0;
                }
                sumIn += links.get(i).getAmount().longValue();
            } else {
                if (amount2xdag(sumOut + links.get(i).getAmount().longValue()) < amount2xdag(sumOut)) {
                    return 0;
                }
                sumOut += links.get(i).getAmount().longValue();
            }
        }

        if (amount2xdag(sumIn + block.getAmount()) < amount2xdag(sumOut)
                || amount2xdag(sumIn + block.getAmount()) < amount2xdag(sumIn)) {
            log.debug("exec fail!");
            return 0;
        }

        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                Block ref = getBlockByHash(links.get(i).getHashLow(), false);
                acceptAmount(ref, -links.get(i).getAmount().longValue());
            } else {
                Block ref = getBlockByHash(links.get(i).getHashLow(), false);
                acceptAmount(ref, links.get(i).getAmount().longValue());
            }
        }

        // 不一定大于0 因为可能部分金额扣除
        long remain = sumIn - sumOut;
        acceptAmount(block, remain);
        updateBlockFlag(block, BI_APPLIED, true);
        // log.debug("====Apply block["+Hex.toHexString(block.getHashLow())+"]
        // done====");

        return 0;
    }

    public long unApplyBlock(Block block) {
        List<Address> links = block.getLinks();
        if ((block.flags & BI_APPLIED) != 0) {
            long sum = 0;
            for (int i = 0; i < links.size(); i++) {
                if (links.get(i).getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                    Block ref = getBlockByHash(links.get(i).getHashLow(), false);
                    acceptAmount(ref, links.get(i).getAmount().longValue());
                    sum -= links.get(i).getAmount().longValue();
                } else {
                    Block ref = getBlockByHash(links.get(i).getHashLow(), false);
                    acceptAmount(ref, -links.get(i).getAmount().longValue());
                    sum += links.get(i).getAmount().longValue();
                }
            }
            acceptAmount(block, sum);
            updateBlockFlag(block, BI_APPLIED, false);
        }
        updateBlockFlag(block, BI_MAIN_REF, false);
        updateBlockRef(block, null);

        for (int i = 0; i < links.size(); i++) {
            Block ref = getBlockByHash(links.get(i).getHashLow(), true);
            if (ref.getRef() != null
                    && equalBytes(ref.getRef().getHashLow(), block.getHashLow())
                    && ((ref.getFlags() & BI_MAIN_REF) != 0)) {
                acceptAmount(block, unApplyBlock(ref));
            }
        }
        return 0;
    }

    /** 设置以block为主块的主链 要么分叉 要么延长 * */
    public void setMain(Block block) {

        blockStore.mainNumberInc();

        netStatus.incMain();
        // 设置奖励
        long time = block.getTimestamp();
        long mainNumber = blockStore.getMainNumber();

        long reward = getReward(time, mainNumber);

        // long reward = getCurrentReward();

        updateBlockFlag(block, BI_MAIN, true);

        // 接收奖励
        acceptAmount(block, reward);

        // 递归执行主块引用的区块 并获取手续费
        acceptAmount(block, applyBlock(block));

        // 主块REF指向自身
        updateBlockRef(block, new Address(block));
        // log.info("set mainblock [{}]", Hex.toHexString(block.getHash()));

    }

    /** 取消Block主块身份 * */
    public void unSetMain(Block block) {
        blockStore.mainNumberDec();
        netStatus.decMain();

        // long amount = getCurrentReward();
        long amount = getReward(block.getTimestamp(), blockStore.getMainNumber());
        updateBlockFlag(block, BI_MAIN, false);

        // 去掉奖励和引用块的手续费
        acceptAmount(block, -amount);
        acceptAmount(block, unApplyBlock(block));
    }

    @Override
    public Block createNewBlock(Map<Address, ECKey> pairs, List<Address> to, boolean mining) {
        if (pairs == null && to == null) {
            if (mining) {
                return createMainBlock();
            }
        }
        int defkeyIndex = -1;
        // 遍历所有key 判断是否有defkey
        List<ECKey> keys = new ArrayList<ECKey>(pairs.values());
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(wallet.getDefKey().ecKey)) {
                defkeyIndex = i;
            }
        }

        List<Address> all = new ArrayList<>();
        all.addAll(pairs.keySet());
        all.addAll(to);
        int res = 1 + pairs.size() + to.size() + 3 * pairs.size() + defkeyIndex == -1 ? 2 : 0;
        long sendtime = XdagTime.getCurrentTimestamp();
        if (mining) {
            res += 1;
            sendtime = XdagTime.getMainTime();
        }

        Address pretop = null;
        if (getPreTopMainBlockForLink(sendtime) != null) {

            pretop = new Address(getPreTopMainBlockForLink(sendtime), XdagField.FieldType.XDAG_FIELD_OUT);
            res++;
        }
        List<Address> refs = getBlockFromOrphanPool(16 - res);
        return new Block(sendtime, pretop, all, refs, mining, keys, defkeyIndex);
    }

    public Block createMainBlock() {
        int res = 4;
        long sendtime = XdagTime.getMainTime();
        Address pretop = null;
        if (getPreTopMainBlockForLink(sendtime) != null) {
            pretop = new Address(getPreTopMainBlockForLink(sendtime), XdagField.FieldType.XDAG_FIELD_OUT);
            res++;
        }
        List<Address> refs = getBlockFromOrphanPool(16 - res);
        return new Block(sendtime, pretop, null, refs, true, null, -1);
    }

    /*
     * @Author punk
     *
     * @Description 从orphan中获取一定数量的orpahn块用来link
     *
     * @Date 2020/4/21
     *
     * @Param [num]
     *
     * @return java.util.List<io.xdag.core.Address>
     **/
    public List<Address> getBlockFromOrphanPool(int num) {
        return orphanPool.getOrphan(num);
    }

    public byte[] getPreTopMainBlockForLink(long sendTime) {
        long maintime = XdagTime.getEpoch(sendTime);
        Block topInfo = null;
        if (top_main_chain == null) {
            return null;
        }

        topInfo = getBlockByHash(top_main_chain, false);
        if (XdagTime.getEpoch(topInfo.getTimestamp()) == maintime) {
            return pretop;
        } else {

            return top_main_chain;
        }
    }

    public void setPretop(Block block) {
        if (block == null) {
            return;
        }
        if (XdagTime.getEpoch(block.getTimestamp()) > XdagTime.getCurrentEpoch()) {
            return;
        }
        BigInteger blockDiff = calculateBlockDiff(block);
        if (pretop == null) {
            pretop = block.getHashLow().clone();
            pretopDiff = blockDiff;
            block.setPretopCandidate(true);
            block.setPretopCandidateDiff(pretopDiff);
            return;
        }

        if (blockDiff.compareTo(pretopDiff) > 0) {
            pretop = block.getHashLow().clone();
            pretopDiff = blockDiff;
            block.setPretopCandidate(true);
            block.setPretopCandidateDiff(pretopDiff);
        }
    }

    /** 计算区块在链上的难度 同时设置难度 和最大难度连接 并返回区块难度 * */
    public BigInteger calculateBlockDiff(Block block) {

        if (block.getDifficulty() != null) {
            log.debug("block 的难度不为空，hash[{}]", Hex.toHexString(block.getHash()));
            return block.getDifficulty();
        }

        // 初始区块自身难度设置
        BigInteger diff0 = getDiffByHash(block.getHash());
        block.setDifficulty(diff0);

        BigInteger maxDiff = diff0;
        Address maxdifflink = null;

        // 临时区块
        Block tmpBlock;
        if (block.getLinks().size() == 0) {
            return diff0;
        }

        // 遍历所有link 找maxlink
        List<Address> links = block.getLinks();
        for (Address ref : links) {
            Block refBlock = getBlockByHash(ref.getHashLow(), false);
            if(refBlock == null) {
                break;
            }
            // 如果引用的那个快的epoch 小于当前这个块的回合
            if ( XdagTime.getEpoch(refBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp())) {
                // 如果难度大于当前最大难度
                BigInteger curDiff = refBlock.getDifficulty().add(diff0);
                if (curDiff.compareTo(maxDiff) > 0) {
                    maxDiff = curDiff;
                    maxdifflink = ref;
                }
            } else {
                // 计算出来的diff
                // 1. 不在同一epoch的maxdiff+diff0
                // 2. 同一epoch的maxdiff
                tmpBlock = refBlock; // tmpBlock是link中的
                BigInteger curDiff = refBlock.getDifficulty();
                while ((tmpBlock != null)
                        && XdagTime.getEpoch(tmpBlock.getTimestamp()) == XdagTime.getEpoch(block.getTimestamp())) {
                    tmpBlock = getMaxDiffLink(tmpBlock, false);
                }
                if (tmpBlock != null
                        && (XdagTime.getEpoch(tmpBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp()))) {
                    curDiff = tmpBlock.getDifficulty().add(diff0);
                }
                if (curDiff.compareTo(maxDiff) > 0) {
                    maxDiff = curDiff;
                    maxdifflink = ref;
                }
            }
        }
        block.setDifficulty(maxDiff);
        block.setMaxDifflink(maxdifflink);
        return maxDiff;
    }

    @Override
    public Block getBlockByHash(byte[] hashlow, boolean isRaw) {
        if (hashlow == null) {
            return null;
        }
        ByteArrayWrapper key = new ByteArrayWrapper(hashlow);
        Block b = memOrphanPool.get(key);
        if (b == null) {
            b = blockStore.getBlockByHash(hashlow, isRaw);
        }
        return b;
    }

    public Block getMaxDiffLink(Block block, boolean isRaw) {
        if (block.getMaxDifflink() != null) {
            return getBlockByHash(block.getMaxDifflink().getHashLow(), isRaw);
        }
        return null;
    }

    @Override
    public BigInteger getTopDiff() {
        return topDiff;
    }

    public void setTopDiff(BigInteger diff) {
        topDiff = diff;
    }

    @Override
    public boolean hasBlock(byte[] hash) {
        return blockStore.hasBlock(hash);
    }

    public void removeOrphan(byte[] hashlow, OrphanRemoveActions action) {
        Block b = getBlockByHash(hashlow, false);
        if (b != null && ((b.getFlags() & BI_REF) == 0) && (action != OrphanRemoveActions.ORPHAN_REMOVE_EXTRA || (b.getFlags() & BI_EXTRA) != 0)) {
            // 如果removeBlock是BI_EXTRA
            if ((b.getFlags() & BI_EXTRA) != 0) {
                log.debug("移除Extra");
                // 那removeBlockInfo就是完整的
                // 从MemOrphanPool中去除
                ByteArrayWrapper key = new ByteArrayWrapper(b.getHashLow());
                Block removeBlockRaw = memOrphanPool.get(key);
                memOrphanPool.remove(key);
                if (action != OrphanRemoveActions.ORPHAN_REMOVE_REUSE) {
                    // 将区块保存
                    saveBlock(removeBlockRaw);
                    memOrphanPool.remove(key);
                    // 移除所有EXTRA块链接的块
                    List<Address> all = removeBlockRaw.getLinks();
                    for(Address addr : all) {
                        removeOrphan(addr.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                    }
                }
                // 更新removeBlockRaw的flag
                // nextra减1

                updateBlockFlag(removeBlockRaw, BI_EXTRA, false);
            } else {
                orphanPool.deleteByHash(b.getHashLow());
            }
            // 更新这个块的flag
            updateBlockFlag(b, BI_REF, true);
        }
    }

    public void updateBlockFlag(Block block, byte flag, boolean direction) {
        if (direction) {
            block.flags |= flag;
        } else {
            block.flags &= ~flag;
        }
        if (block.isSaved) {
            blockStore.updateBlockInfo(BlockStore.BLOCK_FLAG, block);
        }
    }

    public void updateBlockRef(Block block, Address ref) {
        block.setRef(ref);
        if (block.isSaved) {
            blockStore.updateBlockInfo(BlockStore.BLOCK_REF, block);
        }
    }

    public void saveBlock(Block block) {
        block.isSaved = true;
        log.debug("save a block block hash [{}]", Hex.toHexString(block.getHashLow()));
        blockStore.saveBlock(block);
        // 如果是自己的账户
        if (memAccount.containsKey(new ByteArrayWrapper(block.getHash()))) {
            log.debug("new account");
            addNewAccount(block, memAccount.get(new ByteArrayWrapper(block.getHash())));
            memAccount.remove(new ByteArrayWrapper(block.getHash()));
        }

        if  (block.isPretopCandidate()) {
            blockStore.setPretop(block);
            blockStore.setPretopDiff(block.getPretopCandidateDiff());
        }
    }

    public boolean isExtraBlock(Block block) {
        if ((block.getTimestamp() & 0xffff) != 0xffff || block.getNonce() == null || block.isSaved()) {
            return false;
        }
        return true;
    }

    @Override
    public long getOrphanSize() {
        return orphanPool.getOrphanSize();
    }

    @Override
    public long getExtraSize() {
        return memOrphanPool.size();
    }

    @Override
    public List<Block> getBlockByTime(long starttime, long endtime) {
        return blockStore.getBlocksUsedTime(starttime, endtime);
    }

    @Override
    public long getMainBlockSize() {
        return blockStore.getMainNumber();
    }

    @Override
    public long getBlockSize() {
        return blockStore.getBlockNumber();
    }

    public boolean canUseInput(Block block) {
        boolean canUse = false;
        List<ECKey> ecKeys = block.verifiedKeys();
        List<Address> inputs = block.getInputs();
        if (inputs == null || inputs.size() == 0) {
            return true;
        }
        for (Address in : inputs) {
            Block inBlock = getBlockByHash(in.getHashLow(), true);
            byte[] subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);
            ECKey.ECDSASignature sig = inBlock.getOutsig();

            for (ECKey ecKey : ecKeys) {
                byte[] hash = Sha256Hash.hashTwice(BytesUtils.merge(subdata, ecKey.getPubKeybyCompress()));
                if (ecKey.verify(hash, sig)) {
                    canUse = true;
                }
            }

            if (!canUse) {
                //TODO this maybe some old issue( input and output was same )
                List<ECKey> keys = block.getPubKeys();
                for (ECKey ecKey : keys) {
                    byte[] hash = Sha256Hash.hashTwice(BytesUtils.merge(subdata, ecKey.getPubKeybyCompress()));
                    if (ecKey.verify(hash, sig)) {
                        return true;
                    }
                }
                return false;
            }
        }

        return true;
    }

    public boolean checkMineAndAdd(Block block) {
        List<KeyInternalItem> ourkeys = wallet.getKey_internal();
        // 输出签名只有一个
        ECKey.ECDSASignature signature = block.getOutsig();
        // 遍历所有key
        for (int i = 0; i < ourkeys.size(); i++) {
            ECKey ecKey = ourkeys.get(i).ecKey;
            byte[] digest = BytesUtils.merge(
                    block.getSubRawData(block.getOutsigIndex() - 2), ecKey.getPubKeybyCompress());
            byte[] hash = Sha256Hash.hashTwice(digest);
            if (ecKey.verify(hash, signature)) {
                log.debug("Validate Success");
                addNewAccount(block, i);
                return true;
            }
        }
        return false;
    }

    public void addNewAccount(Block block, int keyIndex) {
        if (!block.isSaved()) {
            log.debug("Add into Mem,size:" + memAccount.size());
            memAccount.put(new ByteArrayWrapper(block.getHash()), keyIndex);
        } else {
            log.debug("Add into storage");
            accountStore.addNewAccount(block, keyIndex);
        }
    }

    public void removeAccount(Block block) {
        if (!block.isSaved) {
            memAccount.remove(new ByteArrayWrapper(block.getHash()));
        } else {
            accountStore.removeAccount(block);
        }
    }

    public void setTopMainchain(Block block) {
        this.top_main_chain = block.getHashLow();
    }

    /** 根据当前区块数量计算奖励金额 cheato * */
    public long getCurrentReward() {
        return xdag2amount(1024);
    }

    public long getReward(long time, long num) {
        long start = getStartAmount(time, num);
        long amount = start >> (num >> MAIN_BIG_PERIOD_LOG);
        return amount;
    }

    @Override
    public long getSupply(long nmain)
    {
        long res = 0;
        long amount = getStartAmount(0, nmain);
        long current_nmain = nmain;
        while ((current_nmain >> MAIN_BIG_PERIOD_LOG) > 0) {
            res += (1l << MAIN_BIG_PERIOD_LOG) * amount;
            current_nmain -= 1l << MAIN_BIG_PERIOD_LOG;
            amount >>= 1;
        }
        res += current_nmain * amount;
        long fork_height = Config.MAINNET?MAIN_APOLLO_TESTNET_HEIGHT:MAIN_APOLLO_HEIGHT;
        if(nmain >= fork_height) {
            // add before apollo amount
            res += (fork_height - 1) * (MAIN_START_AMOUNT - MAIN_APOLLO_AMOUNT);
        }
        return res;
    }

    public long getStartAmount(long time, long num) {
        long forkHeight = Config.MAINNET ? MAIN_APOLLO_HEIGHT : MAIN_APOLLO_TESTNET_HEIGHT;
        long startAmount = 0;
        if (num >= forkHeight) {
            startAmount = MAIN_APOLLO_AMOUNT;
        } else {
            startAmount = MAIN_START_AMOUNT;
        }

        return startAmount;
    }

    /** 为区块block添加amount金额 * */
    private void acceptAmount(Block block, long amount) {
        block.setAmount(block.getAmount() + amount);
        blockStore.updateBlockInfo(BlockStore.BLOCK_AMOUNT, block);
        if ((block.flags & BI_OURS) != 0) {
            log.debug("====Our balance add new amount:" + amount + "====,获取到amount的hash【{}】",
                    Hex.toHexString(block.getHashLow()));
            accountStore.updateGBanlance(amount);
        }
    }

    /** 判断是否已经接收过区块 * */
    public boolean isExist(byte[] hashlow) {
        if (memOrphanPool.containsKey(new ByteArrayWrapper(hashlow)) ||
                blockStore.hasBlock(hashlow)) {
            return true;
        }
        return false;
    }

    public AccountStore getAccountStore() {
        return accountStore;
    }

    @Override
    public List<Block> listMainBlocks(int count) {
        Block temp = getBlockByHash(top_main_chain, false);
        List<Block> res = new ArrayList<>();
        while (count > 0) {
            if (temp == null) {
                break;
            }
            if ((temp.getFlags() & BI_MAIN) != 0) {
                count--;
                res.add((Block) temp.clone());
                if (temp.getMaxDifflink() == null) {
                    break;
                }
                temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
            } else {
                if (temp.getMaxDifflink() == null) {
                    break;
                }
                temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
            }
        }
        return res;
    }

    @Override
    public List<Block> listMinedBlocks(int count) {
        Block temp = getBlockByHash(top_main_chain, false);
        List<Block> res = new ArrayList<>();
        while (count > 0) {
            if (temp == null) {
                break;
            }
            if ((temp.getFlags() & BI_MAIN) != 0 && (temp.getFlags() & BI_OURS) != 0) {
                count--;
                res.add((Block) temp.clone());
                if (temp.getMaxDifflink() == null) {
                    break;
                }
                temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
            } else {
                if (temp.getMaxDifflink() == null) {
                    break;
                }
                temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
            }
        }
        return res;
    }

    @Override
    public List<byte[]> getAllAccount() {
        List<byte[]> res = new ArrayList<>();
        List<byte[]> storeAccount = accountStore.getAllAccount();
        res.addAll(storeAccount);
        return res;
    }

    @Override
    public Map<ByteArrayWrapper, Integer> getMemAccount() {
        return memAccount;
    }

    @Override
    public ReentrantReadWriteLock getStateLock() {
        return stateLock;
    }

    enum OrphanRemoveActions {
        ORPHAN_REMOVE_NORMAL, ORPHAN_REMOVE_REUSE, ORPHAN_REMOVE_EXTRA
    }
}
