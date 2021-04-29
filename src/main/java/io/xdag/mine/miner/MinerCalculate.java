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
package io.xdag.mine.miner;

import io.xdag.config.Constants;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BigDecimalUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FormatDateUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.Map;

import static io.xdag.config.Config.AWARD_EPOCH;
import static io.xdag.utils.FastByteComparisons.compareTo;
import static java.lang.Math.E;

@Slf4j
public class MinerCalculate {
    private static final int NSAMPLES_MAX = 255;

    /** 每一轮的确认数是16 */
    private static final int CONFIRMATIONS_COUNT = Constants.CONFIRMATIONS_COUNT;

    /** 实现一个由难度转换为pay 的函数 */
    public static double diffToPay(double sum, int diffCount) {
        double result = 0.0;
        if (diffCount > 0) {
            result = Math.pow(E, ((sum / diffCount) - 20)) * diffCount;
        }
        return result;
    }

//    /** 计算一个矿工所有未支付的数据 返回的是一个平均的 diff 对过去的十六个难度的平均值 */
//    public static double processOutdatedMiner(Miner miner) {
//        double sum = 0.0;
//        int diffcount = 0;
//        double temp;
//        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
//            if ((temp = miner.getMaxDiffs(i)) > 0) {
//                sum = BigDecimalUtils.add(sum, temp);
//                miner.setMaxDiffs(i, 0.0);
//                ++diffcount;
//            }
//        }
//        if (diffcount > 0) {
//            sum /= diffcount;
//        }
//        return sum;
//    }

