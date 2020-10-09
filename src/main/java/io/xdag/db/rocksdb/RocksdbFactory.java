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
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.KVSource;

import java.util.EnumMap;
import org.apache.commons.lang3.StringUtils;

public class RocksdbFactory implements DatabaseFactory {

    private final EnumMap<DatabaseName, KVSource<byte[], byte[]>> databases = new EnumMap<>(DatabaseName.class);

    protected Config config;

    public RocksdbFactory(Config config) {
        this.config = config;
    }

    @Override
    public KVSource<byte[], byte[]> getDB(DatabaseName name) {
        return databases.computeIfAbsent(
                name, k -> {
                    RocksdbKVSource dataSource;
                    // time data source must set fixed prefix length
                    if(StringUtils.equals(DatabaseName.TIME.toString(), name.toString())) {
                        dataSource = new RocksdbKVSource(name.toString(), 9);
                    } else {
                        dataSource = new RocksdbKVSource(name.toString());
                    }
                    dataSource.setConfig(config);
                    return dataSource;
                });
    }

    @Override
    public void close() {
        for (KVSource<byte[], byte[]> db : databases.values()) {
            db.close();
        }
        databases.clear();
    }

//    @Override
//    public SimpleFileStore getSumsDB() {
//        return new SimpleFileStore(config.getStoreDir());
//    }
}
