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

package io.xdag.db.rocksdb;

import static org.junit.Assert.assertEquals;

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Hash;
import io.xdag.utils.BlockUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RocksdbKVSourceTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();

    @Before
    public void setUp() throws Exception {
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());
    }

    @Test
    public void testRocksdbFactory() {
        DatabaseFactory factory = new RocksdbFactory(config);
        KVSource<byte[], byte[]> blockSource = factory.getDB(DatabaseName.BLOCK); // <block-hash,block-info>
        KVSource<byte[], byte[]> indexSource = factory.getDB(DatabaseName.INDEX); // <hash,info>
        KVSource<byte[], byte[]> orphanSource = factory.getDB(DatabaseName.ORPHANIND); // <hash,info>

        blockSource.reset();
        indexSource.reset();
        orphanSource.reset();

        byte[] key = Hex.decode("FFFF");
        byte[] value = Hex.decode("1234");

        blockSource.put(key, value);
        indexSource.put(key, value);
        orphanSource.put(key, value);

        assertEquals("1234", Hex.toHexString(blockSource.get(key)));
        assertEquals("1234", Hex.toHexString(indexSource.get(key)));
        assertEquals("1234", Hex.toHexString(orphanSource.get(key)));
    }

    @Test
    public void testPrefixKeyLookup() {
        DatabaseFactory factory = new RocksdbFactory(config);
        KVSource<byte[], byte[]> indexSource = factory.getDB(DatabaseName.TIME);
        indexSource.reset();

        Bytes32 hashlow1 = Hash.hashTwice(Bytes.wrap("1".getBytes(StandardCharsets.UTF_8)));
        Bytes32 hashlow2 = Hash.hashTwice(Bytes.wrap("2".getBytes(StandardCharsets.UTF_8)));

        long time1 = 1602226304712L;
        byte[] value1 = Hex.decode("1234");
        byte[] value2 = Hex.decode("2345");

        byte[] key1 = BlockUtils.getTimeKey(time1, hashlow1);
        byte[] key2 = BlockUtils.getTimeKey(time1, hashlow2);

        indexSource.put(key1, value1);
        indexSource.put(key2, value2);

        long searchTime = 1602226304712L;
        byte[] key = BlockUtils.getTimeKey(searchTime, null);
        List<byte[]> keys = indexSource.prefixKeyLookup(key);
        assertEquals(2, keys.size());
        List<byte[]> values = indexSource.prefixValueLookup(key);
        assertEquals(2, values.size());
    }
}
