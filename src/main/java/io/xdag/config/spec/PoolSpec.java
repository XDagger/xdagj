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

/**
 * The Mining Pool Specifications
 */
public interface PoolSpec {

    String getPoolIp();
    int getPoolPort();
    String getPoolTag();
    int getGlobalMinerLimit();
    int getGlobalMinerChannelLimit();
    int getMaxConnectPerIp();

    /** 拥有相同地址块的矿工最多允许同时在线的数量 g_connections_per_miner_limit */
    int getMaxMinerPerAccount();
    int getMaxShareCountPerChannel();

    int getConnectionTimeout();


    /** 矿池自己的收益占比 */
    double getPoolRation();

    /** 出块矿工收益占比 */
    double getRewardRation();

    /** 基金会收益占比 */
    double getFundRation();

    /** 参与奖励的占比 */
    double getDirectRation();

    /** 奖励支付的周期 */
    int getAwardEpoch();

    /** 等待超过10个epoch默认启动挖矿 */
    int getWaitEpoch();

}
