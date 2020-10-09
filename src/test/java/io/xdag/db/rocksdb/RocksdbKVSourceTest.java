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

import io.xdag.config.Config;
import io.xdag.crypto.Sha256Hash;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.KVSource;
import io.xdag.db.store.BlockStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.spongycastle.util.encoders.Hex;

import java.util.List;
import static org.junit.Assert.*;

public class RocksdbKVSourceTest {
    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new Config();

    @Before
    public void setUp() throws Exception {
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());
    }

    @Test
    public void testRocksdbFactory() {
        DatabaseFactory factory = new RocksdbFactory(config);
        KVSource<byte[], byte[]> blocksource = factory.getDB(DatabaseName.BLOCK); // <block-hash,block-info>
        KVSource<byte[], byte[]> accountsource = factory.getDB(DatabaseName.ACCOUNT); // <hash,info>
        KVSource<byte[], byte[]> indexsource = factory.getDB(DatabaseName.INDEX); // <hash,info>
        KVSource<byte[], byte[]> orphansource = factory.getDB(DatabaseName.ORPHANIND); // <hash,info>

        blocksource.reset();
        accountsource.reset();
        indexsource.reset();
        orphansource.reset();

        byte[] key = Hex.decode("FFFF");
        byte[] value = Hex.decode("1234");

        blocksource.put(key, value);
        accountsource.put(key, value);
        indexsource.put(key, value);
        orphansource.put(key, value);

        assertEquals("1234", Hex.toHexString(blocksource.get(key)));
        assertEquals("1234", Hex.toHexString(accountsource.get(key)));
        assertEquals("1234", Hex.toHexString(indexsource.get(key)));
        assertEquals("1234", Hex.toHexString(orphansource.get(key)));
    }

    @Test
    public void testPrefixKeyLookup() {
        DatabaseFactory factory = new RocksdbFactory(config);
        KVSource<byte[], byte[]> indexSource = factory.getDB(DatabaseName.TIME);
        indexSource.reset();

        byte[] hashlow1 = Sha256Hash.hashTwice("1".getBytes());
        byte[] hashlow2 = Sha256Hash.hashTwice("2".getBytes());

        long time1 = 1602226304712L;
        byte[] value1 = Hex.decode("1234");
        byte[] value2 = Hex.decode("2345");

        byte[] key1 = BlockStore.getTimeKey(time1, hashlow1);
        byte[] key2 = BlockStore.getTimeKey(time1, hashlow2);

        indexSource.put(key1, value1);
        indexSource.put(key2, value2);

        long searchTime = 1602226304712L;
        byte[] key = BlockStore.getTimeKey(searchTime, null);
        List<byte[]> keys = indexSource.prefixKeyLookup(key);
        assertEquals(2, keys.size());
        List<byte[]> values = indexSource.prefixValueLookup(key);
        assertEquals(2, values.size());
    }
}
