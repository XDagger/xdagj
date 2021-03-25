///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2020-2030 The XdagJ Developers
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in
// * all copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// * THE SOFTWARE.
// */
//package io.xdag.db.store;
//
//import io.xdag.config.Config;
//import io.xdag.core.Block;
//import io.xdag.core.XdagStats;
//import io.xdag.crypto.ECKey;
//import io.xdag.crypto.Sha256Hash;
//import io.xdag.db.DatabaseFactory;
//import io.xdag.db.DatabaseName;
//import io.xdag.db.KVSource;
//import io.xdag.db.rocksdb.RocksdbFactory;
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.junit.rules.TemporaryFolder;
//
//import java.math.BigInteger;
//
//import static io.xdag.BlockBuilder.*;
//import static org.junit.Assert.*;
//
//public class BlockStoreTest {
//    @Rule
//    public TemporaryFolder root = new TemporaryFolder();
//
//    Config config = new Config();
//    DatabaseFactory factory;
//    KVSource<byte[], byte[]> indexSource;
//    KVSource<byte[], byte[]> timeSource;
//    KVSource<byte[], byte[]> blockSource;
//
//    @Before
//    public void setUp() throws Exception {
//        config.setStoreDir(root.newFolder().getAbsolutePath());
//        config.setStoreBackupDir(root.newFolder().getAbsolutePath());
//        factory = new RocksdbFactory(config);
//        indexSource = factory.getDB(DatabaseName.INDEX);
//        timeSource = factory.getDB(DatabaseName.TIME);
//        blockSource = factory.getDB(DatabaseName.BLOCK);
//    }
//
//    @Test
//    public void testNewBlockStore() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        assertNotNull(bs);
//    }
//
//    @Test
//    public void testInit() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//    }
//
//    @Test
//    public void testReset() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.reset();
//    }
//
//    @Test
//    public void testSaveXdagStatus() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//        XdagStats stats = new XdagStats();
//        byte[] hashlow = Sha256Hash.hashTwice("".getBytes());
//        stats.setTop(hashlow);
//        stats.setTopDiff(BigInteger.ONE);
//        stats.setNmain(1);
//        bs.saveXdagStatus(stats);
//        XdagStats storedStats = bs.getXdagStatus();
//        assertArrayEquals(stats.getTop(), storedStats.getTop());
//        assertEquals(stats.getTopDiff(), storedStats.getTopDiff());
//        assertEquals(stats.getNmain(), storedStats.getNmain());
//    }
//
//    @Test
//    public void testSaveBlock() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//        long time = System.currentTimeMillis();
//        ECKey key = new ECKey();
//        Block block = generateAddressBlock(key, time);
//        bs.saveBlock(block);
//        Block storedBlock = bs.getBlockByHash(block.getHashLow(), true);
//        assertArrayEquals(block.toBytes(), storedBlock.toBytes());
//    }
//
//    @Test
//    public void testSaveOurBlock() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//        long time = System.currentTimeMillis();
//        ECKey key = new ECKey();
//        Block block = generateAddressBlock(key, time);
//        bs.saveOurBlock(1, block.getHashLow());
//        assertArrayEquals(block.getHashLow(), bs.getOurBlock(1));
//    }
//
//    @Test
//    public void testRemoveOurBlock() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//        long time = System.currentTimeMillis();
//        ECKey key = new ECKey();
//        Block block = generateAddressBlock(key, time);
//        bs.saveBlock(block);
//        bs.saveOurBlock(1, block.getHashLow());
//        assertFalse(bs.getOurBlock(1) == null);
//        bs.removeOurBlock(block.getHashLow());
//        assertTrue(bs.getOurBlock(1) == null);
//    }
//
//    @Test
//    public void testSaveBlockSums() {
//        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
//        bs.init();
//        long time = 1602951025307L;
//        ECKey key = new ECKey();
//        Block block = generateAddressBlock(key, time);
//        bs.saveBlock(block);
//        byte[] sums = new byte[256];
//        bs.loadSum(time, time + 64 * 1024, sums);
//    }
//}
