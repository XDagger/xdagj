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

import io.xdag.listener.Listener;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;

import java.util.List;
import java.util.Map;

public interface Blockchain {

    // for snapshot pre-seed
    byte[] getPreSeed();

    ImportResult tryToConnect(Block block);

    Block createNewBlock(Map<Address, KeyPair> pairs, List<Address> to, boolean mining, String remark, XAmount fee);

    Block getBlockByHash(Bytes32 hash, boolean isRaw);

    Block getBlockByHeight(long height);

    void checkNewMain();

    long getLatestMainBlockNumber();

    List<Block> listMainBlocks(int count);

    List<Block> listMinedBlocks(int count);
    Map<Bytes, Integer> getMemOurBlocks();

    XdagStats getXdagStats();

    XdagTopStatus getXdagTopStatus();

    XAmount getReward(long nmain);

    XAmount getSupply(long nmain);

    List<Block> getBlocksByTime(long starttime, long endtime);

    // 启动检查主块链线程
    void startCheckMain(long period);

    // 关闭检查主块链线程
    void stopCheckMain();

    // 注册监听器
    void registerListener(Listener listener);

    List<TxHistory> getBlockTxHistoryByAddress(Bytes32 addressHashlow, int page, Object... parameters);

    XdagExtStats getXdagExtStats();
}
