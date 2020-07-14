package io.xdag.mine.miner;

import static java.lang.Math.E;

import java.net.InetSocketAddress;
import java.util.Map;

import io.xdag.config.Constants;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BigDecimalUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.DateUtils;
import io.xdag.utils.FastByteComparisons;
import lombok.extern.slf4j.Slf4j;

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

    /** 计算一个矿工所有未支付的数据 返回的是一个平均的 diff 对过去的十六个难度的平均值 */
    public static double processOutdatedMiner(Miner miner) {
        double sum = 0.0;
        int diffcount = 0;
        double temp;
        for (int i = 0; i < CONFIRMATIONS_COUNT; i++) {
            if ((temp = miner.getMaxDiffs(i)) > 0) {
                sum = BigDecimalUtils.add(sum, temp);
                miner.setMaxDiffs(i, 0.0);
                ++diffcount;
            }
        }
        if (diffcount > 0) {
            sum /= diffcount;
        }
        return sum;
    }

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
            }
            ++count;
        }
        return diffToPay(sum, count);
    }

    public static void printfMinerStats(Miner miner) {
        StringBuilder res = new StringBuilder();
        String address = BasicUtils.hash2Address(miner.getAddressHash());
        double unpaid = calculateUnpaidShares(miner);
        String minerRegTime = DateUtils.dateToString(miner.getRegTime());
        res.append("miner: ")
                .append(address)
                .append("   regTime: ")
                .append(minerRegTime)
                .append("   unpaid: ")
                .append(String.format("%.6f", unpaid))
                .append("   HashRate: ")
                .append(
                        String.format("%.6f", BasicUtils.xdag_log_difficulty2hashrate(miner.getMeanLogDiff())))
                .append("\n");

        Map<InetSocketAddress, MinerChannel> channels = miner.getChannels();
        // todo 给每一个channels 加上一个单独的语句
        for (MinerChannel channel : channels.values()) {
            StringBuilder channelStr = new StringBuilder();
            String connTime = DateUtils.dateToString(channel.getConnectTime());
            String ip = channel.getInetAddress().toString();
            double unpaidChannel = calculateUnpaidShares(channel.getMiner());

            double mean = channel.getMeanLogDiff();
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
                    .append("HahRate:  ")
                    .append(String.format("%.6f", rate))
                    .append("\n");
            res.append(channelStr);
        }
        System.out.println(res);
    }

    /**
     * 根据一个矿工计算的hash 为他计算一个难度
     *
     * @param miner
     *            矿工结构体
     * @param hash
     *            提交的nonce 计算后的hash
     */
    public static void calculateNopaidShares(Miner miner, byte[] hash, long currentTaskTime) {
        double diff = 0.0;
        // 不可能出现大于的情况 防止对老的任务重复计算
        long minerTaskTime = miner.getTaskTime();
        if (minerTaskTime <= currentTaskTime) {
            // 获取到位置
            int i = (int) (((currentTaskTime >> 16) + 1) & 0xf);
            // int i = (int) (((currentTaskTime>> 16) +1 ) & 7);
            diff = BytesUtils.hexBytesToDouble(hash, 8, false);
            diff *= Math.pow(2, -64);
            diff += BytesUtils.hexBytesToDouble(hash, 0, false);
            if (diff < 1) {
                diff = 1;
            }
            diff = 46 - Math.log(diff);
            if (minerTaskTime < currentTaskTime) {
                miner.setTaskTime(currentTaskTime);
                if (miner.getMaxDiffs(i) > 0) {
                    miner.addPrevDiff(miner.getMaxDiffs(i));
                    miner.addPrevDiffCounts();
                }
                miner.setMaxDiffs(i, diff);
            } else if (diff > miner.getMaxDiffs(i)) {
                miner.setMaxDiffs(i, diff);
            }
        }
    }

    public static void calculateNopaidShares(
            MinerChannel channel, byte[] hash, long currentTaskTime) {
        Miner miner = channel.getMiner();
        double diff = 0.0;
        // 不可能出现大于的情况 防止对老的任务重复计算
        long minerTaskTime = miner.getTaskTime();
        long channelTaskTime = channel.getTaskTime();
        if (channelTaskTime <= currentTaskTime) {
            // 获取到位置
            int i = (int) (((currentTaskTime >> 16) + 1) & 0xf);
            // int i = (int) (((currentTaskTime>> 16) +1 ) & 7);
            diff = BytesUtils.hexBytesToDouble(hash, 8, false);
            diff *= Math.pow(2, -64);
            diff += BytesUtils.hexBytesToDouble(hash, 0, false);

            if (diff < 1) {
                diff = 1;
            }
            diff = 46 - Math.log(diff);
            if (channelTaskTime < currentTaskTime) {
                channel.setTaskTime(currentTaskTime);
                if (channel.getMaxDiffs(i) > 0) {
                    channel.addPrevDiff(miner.getMaxDiffs(i));
                    channel.addPrevDiffCounts();
                }
                channel.setMaxDiffs(i, diff);
            } else if (diff > miner.getMaxDiffs(i)) {
                channel.setMaxDiffs(i, diff);
            }
            // 给对应的矿工设置
            if (minerTaskTime < currentTaskTime) {
                miner.setTaskTime(currentTaskTime);
                if (miner.getMaxDiffs(i) > 0) {
                    miner.addPrevDiff(miner.getMaxDiffs(i));
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
        } else if (FastByteComparisons.equalBytes(hash, channel.getMinHash())) {
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

                log.debug("miner updateMeanLogDiff [{}]", meanLogDiff);
                miner.setMeanLogDiff(meanLogDiff);
                log.debug("miner updateMeanLogDiff [{}]", miner.getMeanLogDiff());
            }
            miner.setLastMinHash(hash);
        } else if (FastByteComparisons.equalBytes(hash, miner.getLastMinHash())) {
            miner.setLastMinHash(hash);
        }
    }
}
