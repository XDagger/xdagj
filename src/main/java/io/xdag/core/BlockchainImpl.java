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
import io.xdag.config.Config;
import io.xdag.crypto.ECDSASignature;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Sign;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.listener.Listener;
import io.xdag.listener.Message;
import io.xdag.randomx.RandomX;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.KeyInternalItem;
import io.xdag.wallet.OldWallet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.xdag.config.Constants.*;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.getDiffByHash;
import static io.xdag.utils.FastByteComparisons.equalBytes;
import static io.xdag.utils.MapUtils.getHead;

@Slf4j
@Getter
public class BlockchainImpl implements Blockchain {
    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "check main-" + cnt.getAndIncrement());
        }
    };

    private final OldWallet wallet;
    private final BlockStore blockStore;
    /** 非Extra orphan存放 */
    private final OrphanPool orphanPool;

    private final LinkedHashMap<ByteArrayWrapper, Block> memOrphanPool = new LinkedHashMap<>();
    private final Map<ByteArrayWrapper, Integer> memOurBlocks = new ConcurrentHashMap<>();
    private final XdagStats xdagStats;
    private final Kernel kernel;


    private final XdagTopStatus xdagTopStatus;

    private final ScheduledExecutorService checkLoop;
    private ScheduledFuture<?> checkLoopFuture;

    private final RandomX randomXUtils;

    private final List<Listener> listeners = new ArrayList<>();

    public BlockchainImpl(Kernel kernel) {
        this.kernel = kernel;
        this.wallet = kernel.getWallet();
        this.blockStore = kernel.getBlockStore();
        this.orphanPool = kernel.getOrphanPool();
        XdagStats storedStats = blockStore.getXdagStatus();
        if(storedStats != null) {
            storedStats.setNwaitsync(0);
            this.xdagStats = storedStats;
            this.xdagStats.nextra = 0;
        } else {
            this.xdagStats = new XdagStats();
        }

        XdagTopStatus storedTopStatus = blockStore.getXdagTopStatus();
        this.xdagTopStatus = Objects.requireNonNullElseGet(storedTopStatus, XdagTopStatus::new);

        // add randomx utils
        randomXUtils = kernel.getRandomXUtils();
        if (randomXUtils != null) {
            randomXUtils.setBlockchain(this);
        }

        checkLoop = new ScheduledThreadPoolExecutor(1, factory);
        // 检查主块链
        this.startCheckMain();
    }


    @Override
    public void registerListener(Listener listener) {
        this.listeners.add(listener);
    }

    //读取C版本区块
    public long loadBlockchain(
            String srcFilePath) {
        //todo：文件夹的时间
        long starttime = 1583368095744L;
        long endtime = 1583389441664L;
        ByteBuffer buffer = ByteBuffer.allocate(512);
        StringBuilder file = new StringBuilder(srcFilePath);
        FileInputStream inputStream = null;
        FileChannel channel = null;
        starttime |= 0x00000;
        endtime |= 0xffff;
        File fileImpl;
        long res = 0;
        System.out.println("开始读取区块");
        while (starttime < endtime) {
            List<String> filename = getFileName(starttime);
            String blockfile = Hex.toHexString(BytesUtils.byteToBytes((byte) ((starttime >> 16) & 0xff), true));
            file.append(filename.get(filename.size() - 1)).append(blockfile).append(".dat");
            fileImpl = new File(file.toString());
            if (!fileImpl.exists()) {
                starttime += 0x10000;
                file = new StringBuilder(srcFilePath);
                continue;
            }
            System.out.println("Block from:" + file);
            try {

                inputStream = new FileInputStream(fileImpl);
                channel = inputStream.getChannel();
                while (true) {
                    int eof = channel.read(buffer);
                    if (eof == -1) {
                        break;
                    }
                    buffer.flip();
                    res++;
                    Block block = new Block(new XdagBlock(buffer.array().clone()));
                    this.tryToConnect(block);
                    buffer.clear();
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            starttime += 0x10000;
            file = new StringBuilder(srcFilePath);
        }
        System.out.println("读取结束");
        return res;
    }

    public List<String> getFileName(long time) {
        List<String> file = new ArrayList<>();
        file.add("");
        StringBuilder stringBuffer = new StringBuilder(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        return file;
    }

    /** 尝试去连接这个块 */
    @Override
    public synchronized ImportResult tryToConnect(Block block) {
        try {
            ImportResult result = ImportResult.IMPORTED_NOT_BEST;

            long type = block.getType()&0xf;
            if (Config.MAINNET) {
                if (type != XDAG_FIELD_HEAD.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a mainnet block");
                    return result;
                }
            } else {
                if (type != XDAG_FIELD_HEAD_TEST.asByte()) {
                    result = ImportResult.ERROR;
                    result.setErrorInfo("Block type error, is not a testnet block");
                    return result;
                }
            }

            if (block.getTimestamp() > (XdagTime.getCurrentTimestamp()+ MAIN_CHAIN_PERIOD/4)
                    || block.getTimestamp() < XDAG_ERA
//                    || (limit && timestamp - tmpNodeBlock.time > limit)
            ){
                result = ImportResult.INVALID_BLOCK;
                result.setErrorInfo("Block's time is illegal");
                return result;
            }

            if (isExist(block.getHashLow())) {
                return ImportResult.EXIST;
            }

            if (isExtraBlock(block)) {
                updateBlockFlag(block, BI_EXTRA, true);
            }

            List<Address> all = block.getLinks().stream().distinct().collect(Collectors.toList());
            // 检查区块的引用区块是否都存在,对所有input和output放入block（可能在pending或db中取出
            for (Address ref : all) {
                if (ref != null) {
                    Block refBlock = getBlockByHash(ref.getHashLow(), false);
                    if (refBlock == null) {
//                        log.debug("No Parent " + Hex.toHexString(ref.getHashLow()));
                        result = ImportResult.NO_PARENT;
                        result.setHashlow(ref.getHashLow());
                        result.setErrorInfo("Block have no parent for "+Hex.toHexString(result.getHashlow()));
                        return result;
                    } else {
                        // 链接块的时间需要小于该块时间，否则为不合法区块
                        if(refBlock.getTimestamp() >= block.getTimestamp()) {
                            result = ImportResult.INVALID_BLOCK;
                            result.setHashlow(refBlock.getHashLow());
                            result.setErrorInfo("Ref block's time >= block's time");
                            return result;
                        }

//                        if (!ref.getAmount().equals(BigInteger.ZERO)) {
//                            updateBlockFlag(block, BI_EXTRA, false);
//                        }
                    }

                }
            }

            // remove links
            for (Address ref : all) {
                removeOrphan(ref.getHashLow(),
                        (block.getInfo().flags & BI_EXTRA) != 0
                                ? OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                                : OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                // TODO:add backref
                // if(!all.get(i).getAmount().equals(BigInteger.ZERO)){
                // Block blockRef = getBlockByHash(all.get(i).getHashLow(),false);
                // }
            }

            // 检查当前主链
            checkNewMain();

            // 检查区块合法性 检查input是否能使用
            if (!canUseInput(block)) {
                result = ImportResult.INVALID_BLOCK;
                result.setHashlow(block.getHashLow());
                result.setErrorInfo("Block's input can't be used");
                return ImportResult.INVALID_BLOCK;
            }

            // 如果是自己的区块
            if (checkMineAndAdd(block)) {
                log.info("A block hash:" + Hex.toHexString(block.getHashLow()) + " become mine");
                updateBlockFlag(block, BI_OURS, true);
            }

            // 更新区块难度和maxDiffLink
            calculateBlockDiff(block);

            // 更新preTop
            setPreTop(block);
            setPreTop(getBlockByHash(xdagTopStatus.getTop(), false));

            // 通知XdagPoW 新pretop产生
            onNewPretop();

            // TODO:extra 处理
            processExtraBlock();
//            if (memOrphanPool.size() > MAX_ALLOWED_EXTRA) {
//                Block reuse = getHead(memOrphanPool).getValue();
//                log.debug("remove when extra too big");
//                removeOrphan(reuse.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_REUSE);
//                xdagStats.nblocks--;
//                xdagStats.totalnblocks = Math.max(xdagStats.nblocks,xdagStats.totalnblocks);
//
//                if ((reuse.getInfo().flags & BI_OURS) != 0) {
//                    removeOurBlock(reuse);
//                }
//            }

            // 根据难度更新主链
            // 判断难度是否是比当前最大，并以此更新topMainChain
            if (block.getInfo().getDifficulty().compareTo(xdagTopStatus.getTopDiff()) > 0) {
                // 切换主链 fork
                Block blockRef;
                Block blockRef0 = null;
                // 把当前区块根据最大难度链接块递归查询到不是主链块为止 将这段的区块更新为主链块
                for (blockRef = block;
                     blockRef != null && ((blockRef.getInfo().flags & BI_MAIN_CHAIN) == 0);
                     blockRef = getMaxDiffLink(blockRef, false)) {
                    Block tmpRef = getMaxDiffLink(blockRef, false);
                    if (
                            (tmpRef == null || blockRef.getInfo().getDifficulty().compareTo(calculateBlockDiff(tmpRef)) > 0) &&
                                    (blockRef0 == null || XdagTime.getEpoch(blockRef0.getTimestamp()) > XdagTime.getEpoch(blockRef.getTimestamp()))
                    ) {
//                        log.debug("update BI_MAIN_CHAIN block:{}", Hex.toHexString(blockRef.getHashLow()));
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
                // 将主链回退到blockRef
                long currentHeight = xdagStats.nmain;
                unWindMain(blockRef);
                // 发生回退
                if (xdagStats.nmain - currentHeight > 0) {
                    log.debug("Punk:Before unwind, height = {}, After unwind, height = {}, unwind number = {}", currentHeight, xdagStats.nmain, currentHeight - xdagStats.nmain);
                }
                xdagTopStatus.setTopDiff(block.getInfo().getDifficulty());
                xdagTopStatus.setTop(block.getHashLow());
                result = ImportResult.IMPORTED_BEST;
            }

            // 新增区块
            xdagStats.nblocks++;
            xdagStats.totalnblocks = Math.max(xdagStats.nblocks,xdagStats.totalnblocks);
            if (xdagStats.getTotalnblocks() < xdagStats.getNblocks()) {
                xdagStats.setTotalnblocks(xdagStats.getNblocks());
            }

            //orphan (hash , block)
//            log.debug("======New block waiting to link======,{}",Hex.toHexString(block.getHashLow()));
            if ((block.getInfo().flags & BI_EXTRA) != 0) {
//                log.debug("block:{} is extra, put it into memOrphanPool waiting to link.", Hex.toHexString(block.getHashLow()));
                memOrphanPool.put(new ByteArrayWrapper(block.getHashLow()), block);
                xdagStats.nextra++;
            } else {
//                log.debug("block:{} is extra, put it into orphanPool waiting to link.", Hex.toHexString(block.getHashLow()));
                saveBlock(block);
                orphanPool.addOrphan(block);
                xdagStats.nnoref++;
            }
            blockStore.saveXdagStatus(xdagStats);

            // 如果区块输入不为0说明是交易块
            if (block.getInputs().size() != 0) {
                if ((block.getInfo().getFlags()&BI_OURS) != 0) {
                    log.debug("Punk:pool transaction(reward). block hash:{}", Hex.toHexString(block.getHash()));
                }
            }

            return result;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            return ImportResult.ERROR;
        }
    }

    public void processExtraBlock() {
        if (memOrphanPool.size() > MAX_ALLOWED_EXTRA) {
            Block reuse = getHead(memOrphanPool).getValue();
            log.info("Remove when extra too big");
            removeOrphan(reuse.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_REUSE);
            xdagStats.nblocks--;
            xdagStats.totalnblocks = Math.max(xdagStats.nblocks,xdagStats.totalnblocks);

            if ((reuse.getInfo().flags & BI_OURS) != 0) {
                removeOurBlock(reuse);
            }
        }
    }

    private void onNewPretop() {
        for (Listener listener : listeners) {
            listener.onMessage(new Message(xdagTopStatus.getPreTop()));
        }
    }

    /** 检查更新主链 * */
    @Override
    public synchronized void checkNewMain() {
        Block p = null;
        int i = 0;
        if (xdagTopStatus.getTop() != null) {
            for (Block block = getBlockByHash(xdagTopStatus.getTop(), true); block != null
                    && ((block.getInfo().flags & BI_MAIN) == 0); block = getMaxDiffLink(block, true)) {

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
//        else {
//            log.debug("setMain fail block:{}", p != null?Hex.toHexString(p.getHashLow()):"null");
//            if(p != null) {
//                log.debug("""
//                        condition {
//                            1. p != null:{}
//                            2. ((p.flags & BI_REF) != 0):{}
//                            3. i > 1:{}, i={}
//                            4. XdagTime.getCurrentTimestamp() >= p.getTimestamp() + 2 * 1024:{}
//                        }
//                        """,
//                        p != null,
//                        ((p.getInfo().flags & BI_REF) != 0),
//                        i > 1, i,
//                        ct >= p.getTimestamp() + 2 * 1024);
//            }
//        }
    }

    /** 回退到区块block * */
    public void unWindMain(Block block) {
        if (block == null) {
            return;
        }
        log.debug("Unwind main to block,{}",Hex.toHexString(block.getHashLow()));
//        log.debug("xdagTopStatus.getTop(),{}",xdagTopStatus.getTop()==null?"null":Hex.toHexString(xdagTopStatus.getTop()));
        if (xdagTopStatus.getTop() != null) {
            for (Block tmp = getBlockByHash(xdagTopStatus.getTop(), true); tmp != null
                    && !tmp.equals(block); tmp = getMaxDiffLink(tmp, true)) {
                updateBlockFlag(tmp, BI_MAIN_CHAIN, false);
                // 更新对应的flag信息
                if ((tmp.getInfo().flags & BI_MAIN) != 0) {
                    unSetMain(tmp);
                    // TODO: 稳定分支：这里为应该可以不需要重新存储区块info
//                    blockStore.saveBlockInfo(tmp.getInfo());
                }
            }
        }
    }

    /** 执行区块并返回手续费 * */
    private UnsignedLong applyBlock(Block block) {
        UnsignedLong sumIn = UnsignedLong.ZERO;
        UnsignedLong sumOut = UnsignedLong.ZERO; // sumOut是用来支付其他区块link自己的手续费 现在先用0

        // 处理过
        if ((block.getInfo().flags & BI_MAIN_REF) != 0) {
            return UnsignedLong.ZERO.minus(UnsignedLong.ONE);
        }
        // 设置为已处理
        updateBlockFlag(block, BI_MAIN_REF, true);

        List<Address> links = block.getLinks();
        if (links == null || links.size() == 0) {
            updateBlockFlag(block, BI_APPLIED, true);
            return UnsignedLong.ZERO;
        }

        for (Address link : links) {
            Block ref = getBlockByHash(link.getHashLow(), true);
            UnsignedLong ret = applyBlock(ref);
            if (ret.compareTo(UnsignedLong.ZERO.minus(UnsignedLong.ONE))==0){
//            if (ret == -1) {
                continue;
            }
            updateBlockRef(ref, new Address(block));
            if (amount2xdag(UnsignedLong.valueOf(block.getInfo().getAmount()).plus(ret).longValue()) >= amount2xdag(
                    block.getInfo().getAmount())) {
                acceptAmount(block, ret);
            }
        }

        for (Address link : links) {
            if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                Block ref = getBlockByHash(link.getHashLow(), false);

                if (amount2xdag(ref.getInfo().getAmount()) < amount2xdag(link.getAmount().longValue())) {
                    log.debug("This input ref doesn't have enough amount,hash:{},amount:{},need:{}",Hex.toHexString(ref.getInfo().getHashlow()),ref.getInfo().getAmount(),
                            link.getAmount().longValue());
                    return UnsignedLong.ZERO;
                }
                if (amount2xdag(sumIn.plus(UnsignedLong.valueOf(link.getAmount())).longValue()) < amount2xdag(sumIn.longValue())) {
                    log.debug("This input ref's amount less than 0");
                    return UnsignedLong.ZERO;
                }
                sumIn = sumIn.plus(UnsignedLong.valueOf(link.getAmount()));
            } else {
                if (amount2xdag(sumOut.plus(UnsignedLong.valueOf(link.getAmount())).longValue()) < amount2xdag(sumOut.longValue())) {
                    log.debug("This output ref's amount less than 0");
                    return UnsignedLong.ZERO;
                }
                sumOut = sumOut.plus(UnsignedLong.valueOf(link.getAmount()));
            }
        }

        if (amount2xdag(UnsignedLong.valueOf(block.getInfo().getAmount()).plus(sumIn).longValue()) < amount2xdag(sumOut.longValue())
                || amount2xdag(UnsignedLong.valueOf(block.getInfo().getAmount()).plus(sumIn).longValue()) < amount2xdag(sumIn.longValue())) {
            log.debug("exec fail!");
            return UnsignedLong.ZERO;
        }

        for (Address link : links) {
            Block ref = getBlockByHash(link.getHashLow(), false);
            if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                acceptAmount(ref, UnsignedLong.ZERO.minus(UnsignedLong.valueOf(link.getAmount())));
            } else {
                acceptAmount(ref, UnsignedLong.valueOf(link.getAmount()));
            }
            blockStore.saveBlockInfo(ref.getInfo());
        }

        // 不一定大于0 因为可能部分金额扣除
        UnsignedLong remain = sumIn.minus(sumOut);
        acceptAmount(block, remain);
        updateBlockFlag(block, BI_APPLIED, true);
        return UnsignedLong.ZERO;
    }

    // TODO: 改递归为迭代
    private void applyBlock1(Block block) {
        List<Integer> list=new ArrayList<>();//建立一个整数列表，一个节点列表，两个栈
        List<Block> curr=new ArrayList();
        Stack<Block> stack1=new Stack();
        Stack<Block> stack2=new Stack();
        if(block!=null){  //根不空，进栈1
            stack1.push(block);
            while(!stack1.isEmpty()){//栈1不空出栈1，然后进栈2

                Block temp=stack1.pop();
                // 操作block


                // 操作结束放进stack2
                stack2.push(temp);


                List<Address> links = temp.getLinks();

                for (Address link : links) {
                    Block ref = getBlockByHash(link.getHashLow(), true);
                    stack1.push(ref);
                }
            }}
        while(!stack2.isEmpty()){//栈2不空依次出栈，值加入列表
            Block ss=stack2.pop();
        }



        Block root;
        Stack<Block> stack=new Stack<>();

        stack.push(block);

        while(!stack.empty()){
            // 操作
            root = stack.pop();
            List<Address> links = root.getLinks();

            for (Address link : links) {
                Block ref = getBlockByHash(link.getHashLow(), true);
                stack.push(ref);
            }
        }
    }

    public UnsignedLong unApplyBlock(Block block) {
        List<Address> links = block.getLinks();
        if ((block.getInfo().flags & BI_APPLIED) != 0) {
            UnsignedLong sum = UnsignedLong.ZERO;
            for (Address link : links) {
                Block ref = getBlockByHash(link.getHashLow(), false);
                if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
                    acceptAmount(ref, UnsignedLong.valueOf(link.getAmount()));
                    sum = sum.minus(UnsignedLong.valueOf(link.getAmount()));
                } else {
                    acceptAmount(ref, UnsignedLong.ZERO.minus(UnsignedLong.valueOf(link.getAmount())));
                    sum = sum.plus(UnsignedLong.valueOf(link.getAmount()));
                }
            }
            acceptAmount(block, sum);
            updateBlockFlag(block, BI_APPLIED, false);
        }
        updateBlockFlag(block, BI_MAIN_REF, false);
        updateBlockRef(block, null);

        for (Address link : links) {
            Block ref = getBlockByHash(link.getHashLow(), true);
            if (ref.getInfo().getRef() != null
                    && equalBytes(ref.getInfo().getRef(), block.getHashLow())
                    && ((ref.getInfo().flags & BI_MAIN_REF) != 0)) {
                acceptAmount(block, unApplyBlock(ref));
            }
        }
        return UnsignedLong.ZERO;
    }

    /** 设置以block为主块的主链 要么分叉 要么延长 * */
    public void setMain(Block block) {
        // 设置奖励
        long mainNumber = xdagStats.nmain + 1;
        log.info("mainNumber = {},hash = {}",mainNumber,Hex.toHexString(block.getInfo().getHash()));
        long reward = getReward(mainNumber);
        block.getInfo().setHeight(mainNumber);
        updateBlockFlag(block, BI_MAIN, true);

        // 接收奖励
        acceptAmount(block, UnsignedLong.valueOf(reward));
        xdagStats.nmain++;
//        log.debug("After SetMain, Current main height:{}",xdagStats.nmain);

//        xdagStats.totalnmain = Math.max(xdagStats.nmain,xdagStats.totalnmain);

        // 递归执行主块引用的区块 并获取手续费
        acceptAmount(block, applyBlock(block));
        // 主块REF指向自身
        // TODO:补充手续费
        updateBlockRef(block, new Address(block));

        if (randomXUtils != null) {
            randomXUtils.randomXSetForkTime(block);
        }

    }

    /** 取消Block主块身份 * */
    public void unSetMain(Block block) {

        log.debug("UnSet main,{}, mainnumber = {}",Hex.toHexString(block.getHash()),xdagStats.nmain);

        long amount = getReward(xdagStats.nmain);
        updateBlockFlag(block, BI_MAIN, false);

        xdagStats.nmain--;
//        log.debug("After unSetMain, Current main height:{}",xdagStats.nmain);

        // 去掉奖励和引用块的手续费
        acceptAmount(block, UnsignedLong.ZERO.minus(UnsignedLong.valueOf(amount)));
        acceptAmount(block, unApplyBlock(block));

        if (randomXUtils != null) {
            randomXUtils.randomXUnsetForkTime(block);
        }
        // 非主块不需要高度
        block.getInfo().setHeight(0);
    }

    @Override
    public Block createNewBlock(Map<Address, ECKeyPair> pairs, List<Address> to, boolean mining, String remark) {

        int hasRemark = remark==null?0:1;

        if (pairs == null && to == null) {
            if (mining) {
                return createMainBlock();
            }
        }
        int defKeyIndex = -1;

        // 遍历所有key 判断是否有defKey
        assert pairs != null;
        List<ECKeyPair> keys = new ArrayList<>(Set.copyOf(pairs.values()));
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(wallet.getDefKey().ecKey)) {
                defKeyIndex = i;
            }
        }

        List<Address> all = Lists.newArrayList();
        all.addAll(pairs.keySet());
        all.addAll(to);

        // TODO: 判断pair是否有重复
        // header + input size + sendto size + (key + signature)
        int res = 1 + pairs.size() + to.size() + 3*keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;

        // TODO : 如果区块字段不足
        if (res > 16) {
            return null;
        }

        long sendTime = XdagTime.getCurrentTimestamp();
//        if (mining) {
//            res += 1;
//            sendTime = XdagTime.getMainTime();
//            if (res > 16) {
//                return null;
//            }
//        }

        List<Address> refs = Lists.newArrayList();

        Address preTop = null;
        byte[] pretopHash = getPreTopMainBlockForLink(sendTime);
        if (pretopHash != null) {
            if (getBlockByHash(pretopHash,false).getTimestamp() < sendTime ) {
                preTop = new Address(pretopHash, XdagField.FieldType.XDAG_FIELD_OUT);
                res++;
            }
        }

        if (res < 16) {
            if (preTop!=null) {
                refs.add(preTop);
            }
            List<Address> orphan = getBlockFromOrphanPool(16 - res, sendTime);
            if(orphan!=null && orphan.size() != 0) {
                refs.addAll(orphan);
            }
            return new Block(sendTime, all, refs, mining, keys, remark, defKeyIndex);
        }

        return new Block(sendTime, all, refs, mining, keys, remark, defKeyIndex);
    }

    public Block createMainBlock() {
        // <header + remark + outsig + nonce>
        int res = 1 + 1 + 2 + 1;

        long sendTime = XdagTime.getMainTime();
        Address preTop = null;
        byte[] pretopHash = getPreTopMainBlockForLink(sendTime);
        if (pretopHash != null) {
            preTop = new Address(pretopHash, XdagField.FieldType.XDAG_FIELD_OUT);
            res++;
        }
        List<Address> refs = Lists.newArrayList();
        if(preTop != null) {
            refs.add(preTop);
        }
        List<Address> orphans = getBlockFromOrphanPool(16 - res, sendTime);
        if(CollectionUtils.isNotEmpty(orphans)) {
            refs.addAll(orphans);
        }
        return new Block(sendTime, null, refs, true, null, kernel.getConfig().getPoolTag(), -1);
    }

    /**
     *
     * @Description 从orphan中获取一定数量的orphan块用来link
     *
     * @Param [num]
     *
     * @return java.util.List<io.xdag.core.Address>
     **/
    public List<Address> getBlockFromOrphanPool(int num , long sendtime) {
        return orphanPool.getOrphan(num, sendtime);
    }

    public byte[] getPreTopMainBlockForLink(long sendTime) {
        long mainTime = XdagTime.getEpoch(sendTime);
        Block topInfo;
        if (xdagTopStatus.getTop() == null) {
            return null;
        }

        topInfo = getBlockByHash(xdagTopStatus.getTop(), false);
        if(topInfo == null) {
            return null;
        }
        if (XdagTime.getEpoch(topInfo.getTimestamp()) == mainTime) {
            return xdagTopStatus.getPreTop();
        } else {
            return xdagTopStatus.getTop();
        }
    }

    //    public void setPreTop(Block block) {
//        if (block == null) {
//            return;
//        }
//        if (XdagTime.getEpoch(block.getTimestamp()) > XdagTime.getCurrentEpoch()) {
//            return;
//        }
//        BigInteger blockDiff = calculateBlockDiff(block);
//        if (xdagStats.getPreTop() == null) {
//            xdagStats.setPreTop(block.getHashLow().clone());
//            xdagStats.setPreTopDiff(blockDiff);
//            block.setPretopCandidate(true);
//            block.setPretopCandidateDiff(blockDiff);
//            return;
//        }
//
//        if (blockDiff.compareTo(xdagStats.getPreTopDiff()) > 0) {
//            xdagStats.setPreTop(block.getHashLow().clone());
//            xdagStats.setPreTopDiff(blockDiff);
//            block.setPretopCandidate(true);
//            block.setPretopCandidateDiff(blockDiff);
//        }
//    }
    public void setPreTop(Block block) {
        if (block == null) {
            return;
        }
        if (XdagTime.getEpoch(block.getTimestamp()) > XdagTime.getCurrentEpoch()) {
            return;
        }
        BigInteger blockDiff = calculateBlockDiff(block);
        if (xdagTopStatus.getPreTop() == null) {
            xdagTopStatus.setPreTop(block.getHashLow().clone());
            xdagTopStatus.setPreTopDiff(blockDiff);
            block.setPretopCandidate(true);
            block.setPretopCandidateDiff(blockDiff);
            return;
        }

        if (blockDiff.compareTo(xdagTopStatus.getPreTopDiff()) > 0) {
            xdagTopStatus.setPreTop(block.getHashLow().clone());
            xdagTopStatus.setPreTopDiff(blockDiff);
            block.setPretopCandidate(true);
            block.setPretopCandidateDiff(blockDiff);
        }
    }

    /** 计算区块在链上的难度 同时设置难度 和最大难度连接 并返回区块难度 * */
    public BigInteger calculateBlockDiff(Block block) {

        if (block.getInfo().getDifficulty() != null) {
//            log.debug("block 的难度不为空，hash[{}]", Hex.toHexString(block.getHash()));
            return block.getInfo().getDifficulty();
        }

        BigInteger diff0;
        // 初始区块自身难度设置
        if (randomXUtils!=null && randomXUtils.isRandomxFork(XdagTime.getEpoch(block.getTimestamp()))
                && XdagTime.isEndOfEpoch(block.getTimestamp())) {
            diff0 = getDiffByRandomXHash(block);
        } else {
            diff0 = getDiffByRawHash(block.getHash());
        }
        block.getInfo().setDifficulty(diff0);

        BigInteger maxDiff = diff0;
        Address maxDiffLink = null;

        // 临时区块
        Block tmpBlock;
        if (block.getLinks().size() == 0) {
            return diff0;
        }

        // 遍历所有link 找maxLink
        List<Address> links = block.getLinks();
        for (Address ref : links) {
            Block refBlock = getBlockByHash(ref.getHashLow(), false);
            if(refBlock == null) {
                break;
            }
            // 如果引用的那个快的epoch 小于当前这个块的回合
            if ( XdagTime.getEpoch(refBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp())) {
                // 如果难度大于当前最大难度
                BigInteger refDifficulty = refBlock.getInfo().getDifficulty();
                if(refDifficulty == null) {
                    refDifficulty = BigInteger.ZERO;
                }
                BigInteger curDiff = refDifficulty.add(diff0);
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
                        && (XdagTime.getEpoch(tmpBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp()))) {
                    curDiff = tmpBlock.getInfo().getDifficulty().add(diff0);
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

        block.getInfo().setDifficulty(maxDiff);

//        block.setMaxDiffLink(maxDiffLink);
        if(maxDiffLink != null) {
            block.getInfo().setMaxDiffLink(maxDiffLink.getHashLow());
        }
        return maxDiff;
    }

    public BigInteger getDiffByRandomXHash(Block block) {
        long epoch = XdagTime.getEpoch(block.getTimestamp());
        byte[] data = new byte[64];
        byte[] rxHash = Hash.sha256(BytesUtils.subArray(block.getXdagBlock().getData(),0,512-32));
        System.arraycopy(rxHash,0,data,0,32);
        System.arraycopy(block.getXdagBlock().getField(15).getData(),0,data,32,32);
        byte[] hash = Arrays.reverse(randomXUtils.randomXBlockHash(data,data.length,epoch));
        if (hash != null) {
//            log.debug("New Block randomX hash:{}",Hex.toHexString(hash));
            return getDiffByRawHash(hash);
        }
        return getDiffByRawHash(block.getHash());
    }

    public BigInteger getDiffByRawHash(byte[] hash) {
        return getDiffByHash(hash);
    }

    @Override
    public Block getBlockByHeight(long height) {
        if (height > xdagStats.nmain) {
            return null;
        }

        Block block;
        int i = 0;
        for (block = getBlockByHash(xdagTopStatus.getTop(), false); block!=null && (i < xdagStats.nmain); block = getBlockByHash(block.getInfo().getMaxDiffLink(),false) ) {
            if ( (block.getInfo().getFlags() & BI_MAIN )!= 0) {
                if (height == block.getInfo().getHeight()) {
                    break;
                }
                ++i;
            }
        }
        return block;
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
        if (block.getInfo().getMaxDiffLink() != null) {
            return getBlockByHash(block.getInfo().getMaxDiffLink(), isRaw);
        }
        return null;
    }

    public void removeOrphan(byte[] hashlow, OrphanRemoveActions action) {
        Block b = getBlockByHash(hashlow, false);
        if (b != null && ((b.getInfo().flags & BI_REF) == 0) && (action != OrphanRemoveActions.ORPHAN_REMOVE_EXTRA || (b.getInfo().flags & BI_EXTRA) != 0)) {
            // 如果removeBlock是BI_EXTRA
            if ((b.getInfo().flags & BI_EXTRA) != 0) {
//                log.debug("移除Extra");
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
                    if(removeBlockRaw != null) {
                        List<Address> all = removeBlockRaw.getLinks();
                        for(Address addr : all) {
                            removeOrphan(addr.getHashLow(), OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
                        }
                    }
                }
                // 更新removeBlockRaw的flag
                // nextra减1
                updateBlockFlag(removeBlockRaw, BI_EXTRA, false);
                xdagStats.nextra--;
            } else {
                orphanPool.deleteByHash(b.getHashLow());
                xdagStats.nnoref--;
            }
            // 更新这个块的flag
            updateBlockFlag(b, BI_REF, true);
        }
    }

    public void updateBlockFlag(Block block, byte flag, boolean direction) {
        if(block == null) {
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
        if(ref == null) {
            block.getInfo().setRef(null);
        } else {
            block.getInfo().setRef(ref.getHashLow());
        }
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
    }

    public void saveBlock(Block block) {
        if(block == null) {
            return;
        }
        block.isSaved = true;
//        log.info("save block:{}", Hex.toHexString(block.getHashLow()));
        blockStore.saveBlock(block);
        // 如果是自己的账户
        if (memOurBlocks.containsKey(new ByteArrayWrapper(block.getHash()))) {
//            log.info("new account:{}", Hex.toHexString(block.getHash()));
            if (xdagStats.getOurLastBlockHash() == null) {
//                log.info("Global miner");
                xdagStats.setGlobalMiner(block.getHash());
                blockStore.saveXdagStatus(xdagStats);
            }
            addOurBlock(memOurBlocks.get(new ByteArrayWrapper(block.getHash())), block);
            memOurBlocks.remove(new ByteArrayWrapper(block.getHash()));
        }

        if (block.isPretopCandidate()) {
            xdagTopStatus.setPreTop(block.getHashLow().clone());
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
        boolean canUse = false;
        List<ECKeyPair> ecKeys = block.verifiedKeys();
        List<Address> inputs = block.getInputs();
        if (inputs == null || inputs.size() == 0) {
            return true;
        }
        for (Address in : inputs) {
            Block inBlock = getBlockByHash(in.getHashLow(), true);
            byte[] subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);
//            log.debug("verify encoded:{}", Hex.toHexString(subdata));
            ECDSASignature sig = inBlock.getOutsig();

            for (ECKeyPair ecKey : ecKeys) {
                byte[] publicKeyBytes = ECKeyPair.compressPubKey(ecKey.getPublicKey());
                byte[] digest = BytesUtils.merge(subdata, publicKeyBytes);
//                log.debug("verify encoded:{}", Hex.toHexString(digest));
                byte[] hash = Hash.hashTwice(digest);
                if (ECKeyPair.verify(hash, sig, publicKeyBytes)) {
                    canUse = true;
                }
            }

            if (!canUse) {
                //TODO this maybe some old issue( input and output was same )
                List<ECKeyPair> keys = block.getPubKeys();
                for (ECKeyPair ecKey : keys) {
                    byte[] publicKeyBytes = ECKeyPair.compressPubKey(ecKey.getPublicKey());
                    byte[] hash = Hash.hashTwice(BytesUtils.merge(subdata, publicKeyBytes));
                    if (ECKeyPair.verify(hash, sig, publicKeyBytes)) {
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
        ECDSASignature signature = block.getOutsig();
        // 遍历所有key
        for (int i = 0; i < ourkeys.size(); i++) {
            ECKeyPair ecKey = ourkeys.get(i).ecKey;
            byte[] publicKeyBytes = Sign.publicKeyBytesFromPrivate(ecKey.getPrivateKey(), true);
            byte[] digest = BytesUtils.merge(
                    block.getSubRawData(block.getOutsigIndex() - 2), publicKeyBytes);
            byte[] hash = Hash.hashTwice(digest);
            if (ecKey.verify(hash, signature)) {
                log.info("Validate Success");
                addOurBlock(i, block);
                return true;
            }
        }
        return false;
    }

    public void addOurBlock(int keyIndex, Block block) {
        xdagStats.setOurLastBlockHash(block.getHash());
        if (!block.isSaved()) {
            memOurBlocks.put(new ByteArrayWrapper(block.getHash()), keyIndex);
        } else {
            blockStore.saveOurBlock(keyIndex, block.getInfo().getHashlow());
        }
    }

    public void removeOurBlock(Block block) {
        if (!block.isSaved) {
            memOurBlocks.remove(new ByteArrayWrapper(block.getHash()));
        } else {
            blockStore.removeOurBlock(block.getHashLow());
        }
    }

    public long getReward(long nmain) {
        long start = getStartAmount(nmain);
        return start >> (nmain >> MAIN_BIG_PERIOD_LOG);
    }

    @Override
    public long getSupply(long nmain) {
        UnsignedLong res = UnsignedLong.valueOf(0L);
        long amount = getStartAmount(nmain);
        long current_nmain = nmain;
        while ((current_nmain >> MAIN_BIG_PERIOD_LOG) > 0) {
            res = res.plus(UnsignedLong.fromLongBits(1L << MAIN_BIG_PERIOD_LOG).times(UnsignedLong.valueOf(amount)));
            current_nmain -= 1L << MAIN_BIG_PERIOD_LOG;
            amount >>= 1;
        }
        res = res.plus(UnsignedLong.valueOf(current_nmain).times(UnsignedLong.valueOf(amount)));
        long fork_height = Config.MAINNET?MAIN_APOLLO_HEIGHT:MAIN_APOLLO_TESTNET_HEIGHT;
        if(nmain >= fork_height) {
            // add before apollo amount
            res = res.plus(UnsignedLong.valueOf(fork_height - 1).times(UnsignedLong.valueOf(MAIN_START_AMOUNT - MAIN_APOLLO_AMOUNT)));
        }
        return res.longValue();
    }

    @Override
    public List<Block> getBlocksByTime(long starttime, long endtime) {
        return blockStore.getBlocksUsedTime(starttime, endtime);
    }

    @Override
    public void startCheckMain() {
        if(checkLoop == null) {
            return;
        }
        checkLoopFuture = checkLoop.scheduleAtFixedRate(this::checkMain, 0, 1024, TimeUnit.MILLISECONDS);
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
            e.printStackTrace();
        }
    }

    public long getStartAmount(long nmain) {
        long startAmount;
        long forkHeight = Config.MAINNET ? MAIN_APOLLO_HEIGHT : MAIN_APOLLO_TESTNET_HEIGHT;
        if (nmain >= forkHeight) {
            startAmount = MAIN_APOLLO_AMOUNT;
        } else {
            startAmount = MAIN_START_AMOUNT;
        }

        return startAmount;
    }

    /** 为区块block添加amount金额 * */
    private void acceptAmount(Block block, UnsignedLong amount) {
        block.getInfo().setAmount(UnsignedLong.valueOf(block.getInfo().getAmount()).plus(amount).longValue());
        if (block.isSaved) {
            blockStore.saveBlockInfo(block.getInfo());
        }
        if ((block.getInfo().flags & BI_OURS) != 0) {
            xdagStats.setBalance(amount.plus(UnsignedLong.valueOf(xdagStats.getBalance())).longValue());
        }
    }

    /** 判断是否已经接收过区块 * */
    public boolean isExist(byte[] hashlow) {
        return memOrphanPool.containsKey(new ByteArrayWrapper(hashlow)) ||
                blockStore.hasBlock(hashlow);
    }

    @Override
    public List<Block> listMainBlocks(int count) {
        Block temp = getBlockByHash(xdagTopStatus.getTop(), false);
        if(temp == null) {
            temp = getBlockByHash(xdagTopStatus.getPreTop(), false);
        }
        List<Block> res = new ArrayList<>();
        while (count > 0) {
            if (temp == null) {
                break;
            }
            if ((temp.getInfo().flags & BI_MAIN) != 0) {
                count--;
                res.add((Block) temp.clone());
            }
            if (temp.getInfo().getMaxDiffLink() == null) {
                break;
            }
            temp = getBlockByHash(temp.getInfo().getMaxDiffLink(), false);
        }
        return res;
    }

    // TODO: 列出本矿池生成的主块，如果本矿池只在前期产块或者从未产块，会导致需要遍历所有的区块数据，这部分应该需要优化
    @Override
    public List<Block> listMinedBlocks(int count) {
        Block temp = getBlockByHash(xdagTopStatus.getTop(), false);
        if(temp == null) {
            temp = getBlockByHash(xdagTopStatus.getPreTop(), false);
            //                log.error("Pretop is null");
        }
        List<Block> res = new ArrayList<>();
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
            temp = getBlockByHash(temp.getInfo().getMaxDiffLink(), false);
        }
        return res;
    }

    public Map<ByteArrayWrapper, Integer> getMemOurBlocks() {
        return memOurBlocks;
    }

    enum OrphanRemoveActions {
        ORPHAN_REMOVE_NORMAL, ORPHAN_REMOVE_REUSE, ORPHAN_REMOVE_EXTRA
    }
}
