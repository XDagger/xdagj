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
package io.xdag.evm.client;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

import cn.hutool.core.lang.Pair;
import io.xdag.db.KVSource;
import io.xdag.db.rocksdb.RocksdbKVSource;

public class RocksdbProxy implements KVSource<Bytes, Bytes>  {

    private RocksdbKVSource rocksdbKV;

    public RocksdbProxy(RocksdbKVSource rocksdbKV) {
        this.rocksdbKV = rocksdbKV;
    }

    @Override
    public String getName() {
        return rocksdbKV.getName();
    }

    @Override
    public void setName(String name) {
        rocksdbKV.setName(name);
    }

    @Override
    public boolean isAlive() {
        return rocksdbKV.isAlive();
    }

    @Override
    public void init() {
        rocksdbKV.init();
    }

    @Override
    public void close() {
        rocksdbKV.close();
    }

    @Override
    public void reset() {
        rocksdbKV.reset();
    }

    @Override
    public void put(Bytes key, Bytes val) {
        rocksdbKV.put(key.toArray(), val.toArray());
    }

    @Override
    public Bytes get(Bytes key) {
        byte[] value = rocksdbKV.get(key.toArray());
        return value == null? null:Bytes.wrap(value);
    }

    @Override
    public void delete(Bytes key) {
        rocksdbKV.delete(key.toArray());
    }

    @Override
    public Set<byte[]> keys() throws RuntimeException {
        return rocksdbKV.keys();
    }

    @Override
    public List<Bytes> prefixKeyLookup(byte[] key) {
        return null;
    }

    @Override
    public void fetchPrefix(byte[] key, Function<Pair<Bytes, Bytes>, Boolean> func) {
    }

    @Override
    public List<Bytes> prefixValueLookup(byte[] key) {
        return null;
    }
}
