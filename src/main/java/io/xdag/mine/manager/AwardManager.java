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

import io.xdag.consensus.Task;
import io.xdag.mine.miner.Miner;
import org.apache.tuweni.bytes.Bytes32;

public interface AwardManager {

    /** 获取poolminer */
    Miner getPoolMiner();

    /**
     * 根据地址块的hash 设置矿池自身对象
     *
     * @param hash
     *            地址块hash
     */
    void setPoolMiner(Bytes32 hash);

    /** 接受到一个新的任务 */
    void onNewTask(Task task);

    /** 挖矿收益支付及生成交易块 */
    //void payAndaddNewAwardBlock(byte[] share, byte[] hash, long generateTime);

    void start();

    void stop();

    void addAwardBlock(Bytes32 share, Bytes32 hash, long generateTime);
}