    /** 用于打印矿工但钱未支付的难度总和 */
    public static double calculateUnpaidShares(Miner miner) {
        double sum = miner.getPrevDiff();
        int count = miner.getPrevDiffCounts();
        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
            if (miner.getMaxDiffs(i) > 0) {
                sum = BigDecimalUtils.add(sum, miner.getMaxDiffs(i));
            }
            ++count;
        }
        return diffToPay(sum, count);
    }

    public static double calculateUnpaidShares(MinerChannel channel) {
        double sum = channel.getPrevDiff();
        int count = channel.getPrevDiffCounts();
        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
            if (channel.getMaxDiffs(i) > 0) {
                sum = BigDecimalUtils.add(sum, channel.getMaxDiffs(i));
                ++count;
            }
        }
        log.debug("打印信息的unpaid ，sum= [{}],count = [{}]",sum,count);
        return diffToPay(sum, count);
    }

    public static String minerStats(Miner miner) {
        StringBuilder res = new StringBuilder();
        String address = BasicUtils.hash2Address(miner.getAddressHash());
        double unpaid = calculateUnpaidShares(miner);
        String minerRegTime = FormatDateUtils.format(miner.getRegTime());
        res.append("miner: ")
                .append(address)
                .append("   regTime: ")
                .append(minerRegTime)
                .append("   unpaid: ")
                .append(String.format("%.6f", unpaid))
                .append("   HashRate: ")
                .append(String.format("%.6f", BasicUtils.xdag_log_difficulty2hashrate(miner.getMeanLogDiff())))
                .append("\n");

        Map<InetSocketAddress, MinerChannel> channels = miner.getChannels();
        // todo 给每一个channels 加上一个单独的语句
        for (MinerChannel channel : channels.values()) {
            StringBuilder channelStr = new StringBuilder();
            String connTime = FormatDateUtils.format(channel.getConnectTime());
            String ip = channel.getInetAddress().toString();
            double unpaidChannel = calculateUnpaidShares(channel);

            double mean = channel.getMeanLogDiff();
            log.debug("打印信息的channel的 unpaid = [{}],  meanlog = [{}]， ", unpaidChannel, mean);
            double rate = BasicUtils.xdag_log_difficulty2hashrate(mean);

            channelStr
                    .append("ip: ")
                    .append(ip)
                    .append("   ")
                    .append("connTime: ")
                    .append(connTime)
                    .append("   ")
                    .append("unpaid: ")
                    .append(String.format("%.6f", unpaidChannel))
                    .append("     ")
                    .append("HashRate:  ")
                    .append(String.format("%.6f", rate))
                    .append("   ")
                    .append(channel.isMill() ? "RandomXMiner" : "xdagClient")
                    .append("\n");
            res.append(channelStr);
        }
        return res.toString();
    }

    /**
     * 根据一个矿工计算的hash 为他计算一个难度
     */
    public static void calculateNopaidShares(
            MinerChannel channel, byte[] hash, long currentTaskTime) {
        Miner miner = channel.getMiner();
        double diff = 0.0;
        // 不可能出现大于的情况 防止对老的任务重复计算
        long minerTaskTime = miner.getTaskTime();
        long channelTaskTime = channel.getTaskTime();
        log.debug("channer = [{}],calculateNopaidShares,currentTaskTime = [{}],miner tasktime = [{}],channelTaskTime = [{}]",
                Hex.toHexString(channel.getAccountAddressHash()),currentTaskTime, minerTaskTime,channelTaskTime);
        if (channelTaskTime <= currentTaskTime) {
            // 获取到位置 myron
            int i = (int) (((currentTaskTime >> 16) + 1) & AWARD_EPOCH);
            // int i = (int) (((currentTaskTime>> 16) +1 ) & 7);
            diff = BytesUtils.hexBytesToDouble(hash, 8, false);
            diff *= Math.pow(2, -64);
            diff += BytesUtils.hexBytesToDouble(hash, 0, false);

            if (diff < 1) {
                diff = 1;
            }
            diff = 46 - Math.log(diff);
            log.debug("address [{}] calculateNopaidShares, 最新难度的diff为[{}]",
                    Hex.toHexString(channel.getAccountAddressHash())  , diff);
            if (channelTaskTime < currentTaskTime) {
                channel.setTaskTime(currentTaskTime);
                double maxDiff = channel.getMaxDiffs(i);
                log.debug("address [{}] calculateNopaidShares,首次channel获取到的maxdiff[{}] = [{}]",
                        Hex.toHexString(channel.getAccountAddressHash()),i,maxDiff);
                if (maxDiff > 0) {

                    channel.addPrevDiff(maxDiff);
                    channel.addPrevDiffCounts();
                }
                channel.addMaxDiffs(i, diff);
            } else if (diff > channel.getMaxDiffs(i)) {
                log.debug("address [{}] calculateNopaidShares,channel获取到的maxdiff[{}] = [{}]",
                        Hex.toHexString(channel.getAccountAddressHash()),i,diff);
                channel.addMaxDiffs(i, diff);
            }
            // 给对应的矿工设置
            if (minerTaskTime < currentTaskTime) {
                miner.setTaskTime(currentTaskTime);
                double maxDiff = miner.getMaxDiffs(i);
                log.debug("calculateNopaidShares, channel获取到的maxdiff[{}] = [{}]",i,maxDiff);
                if (maxDiff > 0) {
                    miner.addPrevDiff(maxDiff);
                    miner.addPrevDiffCounts();
                }
                miner.setMaxDiffs(i, diff);
            } else if (diff > miner.getMaxDiffs(i)) {
                miner.setMaxDiffs(i, diff);
            }
        }
    }

    public static double welfordOnePass(double mean, double sample, int nsamples) {
        if (nsamples > 0) {
            double temp = BigDecimalUtils.div(BigDecimalUtils.sub(sample, mean), nsamples);
            mean = BigDecimalUtils.add(mean, temp);
        }
        return mean;
    }

    public static double movingAverageDouble(double mean, double sample, int nsamples) {
        if (nsamples < 2) {
            mean = sample;
        }
        if (nsamples >= NSAMPLES_MAX) {
            mean = welfordOnePass(mean, sample, NSAMPLES_MAX);
        } else {
            mean = welfordOnePass(mean, sample, nsamples);
        }
        return mean;
    }

    /**
     * 更新矿工的meanlog
     *
     * @param channel
     *            对应的矿工
     * @param task
     *            当前的任务
     * @param hash
     *            接收到矿工发送的share后计算的hash
     */
    public static void updateMeanLogDiff(MinerChannel channel, Task task, byte[] hash) {
        log.debug("接收到一个Share消息，更新对应channel 的消息");
        log.debug("对应计算的哈希为[{}]", Hex.toHexString(hash));
        long taskTime = task.getTaskTime();
        long channelTime = channel.getTaskTime();
        if (channelTime < taskTime) {
            if (channelTime != 0) {
                double meanLogDiff = movingAverageDouble(
                        channel.getMeanLogDiff(),
                        BasicUtils.xdag_diff2log(BasicUtils.getDiffByHash(channel.getMinHash())),
                        channel.getBoundedTaskCounter());
                log.debug("channel updateMeanLogDiff [{}]", meanLogDiff);
                channel.setMeanLogDiff(meanLogDiff);
                log.debug("channel updateMeanLogDiff [{}]", channel.getMeanLogDiff());
                if (channel.getBoundedTaskCounter() < NSAMPLES_MAX) {
                    channel.addBoundedTaskCounter();
                }
            }
            channel.setMinHash(hash);
        } else if (compareTo(hash, 0, hash.length, channel.getMinHash(),0,hash.length) < 0) {
            channel.setMinHash(hash);
        }

        Miner miner = channel.getMiner();
        long minerTaskTime = miner.getTaskTime();
        if (minerTaskTime < taskTime) {
            if (minerTaskTime != 0) {
                double meanLogDiff = movingAverageDouble(
                        miner.getMeanLogDiff(),
                        BasicUtils.xdag_diff2log(BasicUtils.getDiffByHash(miner.getLastMinHash())),
                        miner.getBoundedTaskCounter());

                log.debug("miner[{}] updateMeanLogDiff [{}]",
                        Hex.toHexString(miner.getAddressHash()), meanLogDiff);
                miner.setMeanLogDiff(meanLogDiff);
                //log.debug("miner updateMeanLogDiff [{}]", miner.getMeanLogDiff());

                if (miner.boundedTaskCounter < NSAMPLES_MAX){
                    miner.addBoundedTaskCounter();
                }
            }
            miner.setLastMinHash(hash);
        } else if (compareTo(hash, 0, hash.length,miner.getLastMinHash(),0, hash.length) < 0) {
            miner.setLastMinHash(hash);
        }
    }


}
