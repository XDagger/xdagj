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

import io.xdag.crypto.ECKey;
import io.xdag.utils.ByteArrayWrapper;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface Blockchain {

    ImportResult tryToConnect(Block block);

    Block createNewBlock(Map<Address, ECKey> pairs, List<Address> to, boolean mining);

    Block getBlockByHash(byte[] hash, boolean isRaw);

    BigInteger getTopDiff();

    BigInteger getPretopDiff();

    boolean hasBlock(byte[] hash);

    byte[] getTop_main_chain();

    long getMainBlockSize();

    long getBlockSize();

    long getOrphanSize();

    long getExtraSize();

    List<Block> getBlockByTime(long starttime, long endtime);

    void checkNewMain();

    List<Block> listMainBlocks(int count);

    List<Block> listMinedBlocks(int count);

    List<byte[]> getAllAccount();

    Map<ByteArrayWrapper, Integer> getMemAccount();

    ReentrantReadWriteLock getStateLock();

    Block getExtraBlock(byte[] hash);

    long getSupply(long nmain);
}
