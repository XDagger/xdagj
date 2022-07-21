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

import static io.xdag.config.Constants.MAIN_BIG_PERIOD_LOG;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.assertEquals;

import io.xdag.config.Config;
import io.xdag.config.MainnetConfig;
import org.junit.Test;

public class MintTest {

    Config config = new MainnetConfig();

    @Test
    public void testMint() {
        // 每四年减半 大致就是增加了 2097152个块
        int num = 1017323 + 2097152;
        long reward = getCurrentReward();
        long reward1 = getReward(0, num);

        assertEquals(4398046511104L, reward);
        assertEquals(274877906944L, reward1);

    }

    /**
     * 根据当前区块数量计算奖励金额 cheato *
     */
    public long getCurrentReward() {
        return xdag2amount(1024);
    }

    public long getReward(long time, long num) {
        long start = getStartAmount(time, num);
        return start >> (num >> MAIN_BIG_PERIOD_LOG);
    }

    private long getStartAmount(long time, long num) {
        long forkHeight = config.getApolloForkHeight();
        long startAmount;
        if (num >= forkHeight) {
            startAmount = config.getApolloForkAmount();
        } else {
            startAmount = config.getMainStartAmount();
        }

        return startAmount;
    }
}
