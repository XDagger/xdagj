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

import cn.hutool.core.lang.Pair;
import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.db.KVSource;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FileUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.rocksdb.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@Slf4j
@Setter
@Getter
public class RocksdbKVSource implements KVSource<byte[], byte[]> {

    static {
        RocksDB.loadLibrary();
    }

    private Config config;
    private String name;
    private RocksDB db;
    private ReadOptions readOpts;
    private boolean alive;
    private int prefixSeekLength;

    /**
     * The native RocksDB insert/update/delete are normally thread-safe However
     * closeoperation is not thread-safe. This ReadWriteLock still permits
     * concurrent execution of insert/delete/update operations however blocks them
     * on init/close/delete operations
     */
    private final ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public RocksdbKVSource(String name) {
        this.name = name;
        log.debug("New RocksdbKVSource: " + name);
    }

    public RocksdbKVSource(String name, int prefixSeekLength) {
        this.name = name;
        this.prefixSeekLength = prefixSeekLength;
        log.debug("New RocksdbKVSource: " + name);
    }

    @Override
    public void init() {
        resetDbLock.writeLock().lock();
        try {
            log.debug("~> RocksdbKVSource.init(): " + name);

            if (isAlive()) {
                return;
            }

            if (name == null) {
                throw new NullPointerException("no name set to the db");
            }

            try (Options options = new Options()) {
                // most of these options are suggested by
                // https://github.com/facebook/rocksdb/wiki/Set-Up-Options

                // general options
                options.setCreateIfMissing(true);
                options.setCompressionType(CompressionType.LZ4_COMPRESSION);
                options.setBottommostCompressionType(CompressionType.LZ4_COMPRESSION);
                options.setLevelCompactionDynamicLevelBytes(true);
                options.setMaxOpenFiles(config.getNodeSpec().getStoreMaxOpenFiles());
                options.setIncreaseParallelism(config.getNodeSpec().getStoreMaxThreads());

                // key prefix for state node lookups
                options.useFixedLengthPrefixExtractor(prefixSeekLength);

                // table options
                final BlockBasedTableConfig tableCfg;
                options.setTableFormatConfig(tableCfg = new BlockBasedTableConfig());
                tableCfg.setBlockSize(16 * 1024);
                tableCfg.setBlockCache(new LRUCache(32 * 1024 * 1024));
                tableCfg.setCacheIndexAndFilterBlocks(true);
                tableCfg.setPinL0FilterAndIndexBlocksInCache(true);
                tableCfg.setFilter(new BloomFilter(10, false));

                // read options
                readOpts = new ReadOptions();
                readOpts = readOpts.setPrefixSameAsStart(true).setVerifyChecksums(false);

                try {
                    log.info("Opening database");
                    final Path dbPath = getPath();
                    if (!Files.isSymbolicLink(dbPath.getParent())) {
                        Files.createDirectories(dbPath.getParent());
                    }

                    if (config.getNodeSpec().isStoreFromBackup() && backupPath().toFile().canWrite()) {
                        log.debug("Restoring database from backup: '{}'", name);
                        try (BackupableDBOptions backupOptions = new BackupableDBOptions(backupPath().toString());
                                RestoreOptions restoreOptions = new RestoreOptions(false);
                                BackupEngine backups = BackupEngine.open(Env.getDefault(), backupOptions)) {

                            if (!backups.getBackupInfo().isEmpty()) {
                                backups.restoreDbFromLatestBackup(
                                        getPath().toString(), getPath().toString(), restoreOptions);
                            }

                        } catch (RocksDBException e) {
                            log.error("Failed to restore database '{}' from backup", name, e);
                        }
                    }
                    log.debug("Initializing new or existing database: '{}'", name);
                    try {
                        db = RocksDB.open(options, dbPath.toString());
                    } catch (RocksDBException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException("Failed to initialize database", e);
                    }

                    alive = true;

                } catch (IOException ioe) {
                    log.error(ioe.getMessage(), ioe);
                    throw new RuntimeException("Failed to initialize database", ioe);
                }

                log.debug("<~ RocksdbKVSource.init(): " + name);
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    public void backup() {
        resetDbLock.readLock().lock();
        if (log.isTraceEnabled()) {
            log.trace("~> RocksdbKVSource.backup(): " + name);
        }
        Path path = backupPath();
        path.toFile().mkdirs();
        try (BackupableDBOptions backupOptions = new BackupableDBOptions(path.toString());
                BackupEngine backups = BackupEngine.open(Env.getDefault(), backupOptions)) {

            backups.createNewBackup(db, true);

            if (log.isTraceEnabled()) {
                log.trace("<~ RocksdbKVSource.backup(): " + name + " done");
            }
        } catch (RocksDBException e) {
            log.error("Failed to backup database '{}'", name, e);
            hintOnTooManyOpenFiles(e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void put(byte[] key, byte[] val) {
        resetDbLock.readLock().lock();
        try {
            if (log.isTraceEnabled()) {
                log.trace(
                        "~> RocksdbKVSource.put(): "
                                + name
                                + ", key: "
                                + Hex.encodeHexString(key)
                                + ", "
                                + (val == null ? "null" : val.length));
            }
            if (val != null) {
                if (db == null) {
                    log.error("db is null");
                } else {
                    log.info("put block key ={} ,val = {}",Hex.encodeHexString(key),Hex.encodeHexString(val));
                    db.put(key, val);
                }
            } else {
                db.delete(key);
            }
            if (log.isTraceEnabled()) {
                log.trace(
                        "<~ RocksdbKVSource.put(): "
                                + name
                                + ", key: "
                                + Hex.encodeHexString(key)
                                + ", "
                                + (val == null ? "null" : val.length));
            }
        } catch (RocksDBException e) {
            System.out.println("Failed to put into db");
            log.error("Failed to put into db '{}'", name, e);
            hintOnTooManyOpenFiles(e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }
    //get 不到
    @Override
    public byte[] get(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            if (log.isTraceEnabled()) {
                log.trace("~> RocksdbKVSource.get(): " + name + ", key: " + Hex.encodeHexString(key));
            }
            byte[] ret = db.get(readOpts, key);
            if (log.isTraceEnabled()) {
                log.trace(
                        "<~ RocksdbKVSource.get(): "
                                + name
                                + ", key: "
                                + Hex.encodeHexString(key)
                                + ", "
                                + (ret == null ? "null" : ret.length));
            }
            return ret;
        } catch (RocksDBException e) {
            log.error("Failed to get from db '{}'", name, e);
            hintOnTooManyOpenFiles(e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            if (log.isTraceEnabled()) {
                log.trace("~> RocksdbKVSource.delete(): " + name + ", key: " + Hex.encodeHexString(key));
            }
            db.delete(key);
            if (log.isTraceEnabled()) {
                log.trace("<~ RocksdbKVSource.delete(): " + name + ", key: " + Hex.encodeHexString(key));
            }
        } catch (RocksDBException e) {
            log.error("Failed to delete from db '{}'", name, e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() throws RuntimeException {
        resetDbLock.readLock().lock();
        try {
            if (log.isTraceEnabled()) {
                log.trace("~> RocksdbKVSource.keys(): " + name);
            }
            try (RocksIterator iterator = db.newIterator()) {
                Set<byte[]> result = new HashSet<>();
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    result.add(iterator.key());
                }
                if (log.isTraceEnabled()) {
                    log.trace("<~ RocksdbKVSource.keys(): " + name + ", " + result.size());
                }
                return result;
            } catch (Exception e) {
                log.error("Error iterating db '{}'", name, e);
                hintOnTooManyOpenFiles(e);
                throw new RuntimeException(e);
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public List<byte[]> prefixValueLookup(byte[] key) {
        List<byte[]> retList = Lists.newLinkedList();
        fetchPrefix(key, pair -> {
            retList.add(pair.getKey());
            return Boolean.FALSE;
        });
        return retList;
    }

    @Override
    public List<byte[]> prefixKeyLookup(byte[] key) {
        List<byte[]> retList = Lists.newLinkedList();
        fetchPrefix(key, pair -> {
            retList.add(pair.getKey());
            return Boolean.FALSE;
        });
        return retList;
    }

    @Override
    public void fetchPrefix(byte[] key, Function<Pair<byte[], byte[]>, Boolean> func) {
        resetDbLock.readLock().lock();
        try (RocksIterator it = db.newIterator(readOpts)) {
            for (it.seek(key); it.isValid(); it.next()) {
                if (BytesUtils.keyStartsWith(it.key(), key)) {
                    if (func.apply(Pair.of(it.key(), it.value()))){
                        return;
                    }
                } else {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Failed to seek by prefix in db '{}'", name, e);
            hintOnTooManyOpenFiles(e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) {
                return;
            }

            log.debug("Close db: {}", name);
            db.close();
            readOpts.close();

            alive = false;

        } catch (Exception e) {
            log.error("Error closing db '{}'", name, e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public void reset() {
        close();
        FileUtils.recursiveDelete(getPath().toString());
        init();
    }

    private Path getPath() {
        return Paths.get(config.getNodeSpec().getStoreDir(), name);
    }

    private Path backupPath() {
        return Paths.get(config.getNodeSpec().getStoreDir(), "backup", name);
    }

    private void hintOnTooManyOpenFiles(Exception e) {
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("too many open files")) {
            log.info("");
            log.info("       Mitigating 'Too many open files':");
            log.info("       either decrease value of xdag.store.max.openfiles parameter ");
            log.info("       or set higher limit by using 'ulimit -n' command in command line");
            log.info("");
        }
    }
}
