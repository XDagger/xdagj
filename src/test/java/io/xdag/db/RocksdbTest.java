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

import io.xdag.config.Config;
import io.xdag.crypto.Sha256Hash;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.List;
import java.util.Set;

public class RocksdbTest {
    Config config = new Config();

    //
    @Before
    public void setUp() throws Exception {
        config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
        config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");
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

        System.out.println(Hex.toHexString(blocksource.get(key)));
        System.out.println(Hex.toHexString(accountsource.get(key)));
        System.out.println(Hex.toHexString(indexsource.get(key)));
        System.out.println(Hex.toHexString(orphansource.get(key)));
    }

    @Test
    public void testPrefix() {
        RocksdbKVSource dataSource = new RocksdbKVSource("BlockStore");
        dataSource.setConfig(config);
        dataSource.reset();
        byte[] posPrefix = Hex.decode("FFFFFFFFFFFFFFFF");
        System.out.println(posPrefix.length);

        byte[] hash1 = Sha256Hash.hashTwice(Hex.decode("1122"));
        byte[] hash2 = Sha256Hash.hashTwice(Hex.decode("3344"));
        byte[] value1 = Hex.decode("123456");
        byte[] value2 = Hex.decode("12345678");
        dataSource.put(BytesUtils.merge(posPrefix, hash1), value1);
        dataSource.put(BytesUtils.merge(posPrefix, hash2), value2);
        dataSource.put(hash2, value2);

        System.out.println("=====prefix pos====");
        List<byte[]> ans = dataSource.prefixValueLookup(posPrefix, 8);
        for (byte[] byt : ans) {
            System.out.println(Hex.toHexString(byt));
        }

        System.out.println("=====prefix pos====");
        List<byte[]> keys = dataSource.prefixKeyLookup(posPrefix, 8);
        for (byte[] byt : keys) {
            System.out.println(Hex.toHexString(byt));
        }

        Set<byte[]> key = dataSource.keys();
        System.out.println(key.size());
        for (byte[] byt : key) {
            System.out.println(Hex.toHexString(byt));
        }

        dataSource.delete(hash2);

        key = dataSource.keys();
        System.out.println(key.size());

        for (byte[] byt : key) {
            System.out.println(Hex.toHexString(byt));
        }
    }

    @Test
    public void testBlockStore() {
        DatabaseFactory factory = new RocksdbFactory(config);
        KVSource<byte[], byte[]> blocksource = factory.getDB(DatabaseName.BLOCK); // <block-hash,block-info>
        KVSource<byte[], byte[]> accountsource = factory.getDB(DatabaseName.ACCOUNT); // <hash,info>
        KVSource<byte[], byte[]> indexsource = factory.getDB(DatabaseName.INDEX); // <hash,info>
        KVSource<byte[], byte[]> orphansource = factory.getDB(DatabaseName.ORPHANIND); // <hash,info>
        KVSource<byte[], byte[]> timesource = factory.getDB(DatabaseName.TIME); // <hash,info>

        blocksource.reset();
        accountsource.reset();
        indexsource.reset();
        orphansource.reset();
        timesource.reset();

        long time1 = 1618935152640L; // 178f00dffff
        byte[] value = Hex.decode("1234");
        long time2 = 1618935087150L;
        byte[] value2 = Hex.decode("2345");

        long time1Prefix = time1 >> 16;
        long time2Prefix = time2 >> 16;
        long time1Key = time1 & 0xffff;
        long time2Key = time2 & 0xffff;
        byte[] key1 = BytesUtils.merge(
                BytesUtils.longToBytes(time1Prefix, false), BytesUtils.longToBytes(time1Key, false));
        byte[] key2 = BytesUtils.merge(
                BytesUtils.longToBytes(time2Prefix, false), BytesUtils.longToBytes(time2Key, false));

        timesource.put(key1, value);
        timesource.put(key2, value2);

        long timeprefix = 24702990L;
        byte[] key = BytesUtils.longToBytes(timeprefix, false);
        List<byte[]> keys = timesource.prefixKeyLookup(key, key.length);
        for (byte[] byt : keys) {
            System.out.println(Hex.toHexString(byt));
        }
        List<byte[]> ans = timesource.prefixValueLookup(key, key.length);
        for (byte[] byt : ans) {
            System.out.println(Hex.toHexString(byt));
        }
    }
}
