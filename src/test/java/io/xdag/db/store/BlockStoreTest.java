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
package io.xdag.db.store;

import io.xdag.config.Config;
import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagStats;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Keys;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.KVSource;
import io.xdag.db.rocksdb.RocksdbFactory;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.util.List;

import static io.xdag.BlockBuilder.*;
import static org.junit.Assert.*;

public class BlockStoreTest {
    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new Config();
    DatabaseFactory factory;
    KVSource<byte[], byte[]> indexSource;
    KVSource<byte[], byte[]> timeSource;
    KVSource<byte[], byte[]> blockSource;

    @Before
    public void setUp() throws Exception {
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());
        factory = new RocksdbFactory(config);
        indexSource = factory.getDB(DatabaseName.INDEX);
        timeSource = factory.getDB(DatabaseName.TIME);
        blockSource = factory.getDB(DatabaseName.BLOCK);
    }

    @Test
    public void testNewBlockStore() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        assertNotNull(bs);
    }

    @Test
    public void testInit() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
    }

    @Test
    public void testReset() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.reset();
    }

    @Test
    public void testSaveXdagStatus() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
        XdagStats stats = new XdagStats();
        byte[] hashlow = Hash.hashTwice("".getBytes());
//        stats.setTop(hashlow);
//        stats.setTopDiff(BigInteger.ONE);
        stats.setNmain(1);
        bs.saveXdagStatus(stats);
        XdagStats storedStats = bs.getXdagStatus();
//        assertArrayEquals(stats.getTop(), storedStats.getTop());
//        assertEquals(stats.getTopDiff(), storedStats.getTopDiff());
        assertEquals(stats.getNmain(), storedStats.getNmain());
    }

    @Test
    public void testSaveBlock() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
        long time = System.currentTimeMillis();
        ECKeyPair key = Keys.createEcKeyPair();
        Block block = generateAddressBlock(key, time);
        bs.saveBlock(block);
        Block storedBlock = bs.getBlockByHash(block.getHashLow(), true);

        assertArrayEquals(block.toBytes(), storedBlock.toBytes());
    }

    @Test
    public void testSaveOurBlock() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
        long time = System.currentTimeMillis();
        ECKeyPair key = Keys.createEcKeyPair();
        Block block = generateAddressBlock(key, time);
        bs.saveOurBlock(1, block.getHashLow());
        assertArrayEquals(block.getHashLow(), bs.getOurBlock(1));
    }

    @Test
    public void testRemoveOurBlock() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
        long time = System.currentTimeMillis();
        ECKeyPair key = Keys.createEcKeyPair();
        Block block = generateAddressBlock(key, time);
        bs.saveBlock(block);
        bs.saveOurBlock(1, block.getHashLow());
        assertFalse(bs.getOurBlock(1) == null);
        bs.removeOurBlock(block.getHashLow());
        assertTrue(bs.getOurBlock(1) == null);
    }

    @Test
    public void testSaveBlockSums() {
        BlockStore bs = new BlockStore(indexSource, timeSource, blockSource);
        bs.init();
        long time = 1602951025307L;
        ECKeyPair key = Keys.createEcKeyPair();
        Block block = generateAddressBlock(key, time);
        bs.saveBlock(block);
        byte[] sums = new byte[256];
        bs.loadSum(time, time + 64 * 1024, sums);
    }

    @Test
    public void getBlockByTimeTest() {
        BlockStore blockStore = new BlockStore(indexSource, timeSource, blockSource);
        blockStore.init();


        // 创建区块
        Block block = new Block(new XdagBlock(Hex.decode("00000000000000003833333333530540ffff8741810100000000000000000000032dea64ace570d7ae8668c8a4f52265c16497c9dd8cd62b0000000000000000f1f245ea01d304c3be265cad77f5589acdc45a7b3d35972f0000000000000000f23cddd22c17bf0a083e4bbe63c0e224dfc20a583238ef7a0000000000000000b4407441ad9c0372a7f053a3dbaaa4855589228cef7f05b000000000000000004206427aa89b7066b05379bec0e9264a34c55391f12137bb00000000000000009b55f3a7af41e29d8b6b4e4581387c507726437f7aacc7930000000000000000905786241884e7520a8ad2c777871b28548c78b8964107e20000000000000000a2583dc5f6001020e406edb1c6ed52c41bae2ef1dda9439200000000000000009f5c7e9633614d665fe6739fd122cdb0360b2c688d02685d00000000000000005fbc1107fe34e3faeab63e1ef3e24b6c66053103c4868a6600000000000000003a7883fa0ddb348428d72856ff0527e5aff79b2c739fb946b53ce6b29530a07dc821749a7ffa3f6b6e3417d6c0c54457c9909800b7dc5b034b7a1f979032e4cb000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008ed85467b39cc220720472c5f0b116afaccce977c71a655daae7789782c5fae9")));
        Block block1 = new Block(new XdagBlock(Hex.decode("00000000000000003833333333530540ffff8941810100000000000000000000032dea64ace570d7ae8668c8a4f52265c16497c9dd8cd62b0000000000000000f1f245ea01d304c3be265cad77f5589acdc45a7b3d35972f0000000000000000f23cddd22c17bf0a083e4bbe63c0e224dfc20a583238ef7a0000000000000000b4407441ad9c0372a7f053a3dbaaa4855589228cef7f05b000000000000000004206427aa89b7066b05379bec0e9264a34c55391f12137bb00000000000000009b55f3a7af41e29d8b6b4e4581387c507726437f7aacc7930000000000000000905786241884e7520a8ad2c777871b28548c78b8964107e20000000000000000a2583dc5f6001020e406edb1c6ed52c41bae2ef1dda9439200000000000000009f5c7e9633614d665fe6739fd122cdb0360b2c688d02685d00000000000000005fbc1107fe34e3faeab63e1ef3e24b6c66053103c4868a6600000000000000003a7883fa0ddb348428d72856ff0527e5aff79b2c739fb946b53ce6b29530a07dc821749a7ffa3f6b6e3417d6c0c54457c9909800b7dc5b034b7a1f979032e4cb000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008ed85467b39cc220720472c5f0b116afaccce977c71a655daae7789782c5fae9")));
        String bh = Hex.toHexString(block.getHashLow());
        String raw = Hex.toHexString(block.getXdagBlock().getData());

        long time = block.getTimestamp();

        System.out.println("first block time:"+time);
        System.out.println("second block time:"+block1.getTimestamp());
        System.out.println("hashlow:"+bh);

        blockStore.saveBlock(block);
        blockStore.saveBlock(block1);

        List<Block> blocks = blockStore.getBlocksByTime(time);
        assertEquals(1,blocks.size());

        assertEquals(block,blocks.get(0));

    }
}
