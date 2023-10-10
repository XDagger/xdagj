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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.xdag.utils.BytesUtils;
import io.xdag.utils.ClosableIterator;

public class LeveldbDatabaseTest {

    private final byte[] key = BytesUtils.of("key");
    private final byte[] value = BytesUtils.of("value");

    private LeveldbDatabase db;

    @Before
    public void setup() throws IOException {
        Path temp = Files.createTempDirectory("db");
        db = new LeveldbDatabase(temp.toFile());
    }

    @After
    public void teardown() {
        db.destroy();
    }

    @Test
    public void testRecover() {
        db.recover(db.createOptions());
    }

    @Test
    public void testGetAndPut() {
        assertNull(db.get(key));
        db.put(key, value);
        assertArrayEquals(value, db.get(key));
    }

    @Test
    public void testUpdateBatch() {
        db.put(BytesUtils.of("a"), BytesUtils.of("1"));

        List<Pair<byte[], byte[]>> update = new ArrayList<>();
        update.add(Pair.of(BytesUtils.of("a"), null));
        update.add(Pair.of(BytesUtils.of("b"), BytesUtils.of("2")));
        update.add(Pair.of(BytesUtils.of("c"), BytesUtils.of("3")));
        db.updateBatch(update);

        assertNull(db.get(BytesUtils.of("a")));
        assertArrayEquals(db.get(BytesUtils.of("b")), BytesUtils.of("2"));
        assertArrayEquals(db.get(BytesUtils.of("c")), BytesUtils.of("3"));
    }

    @Test
    public void testIterator() {
        db.put(BytesUtils.of("a"), BytesUtils.of("1"));
        db.put(BytesUtils.of("b"), BytesUtils.of("2"));
        db.put(BytesUtils.of("c"), BytesUtils.of("3"));

        ClosableIterator<Entry<byte[], byte[]>> itr = db.iterator(BytesUtils.of("a1"));
        assertTrue(itr.hasNext());
        assertArrayEquals(BytesUtils.of("b"), itr.next().getKey());
        assertTrue(itr.hasNext());
        assertArrayEquals(BytesUtils.of("c"), itr.next().getKey());
        itr.close();
    }

    @Test
    public void testClose() {
        db.close();
    }

    @Test
    public void testDestroy() {
        db.destroy();

        assertFalse(db.getDataDir().toFile().exists());
    }

    @Test
    public void testSnapshort() throws IOException {
        db.put(BytesUtils.of("a"), BytesUtils.of("1"));
        db.put(BytesUtils.of("b"), BytesUtils.of("2"));
        Snapshot s = db.getSnapshot();
        ReadOptions ro = new ReadOptions();
        ro.snapshot(s);

        db.put(BytesUtils.of("a"), BytesUtils.of("11"));
        db.put(BytesUtils.of("b"), BytesUtils.of("22"));

        byte[] val1 = db.get(BytesUtils.of("a"));
        byte[] val2 = db.get(BytesUtils.of("a"), ro);

        System.out.println(new String(val1));
        System.out.println(new String(val2));

        val1 = db.get(BytesUtils.of("b"));
        val2 = db.get(BytesUtils.of("b"), ro);

        System.out.println(new String(val1));
        System.out.println(new String(val2));

        s.close();
    }
}
