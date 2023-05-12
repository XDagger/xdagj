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
package io.xdag.db;

import io.xdag.core.BlockInfo;
import io.xdag.core.PreBlockInfo;
import io.xdag.core.XAmount;
import io.xdag.db.rocksdb.RocksdbKVSource;
import java.util.List;
import org.hyperledger.besu.crypto.KeyPair;
import org.rocksdb.RocksIterator;

public interface SnapshotStore {

    void init();

    void reset();

    void makeSnapshot(RocksdbKVSource blockSource,RocksdbKVSource indexSource,boolean b);

    void saveSnapshotToIndex(BlockStore blockStore, List<KeyPair> keys,long snapshotTime);

    void saveAddress(BlockStore blockStore,AddressStore addressStore,List<KeyPair> keys,long snapshotTime);

    void save(RocksIterator iter, BlockInfo blockInfo);

    void setBlockInfo(BlockInfo blockInfo, PreBlockInfo preBlockInfo);

    XAmount getOurBalance();

    long getNextTime();

    long getHeight();

    XAmount getAllBalance();

}
