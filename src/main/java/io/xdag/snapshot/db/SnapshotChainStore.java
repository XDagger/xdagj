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
package io.xdag.snapshot.db;

import io.xdag.snapshot.core.SnapshotUnit;
import io.xdag.snapshot.core.StatsBlock;
import java.util.List;

import org.hyperledger.besu.crypto.KeyPair;

public interface SnapshotChainStore {

    void init();

    void reset();

    void saveSnapshotUnit(byte[] key, SnapshotUnit snapshotUnit);

    SnapshotUnit getSnapshotUnit(byte[] key);

    List<SnapshotUnit> getAllSnapshotUnit();

    List<StatsBlock> getSnapshotStatsBlock();

    StatsBlock getLatestStatsBlock();

    byte[] getSnapshotPreSeed();

    void saveSnaptshotStatsBlock(int i, StatsBlock statsBlock);

    void saveGlobalBalance(long balance);

    long getGlobalBalance();

    StatsBlock getStatsBlockByIndex(int i);

    boolean loadFromSnapshotData(String filepath, boolean mainLag, List<KeyPair> publicKeys);

}
