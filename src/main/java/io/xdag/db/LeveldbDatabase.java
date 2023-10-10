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

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;

import io.xdag.utils.ClosableIterator;
import io.xdag.utils.FileUtils;
import io.xdag.utils.SystemUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LeveldbDatabase implements Database {

    private final File file;
    private DB db;
    private boolean isOpened;

    /**
     * Creates an LevelDB instance and opens it.
     */
    public LeveldbDatabase(File file) {
        this.file = file;

        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("Failed to create directory: {}", dir);
        }

        open(createOptions());
    }

    /**
     * Creates the default options.
     */
    protected Options createOptions() {
        Options options = new Options();
        options.createIfMissing(true)
                .compressionType(CompressionType.NONE)
                .blockSize(4 * 1024 * 1024)
                .writeBufferSize(8 * 1024 * 1024)
                .cacheSize(64L * 1024L * 1024L)
                .paranoidChecks(true)
                .verifyChecksums(true)
                .maxOpenFiles(128);

        return options;
    }

    /**
     * Open the database.
     */
    protected void open(Options options) {
        try {
            db = JniDBFactory.factory.open(file, options);
            isOpened = true;
        } catch (IOException e) {
            if (e.getMessage().contains("Corruption")) {
                // recover
                recover(options);

                // reopen
                try {
                    db = JniDBFactory.factory.open(file, options);
                    isOpened = true;
                } catch (IOException ex) {
                    log.error("Failed to open database", e);
                    SystemUtils.exitAsync(SystemUtils.Code.FAILED_TO_OPEN_DB);
                }
            } else {
                log.error("Failed to open database", e);
                SystemUtils.exitAsync(SystemUtils.Code.FAILED_TO_OPEN_DB);
            }
        }
    }

    /**
     * Tries to recover the database in case of corruption.
     */
    protected void recover(Options options) {
        try {
            log.info("Trying to repair the database: {}", file);
            factory.repair(file, options);
            log.info("Repair done!");
        } catch (IOException ex) {
            log.error("Failed to repair the database", ex);
            SystemUtils.exitAsync(SystemUtils.Code.FAILED_TO_REPAIR_DB);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        return db.get(key);
    }

    @Override
    public byte[] get(byte[] key, ReadOptions readOptions) {
        return db.get(key, readOptions);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        db.put(key, value);
    }

    public void put(byte[] key, byte[] value, WriteOptions writeOptions) {
        writeOptions.snapshot(true);
        db.put(key, value, writeOptions);
    }

    @Override
    public void delete(byte[] key) {
        db.delete(key);
    }

    public Snapshot getSnapshot() {
        return db.getSnapshot();
    }

    @Override
    public void updateBatch(List<Pair<byte[], byte[]>> pairs) {
        try (WriteBatch batch = db.createWriteBatch()) {
            for (Pair<byte[], byte[]> p : pairs) {
                if (p.getValue() == null) {
                    batch.delete(p.getLeft());
                } else {
                    batch.put(p.getLeft(), p.getRight());
                }
            }
            db.write(batch);
        } catch (IOException e) {
            log.error("Failed to update batch", e);
            SystemUtils.exitAsync(SystemUtils.Code.FAILED_TO_WRITE_BATCH_TO_DB);
        }
    }

    @Override
    public void close() {
        try {
            if (isOpened) {
                db.close();
                isOpened = false;
            }
        } catch (IOException e) {
            log.error("Failed to close database: {}", file, e);
        }
    }

    @Override
    public void destroy() {
        close();
        FileUtils.recursiveDelete(file);
    }

    @Override
    public Path getDataDir() {
        return file.toPath();
    }

    @Override
    public ClosableIterator<Entry<byte[], byte[]>> iterator() {
        return iterator(null);
    }

    @Override
    public ClosableIterator<Entry<byte[], byte[]>> iterator(byte[] prefix) {

        return new ClosableIterator<Entry<byte[], byte[]>>() {
            final DBIterator itr = db.iterator();

            private ClosableIterator<Entry<byte[], byte[]>> initialize() {
                if (prefix != null) {
                    itr.seek(prefix);
                } else {
                    itr.seekToFirst();
                }
                return this;
            }

            @Override
            public boolean hasNext() {
                return itr.hasNext();
            }

            @Override
            public Entry<byte[], byte[]> next() {
                return itr.next();
            }

            @Override
            public void close() {
                try {
                    itr.close();
                } catch (IOException e) {
                    throw new DatabaseException(e);
                }
            }
        }.initialize();
    }

    public static class LeveldbFactory implements DatabaseFactory {

        private final EnumMap<DatabaseName, Database> databases = new EnumMap<>(DatabaseName.class);

        private final File dataDir;

        public LeveldbFactory(File dataDir) {
            this.dataDir = dataDir;
        }

        @Override
        public Database getDB(DatabaseName name) {
            return databases.computeIfAbsent(name, k -> {
                File file = new File(dataDir.getAbsolutePath(), k.toString().toLowerCase(Locale.ROOT));
                return new LeveldbDatabase(file);
            });
        }

        @Override
        public void close() {
            for (Database db : databases.values()) {
                db.close();
            }
            databases.clear();
        }

        @Override
        public Path getDataDir() {
            return dataDir.toPath();
        }
    }
}
