package io.xdag.core;

import io.xdag.config.Config;
import org.junit.Test;

import static io.xdag.config.Constants.*;
import static io.xdag.utils.BasicUtils.xdag2amount;

public class MintTest {
    @Test
    public void testMint() {
        // 每四年减半 大致就是增加了 2097152个块
        int num = 1017323 + 2097152;
        long reward = getCurrentReward();
        long reward1 = getReward(0, num);

        System.out.println(reward);
        System.out.println(reward1);

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

    private long getStartAmount(long time, long num) {
        long forkHeight = Config.MainNet ? MAIN_APOLLO_HEIGHT : MAIN_APOLLO_TESTNET_HEIGHT;
        long startAmount = 0;
        if (num >= forkHeight) {
            startAmount = MAIN_APOLLO_AMOUNT;
        } else {
            startAmount = MAIN_START_AMOUNT;
        }

        return startAmount;
    }
}
