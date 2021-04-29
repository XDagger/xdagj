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
package io.xdag.mine.manager;

import static io.xdag.config.Config.AWARD_EPOCH;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static java.lang.Math.E;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.xdag.core.*;
import io.xdag.crypto.ECKeyPair;
import io.xdag.mine.MinerChannel;
import io.xdag.utils.*;
import io.xdag.wallet.OldWallet;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.consensus.Task;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class AwardManagerImpl implements AwardManager, Runnable {
    /** 每一轮的确认数是16 */
    private static final int CONFIRMATIONS_COUNT = 16;
    /** 矿池自己的收益 */
    private static double poolRation;
    /** 出块矿工占比 */
    private static double minerRewardRation;
    /** 基金会的奖励 */
    private static double fundRation;
    /** 矿工的参与奖励 */
    private static double directRation;
    private final double DBL = 2.2204460492503131E-016;

    // 定义每一个部分的收益占比
    protected Miner poolMiner;
    /** 存放的是过去十六个区块的hash */
    protected List<ByteArrayWrapper> blockHashs = new CopyOnWriteArrayList<ByteArrayWrapper>();
    protected List<ByteArrayWrapper> minShares = new CopyOnWriteArrayList<>(new ArrayList<>(16));
    protected long currentTaskTime;
    protected long currentTaskIndex;
    protected Config config;
    private List<Miner> miners;
    @Setter
    private MinerManager minerManager;
    private final Kernel kernel;
    private final Blockchain blockchain;
    private final OldWallet xdagWallet;

    private ArrayList<Double> diff = new ArrayList<>();
    private ArrayList<Double> prev_diff = new ArrayList<>();

    public AwardManagerImpl(Kernel kernel) {
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.blockchain = kernel.getBlockchain();
        this.xdagWallet = kernel.getWallet();
        this.poolMiner = kernel.getPoolMiner();
        this.minerManager = kernel.getMinerManager();
        init();
        setPoolConfig();
    }
    private final BlockingQueue<AwardBlock> awardBlockBlockingQueue = new LinkedBlockingQueue<>();
    private Thread t;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AwardBlock awardBlock = awardBlockBlockingQueue.take();
                payAndaddNewAwardBlock(awardBlock);
            }catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(" can not take the awardBlock from awardBlockQueue ");
                break;
            }
        }
    }

    @Override
    public void start() {
        if (t == null) {
            t = new Thread(this, "AwardManagerImpl");
            t.start();
        }
    }

    @Override
    public void stop() {
        if (t != null) {
            try {
                t.interrupt();
                t.join();
            } catch (InterruptedException e) {
                log.error("Failed to stop AwardManagerImpl");
                Thread.currentThread().interrupt();
            }
            t = null;
        }
    }




    @Override
    public void addAwardBlock(byte[] share, byte[] hash, long generateTime){
        AwardBlock awardBlock = new AwardBlock();
        awardBlock.share = share;
        awardBlock.hash = hash;
        awardBlock.generateTime = generateTime;
        if (!awardBlockBlockingQueue.offer(awardBlock)) {
            log.error("Failed to add a awardBlock to the b queue!" );
        }
    }

    /** 计算一个矿工所有未支付的数据 返回的是一个平均的 diff 对过去的十六个难度的平均值 */
    private static double processOutdatedMiner(Miner miner) {
        log.debug("processOutdatedMiner");
        double sum = 0.0;
        int diffcount = 0;
        double temp;
        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
            if ((temp = miner.getMaxDiffs(i)) > 0) {
                sum += temp;
                miner.setMaxDiffs(i, 0.0);
                ++diffcount;
            }
        }

        if (diffcount > 0) {
            sum /= diffcount;
        }
        return sum;
    }

    /** 实现一个由难度转换为pay 的函数 */
    private static double diffToPay(double sum, int diffCount) {
        double result = 0.0;
        if (diffCount > 0) {
            result = Math.pow(E, ((sum / diffCount) - 20)) * diffCount;
        }
        return result;
    }

    public void init() {
        log.debug("容器初始化");
        // 容器的初始化
        for (int i = 0; i < 16; i++) {
            blockHashs.add(null);
            minShares.add(null);
        }
    }

    /** 给矿池设置一些支付上的参数 */
    private void setPoolConfig() {
        poolRation = BigDecimalUtils.div(config.getPoolRation(), 100);
        if (poolRation < 0) {
            poolRation = 0;
        } else if (poolRation > 1) {
            poolRation = 1;
        }

        minerRewardRation = BigDecimalUtils.div(config.getRewardRation(), 100);
        if (minerRewardRation < 0) {
            minerRewardRation = 0;
        } else if (poolRation + minerRewardRation > 1) {
            minerRewardRation = 1 - poolRation;
        }

        directRation = BigDecimalUtils.div(config.getDirectRation(), 100);
        if (directRation < 0) {
            directRation = 0;
        } else if (poolRation + minerRewardRation + directRation > 1) {
            directRation = 1 - poolRation - minerRewardRation;
        }

        fundRation = BigDecimalUtils.div(config.getFundRation(), 100);
        if (fundRation < 0) {
            fundRation = 0;
        } else if (poolRation + minerRewardRation + directRation + fundRation > 1) {
            fundRation = 1 - poolRation - minerRewardRation - directRation;
        }
    }

    /**
     * 对主块进行支付 并且设置当前这一轮主块的相关信息
     */
    public void payAndaddNewAwardBlock(AwardBlock awardBlock) {
        log.debug("Pay miner");
        payMiners(awardBlock.generateTime);
        log.debug("set index:" + (int) ((awardBlock.generateTime >> 16) & AWARD_EPOCH));
        blockHashs.set((int) ((awardBlock.generateTime >> 16) & AWARD_EPOCH), new ByteArrayWrapper(awardBlock.hash));
        minShares.set((int) ((awardBlock.generateTime >> 16) & AWARD_EPOCH), new ByteArrayWrapper(awardBlock.share));
    }

    @Override
    public Miner getPoolMiner() {
        return poolMiner;
    }

    @Override
    public void setPoolMiner(byte[] hash) {
        this.poolMiner = new Miner(hash);
    }

    @Override
    public void onNewTask(Task task) {
        currentTaskTime = task.getTaskTime();
        currentTaskIndex = task.getTaskIndex();
    }

    /**
     * @param time
     *            时间段
     * @return 错误代码   -1 没有矿工参与挖矿 不进行支付操作 -2 找不到对应的区块hash 或者 结果nonce -3 找不到对应的区块
     *                  -4区块余额不足，不是主块不进行支付 -5 余额分配失败 -6 找不到签名密钥 -7 难度太小 不予支付
     */
    public int payMiners(long time) {
        log.debug("=========== start  payMiners for time [{}]===========",time);
        // 获取到的是当前任务的对应的+1的位置 以此延迟16轮
        int index = (int) (((time >> 16) + 1) & AWARD_EPOCH);
        int keyPos = -1;
        int minerCounts = 0;
        PayData payData = new PayData();

        // 每一个区块最多可以放多少交易 这个要由密钥的位置来决定
        int payminersPerBlock = 0;
        miners = new ArrayList<>();
        // 统计矿工的数量
        if(minerManager != null) {
            for (Miner miner : minerManager.getActivateMiners().values()) {
                miners.add(miner);
                minerCounts++;
                log.debug("矿工的数量为[{}]",minerCounts);
                //log.debug("添加的矿工地址为[{}],共计[{}]个矿工",Hex.toHexString(miner.getAddressHash()), minerCounts);
            }
        }

        // 没有矿工挖矿，直接全部收入交由地址块
        if (minerCounts <= 0) {
            log.debug("no miners");
            return -1;
        }

        // 获取到要计算的hash 和对应的nocne
        byte[] hash = blockHashs.get(index) == null ? null : blockHashs.get(index).getData();
        byte[] nonce = minShares.get(index) == null ? null : minShares.get(index).getData();

        if (hash == null || nonce == null) {
            log.debug("can not find  the hash or nonce ,hash is null？[{}],nonce is null ?[{}]", hash == null, nonce == null);
            return -2;
        }

        // 获取到这个区块 查询时要把前面的置0
        byte[] hashlow = BytesUtils.fixBytes(hash, 8, 24);
        Block block = blockchain.getBlockByHash(hashlow, false);
        //TODO
        log.debug("Hash low : "+Hex.toHexString(hashlow));
        if (keyPos < 0) {
            if (kernel.getBlockchain().getMemOurBlocks().get(new ByteArrayWrapper(hashlow)) == null) {
                keyPos = kernel.getBlockStore().getKeyIndexByHash(hashlow);
            } else {
                keyPos = kernel.getBlockchain().getMemOurBlocks().get(new ByteArrayWrapper(hashlow));
            }
            log.debug("keypos : "+keyPos);
            if (keyPos < 0){
                return -2;
            }
        }

        if (block == null) {
            log.debug("can't find the block");
            return -3;
        }

        payData.balance = block.getInfo().getAmount();

        if (payData.balance <= 0) {
            log.debug("no main block,can't pay");
            return -4;
        }

        // 计算矿池部分的收益
        payData.poolFee = BigDecimalUtils.mul(payData.balance, poolRation);
        payData.unusedBalance = payData.balance - payData.poolFee;

        // 进行各部分奖励的计算
        if (payData.unusedBalance <= 0) {
            log.debug("Balance no enough");
            return -5;
        }

        if (keyPos < 0) {
            log.debug("can't find the key");
            return -6;
        }

        // 决定一个区块是否需要再有一个签名字段
        // todo 这里不够严谨把 如果时第三把第四把呢
        if (xdagWallet.getKey_internal().size() - 1 == keyPos) {
            payminersPerBlock = 11;
        } else {
            payminersPerBlock = 9;
        }

//        poolMiner.setDiffSum(time, 0.0);
//        poolMiner.setPrevDiffSum(time, minerCounts);

        // 真正处理的数据是在这一块
        // 这个函数会把每一个矿工的计算出来的diff 和prevdiff 都存在上面的列表
        // prevDiffSum 是每一个矿工的本轮计算的难度 加上以前所有难度之和
        // diffs 是本轮进行的计算

        //同样开辟两个数组

        diff = new ArrayList<>(minerCounts);
        prev_diff = new ArrayList<>(minerCounts);
        Collections.fill(diff, 0.0);
        Collections.fill(prev_diff, 0.0);
        double prevDiffSum = precalculatePayments(nonce, index, payData);

        log.debug("after cal prevdiffSum为[{}]",prevDiffSum);
        if (prevDiffSum <= DBL) {
            log.debug("diff is too low");
            return -7;
        }

        // 通过precalculatePay后计算出的数据 进行计算
        doPayments(hashlow, payminersPerBlock, payData, keyPos);
        log.debug("=========== end  payMiners for time [{}]===========",time);
        return 0;
    }

    private double precalculatePayments(byte[] nonce, int index, PayData payData) {
        log.debug("precalculatePayments........");
        //这里缺少对矿池的计算
        //现对矿池进行计算
        payData.prevDiffSums = countpay(poolMiner,index,payData);

        //遍历每一个矿工进行运行 获取到对应的数据
        for (int i = 0; i < miners.size(); i++) {
            Miner miner = miners.get(i);
            prev_diff.add(i,countpay(miner, index, i));

            payData.diffSums += diff.get(i);
            payData.prevDiffSums += prev_diff.get(i);

            if (payData.rewardMiner == null
                    && (FastByteComparisons.compareTo(nonce, 8, 24, miner.getAddressHash(), 8, 24) == 0)) {
                payData.rewardMiner = new byte[32];
                payData.rewardMiner = miner.getAddressHash();
                // 有可以出块的矿工 分配矿工的奖励
                payData.minerReward = BigDecimalUtils.mul(payData.balance, minerRewardRation);
                payData.unusedBalance -= payData.minerReward;
            }
        }

        for (Map.Entry<InetSocketAddress, MinerChannel> entry : minerManager.getActivateMinerChannels().entrySet()){
            MinerChannel channel = entry.getValue();
            if (channel.getMaxDiffs(index) > 0 ) {
                channel.addMaxDiffs(index, 0);
            }
            channel.setPrevDiff(0.0);
            channel.setPrevDiffCounts(0);
        }

        // 要进行参与奖励的支付
        if (payData.diffSums > 0) {
            payData.directIncome = BigDecimalUtils.mul(payData.balance, directRation);
            payData.unusedBalance -= payData.directIncome;
        }
        return payData.prevDiffSums;
    }

    /**
     * 对矿工之前的挖矿的难度进行计算 主要是用于形成支付的权重
     *
     * @param miner
     *            矿工的结构体
     * @param index
     *            对应的要计算的难度编号
     */
    private Double countpay(Miner miner, int index,int i) {
        double diffSum = 0.0;
        int diffCount = 0;
        // 这里是如果矿工的
        if (miner.getMinerStates() == MinerStates.MINER_ARCHIVE
                &&
                // c好过十六个时间戳没有进行计算
                currentTaskIndex - miner.getTaskIndex() > 16) {
            // 这个主要是为了超过十六个快没有挖矿 所以要给他支付
            diffSum += processOutdatedMiner(miner);
            diffCount++;
        } else if (miner.getMaxDiffs(index) > 0) {
            diffSum += miner.getMaxDiffs(index);
            miner.setMaxDiffs(index, 0.0);
            ++diffCount;
        }
        diff.add(i, diffToPay(diffSum, diffCount));
        diffSum += miner.getPrevDiff();
        diffCount += miner.getPrevDiffCounts();

        miner.setPrevDiff(0.0);
        miner.setPrevDiffCounts(0);

        return diffToPay(diffSum, diffCount);

    }

    private Double countpay(Miner miner, int index,PayData payData) {
        double diffSum = 0.0;
        int diffCount = 0;
        // 这里是如果矿工的
        if (miner.getMinerStates() == MinerStates.MINER_ARCHIVE
                &&
                // c好过十六个时间戳没有进行计算
                currentTaskIndex - miner.getTaskIndex() > 16) {
            // 这个主要是为了超过十六个快没有挖矿 所以要给他支付
            diffSum += processOutdatedMiner(miner);
            diffCount++;
        } else if (miner.getMaxDiffs(index) > 0) {
            diffSum += miner.getMaxDiffs(index);
            miner.setMaxDiffs(index, 0.0);
            ++diffCount;
        }

        payData.diffSums = diffToPay(diffSum, diffCount);
        diffSum += miner.getPrevDiff();
        diffCount += miner.getPrevDiffCounts();

        miner.setPrevDiff(0.0);
        miner.setPrevDiffCounts(0);

        return diffToPay(diffSum, diffCount);

    }

    public void doPayments(byte[] hash, int paymentsPerBlock, PayData payData, int keyPos) {
        log.debug("Do payment");
        ArrayList<Address> receipt = new ArrayList<>(paymentsPerBlock - 1);
        Map<Address, ECKeyPair> inputMap = new HashMap<>();
        Address input = new Address(hash, XDAG_FIELD_IN);
        ECKeyPair inputKey = xdagWallet.getKeyByIndex(keyPos);
        inputMap.put(input, inputKey);
        long payAmount = 0L;
        /**
         * 基金会和转账矿池部分代码 暂时不用 //先支付给基金会 long fundpay =
         * BasicUtils.xdag2amount(payData.fundIncome); byte[] fund =
         * BytesUtils.fixBytes(BasicUtils.address2Hash(Constants.FUND_ADDRESS),8,24);
         *
         * <p>
         * receipt.add(new
         * Address(fund,XDAG_FIELD_OUT,BasicUtils.xdag2amount(payData.fundIncome)));
         * payAmount += payData.fundIncome;
         *
         * <p>
         * //支付给矿池 实际上放着不动 就属于矿池 receipt.add(new
         * Address(poolMiner.getAddressLow(),XDAG_FIELD_OUT,payData.poolFee)); payAmount
         * += payData.poolFee;
         */
        // 不断循环 支付给矿工
        //// TODO: 2021/4/19  打印矿工的数据
        for (int i = 0; i < miners.size(); i++) {
            Miner miner = miners.get(i);
            log.debug("Do payments for every miner,miner address = [{}]",Hex.toHexString(miner.getAddressHash()));
            // 保存的是一个矿工所有的收入
            long paymentSum = 0L;
            // 根据历史记录分发奖励
            if (payData.prevDiffSums > 0) {
                double per = BigDecimalUtils.div(prev_diff.get(i), payData.prevDiffSums);
                // paymentSum += (long)payData.unusedBalance * per;
                paymentSum += BigDecimalUtils.mul(payData.unusedBalance, per);
            }
            // 计算当前这一轮
            if (payData.diffSums > 0) {
                double per = BigDecimalUtils.div(diff.get(i), payData.diffSums);
                // paymentSum += (long)payData.directIncome * per;
                paymentSum += BigDecimalUtils.mul(payData.directIncome, per);
            }
            if (payData.rewardMiner != null
                    && FastByteComparisons.compareTo(payData.rewardMiner, 8, 24, miner.getAddressHash(), 8, 24) == 0) {
                paymentSum += payData.minerReward;
            }
            if (paymentSum < 0.000000001) {
                continue;
            }
            payAmount += paymentSum;
            receipt.add(new Address(miner.getAddressHaashLow(), XDAG_FIELD_OUT, paymentSum));
            if (receipt.size() == paymentsPerBlock) {

                transaction(hash, receipt, payAmount, keyPos);
                payAmount = 0L;
                receipt.clear();
            }
        }

        if (receipt.size() > 0) {
            transaction(hash, receipt, payAmount, keyPos);
            payAmount = 0L;
            receipt.clear();
        }
    }

    public void transaction(byte[] hashLow, ArrayList<Address> receipt, long payAmount, int keypos) {
        log.debug("All Payment: {}", payAmount);
        log.debug("unlock keypos =[{}]",keypos);
        for (Address address : receipt) {
            log.debug("pay data: {}", Hex.toHexString(address.getData()));
        }
        Map<Address, ECKeyPair> inputMap = new HashMap<>();
        Address input = new Address(hashLow, XDAG_FIELD_IN, payAmount);
        ECKeyPair inputKey = xdagWallet.getKeyByIndex(keypos);
        inputMap.put(input, inputKey);
        Block block = blockchain.createNewBlock(inputMap, receipt, false, null);
        if (inputKey.equals(xdagWallet.getDefKey().ecKey)) {
            block.signOut(inputKey);
        } else {
            block.signIn(inputKey);
            block.signOut(xdagWallet.getDefKey().ecKey);
        }
        log.debug("pay block hash【{}】", Hex.toHexString(block.getHash()));

        // todo 需要验证还是直接connect
        kernel.getSyncMgr().validateAndAddNewBlock(new BlockWrapper(block, 5));
        // kernel.getBlockchain().tryToConnect(block);
    }

    /** 内部类 用于计算支付数据 */
    public static class PayData {
        // 整个区块用于支付的金额
        long balance;
        // 每一次扣除后剩余的钱 回合所用矿工平分
        long unusedBalance;
        // 矿池自己的收入
        long poolFee;
        // 出块矿工的奖励
        long minerReward;
        // 参与挖矿的奖励
        long directIncome;
        // 基金会的奖励
        long fundIncome;
        // 所有矿工diff 的难度之和
        double diffSums;
        // 所有矿工prevdiff的难度之和
        double prevDiffSums;
        // 记录奖励矿工的位置
        byte[] rewardMiner = null;
    }


    /** 用于记录奖励主块的信息 */
    public static class AwardBlock {
        byte[] share;
        byte[] hash;
        long generateTime;

    }
}
