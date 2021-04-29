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

import io.xdag.crypto.ECKeyPair;
import io.xdag.listener.Listener;
import io.xdag.utils.ByteArrayWrapper;
import java.util.List;
import java.util.Map;

public interface Blockchain {

    ImportResult tryToConnect(Block block);

    Block createNewBlock(Map<Address, ECKeyPair> pairs, List<Address> to, boolean mining, String remark);

    Block getBlockByHash(byte[] hash, boolean isRaw);

    Block getBlockByHeight(long height);

    void checkNewMain();

    long loadBlockchain(String srcFilePath);

    List<Block> listMainBlocks(int count);

    List<Block> listMinedBlocks(int count);

    Map<ByteArrayWrapper, Integer> getMemOurBlocks();

    XdagStats getXdagStats();
    XdagTopStatus getXdagTopStatus();

    long getSupply(long nmain);

    List<Block> getBlocksByTime(long starttime, long endtime);

    // TODO ： 补充单元测试
    // 启动检查主块链线程
    void startCheckMain();

    // 关闭检查主块链线程
    void stopCheckMain();

    // 注册监听器
    void registerListener(Listener listener);
}
