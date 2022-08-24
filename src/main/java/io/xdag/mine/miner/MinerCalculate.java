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

import static io.xdag.utils.BytesUtils.compareTo;
import static java.lang.Math.E;

import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BigDecimalUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import java.net.InetSocketAddress;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;

@Slf4j
public class MinerCalculate {

    private static final int NSAMPLES_MAX = 255;

    /**
     * 每一轮的确认数是16
     */
    private static final int CONFIRMATIONS_COUNT = Constants.CONFIRMATIONS_COUNT;

    /**
     * 实现一个由难度转换为pay 的函数
     */
    public static double diffToPay(double sum, int diffCount) {
        double result = 0.0;
        if (diffCount > 0) {
            result = Math.pow(E, ((sum / diffCount) - 20)) * diffCount;
        }
        return result;
    }

    /**
     * 用于打印矿工但钱未支付的难度总和
     */
    public static double calculateUnpaidShares(Miner miner) {
        double sum = miner.getPrevDiff();
        int count = miner.getPrevDiffCounts();
        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
            if (miner.getMaxDiffs(i) > 0) {
                sum = BigDecimalUtils.add(sum, miner.getMaxDiffs(i));
                ++count;
            }
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
        log.debug("print unpaid for miner: {} , sum= [{}] , count = [{}]",
                BasicUtils.hash2Address(channel.getMiner().getAddressHash()),sum, count);
        return diffToPay(sum, count);
    }

    public static String minerStats(Miner miner) {
        StringBuilder res = new StringBuilder();
        String address = BasicUtils.hash2Address(miner.getAddressHash());
        double unpaid = calculateUnpaidShares(miner);
        String minerRegTime = XdagTime.format(miner.getRegTime());
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
            String connTime = XdagTime.format(channel.getConnectTime());
            String ip = channel.getInetAddress().toString();
            double unpaidChannel = calculateUnpaidShares(channel);

            double mean = channel.getMeanLogDiff();
            log.debug("print unpaid = [{}],  meanlog = [{}] for miner: {} ",
                    unpaidChannel, mean ,BasicUtils.hash2Address(channel.getMiner().getAddressHash()));
            double rate = BasicUtils.xdag_log_difficulty2hashrate(mean);

            channelStr
                    .append("WorkerName: ")
                    .append(channel.getWorkerName())
                    .append("   ")
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
            Config config, MinerChannel channel, Bytes32 hash, long currentTaskTime) {
        Miner miner = channel.getMiner();
        double diff;
        // 不可能出现大于的情况 防止对老的任务重复计算
        long minerTaskTime = miner.getTaskTime();
        long channelTaskTime = channel.getTaskTime();
        log.debug(
                "CalculateNoPaidShares for miner: {},currentTaskTime = [{}],miner tasktime = [{}],channelTaskTime = [{}]",
                channel.getAddressHash(), currentTaskTime, minerTaskTime, channelTaskTime);
        if (channelTaskTime <= currentTaskTime) {
            // 获取到位置 myron
            int i = (int) (((currentTaskTime >> 16) + 1) & config.getPoolSpec().getAwardEpoch());
            // int i = (int) (((currentTaskTime>> 16) +1 ) & 7);
            diff = BytesUtils.hexBytesToDouble(hash.toArray(), 8, false);
            diff *= Math.pow(2, -64);
            diff += BytesUtils.hexBytesToDouble(hash.toArray(), 0, false);

            if (diff < 1) {
                diff = 1;
            }
            diff = 46 - Math.log(diff);
            log.debug("CalculateNoPaidShares for miner: {}, latest diff is [{}]",
                    channel.getAddressHash(), diff);
            if (channelTaskTime < currentTaskTime) {
                channel.setTaskTime(currentTaskTime);
                double maxDiff = channel.getMaxDiffs(i);
                log.debug("CalculateNoPaidShares for miner: {},channel get maxDiff at first is [{}] = [{}]",
                        channel.getAccountAddressHash().toHexString(), i, maxDiff);
                if (maxDiff > 0) {

                    channel.addPrevDiff(maxDiff);
                    channel.addPrevDiffCounts();
                }
                channel.addMaxDiffs(i, diff);
            } else if (diff > channel.getMaxDiffs(i)) {
                log.debug("CalculateNoPaidShares for miner: {},channel get the maxDiff[{}] = [{}]",
                        channel.getAddressHash(), i, diff);
                channel.addMaxDiffs(i, diff);
            }
            // 给对应的矿工设置
            if (minerTaskTime < currentTaskTime) {
                miner.setTaskTime(currentTaskTime);
                double maxDiff = miner.getMaxDiffs(i);
                log.debug("CalculateNoPaidShares for miner: {}, channel get the maxdiff[{}] = [{}]",
                        channel.getAddressHash(), i, maxDiff);
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
     * @param channel 对应的矿工
     * @param task 当前的任务
     * @param hash 接收到矿工发送的share后计算的hash
     */
    public static void updateMeanLogDiff(MinerChannel channel, Task task, Bytes32 hash) {
        log.debug("Receive a Share message from miner : {} and update the message of the corresponding channel",
                channel.getAddressHash());
        log.debug("The hash calculated by miner:{} is [{}]",
                channel.getAddressHash(),hash.toHexString());
        long taskTime = task.getTaskTime();
        long channelTime = channel.getTaskTime();
        if (channelTime < taskTime) {
            if (channelTime != 0) {
                double meanLogDiff = movingAverageDouble(
                        channel.getMeanLogDiff(),
                        BasicUtils.xdag_diff2log(BasicUtils.getDiffByHash(channel.getMinHash())),
                        channel.getBoundedTaskCounter());
                channel.setMeanLogDiff(meanLogDiff);
                log.debug("channel updateMeanLogDiff [{}] for miner: {}",
                        channel.getMeanLogDiff(),channel.getAddressHash());
                if (channel.getBoundedTaskCounter() < NSAMPLES_MAX) {
                    channel.addBoundedTaskCounter();
                }
            }
            channel.setMinHash(hash);
        } else if (compareTo(hash.toArray(), 0, hash.size(), channel.getMinHash().toArray(), 0, hash.size()) < 0) {
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

                log.debug("miner: {} updateMeanLogDiff [{}]",
                        BasicUtils.hash2Address(miner.getAddressHash()), meanLogDiff);
                miner.setMeanLogDiff(meanLogDiff);
                //log.debug("miner updateMeanLogDiff [{}]", miner.getMeanLogDiff());

                if (miner.boundedTaskCounter < NSAMPLES_MAX) {
                    miner.addBoundedTaskCounter();
                }
            }
            miner.setLastMinHash(hash);
        } else if (compareTo(hash.toArray(), 0, hash.size(), miner.getLastMinHash().toArray(), 0, hash.size()) < 0) {
            miner.setLastMinHash(hash);
        }
    }


}
