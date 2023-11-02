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
package io.xdag.config.spec;

import java.util.Map;

import io.xdag.core.Fork;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;

public interface DagSpec {

    XAmount getMinTransactionFee();

    XAmount getMaxMainBlockTransactionFee();

    long getMaxTxPoolTimeDrift();

    long getMaxTransactionDataSize(TransactionType type);

    /**
     * Returns the main block reward for a specific block.
     *
     * @param number
     *            block number
     * @return the main block reward
     */
    XAmount getMainBlockReward(long number);

    /**
     * Returns the xdag total supply for a block number.
     *
     * @param number
     *            block number
     * @return xdag total supply
     */
    XAmount getMainBlockSupply(long number);

    /**
     * Get checkpoints.
     *
     * @return a map of blockchain checkpoints [block number] => [block hash]
     */
    Map<Long, byte[]> checkpoints();

    /**
     * Returns the fork signaling period, also the fork activation check period.
     *
     * @param fork
     *            the fork
     * @return the start and end block numbers, inclusive
     */
    long[] getForkSignalingPeriod(Fork fork);

    boolean forkApolloEnabled();

    long getPowEpochTimeout();
}
