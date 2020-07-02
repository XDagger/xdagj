package io.xdag.db.rocksdb;

import io.xdag.config.Config;
import io.xdag.db.KVSource;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FileUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.rocksdb.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Data
@Slf4j
public class RocksdbKVSource implements KVSource<byte[], byte[]> {

  public static final int HASH_LEN = 32;
  public static final int PREFIX_BYTES = 8;
  //    public static final int PREFIX_BYTES = 8;

  private Config config;

  private String name;
  private RocksDB db;
  private ReadOptions readOpts;
  private boolean alive;

  // The native RocksDB insert/update/delete are normally thread-safe
  // However close operation is not thread-safe.
  // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
  // however blocks them on init/close/delete operations
  private ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

  static {
    RocksDB.loadLibrary();
  }

  public RocksdbKVSource(String name) {
    this.name = name;
    log.debug("New RocksdbKVSource: " + name);
  }

  public RocksdbKVSource(Config config, String name) {
    this.config = config;
    this.name = name;
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
        // TODO: 2020/5/19 windows下启动这个会报错
        // options.setCompressionType(CompressionType.LZ4_COMPRESSION);
        options.setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION);
        options.setLevelCompactionDynamicLevelBytes(true);
        options.setMaxOpenFiles(config.getStoreMaxOpenFiles());
        options.setIncreaseParallelism(config.getStoreMaxThreads());

        // key prefix for state node lookups
        //                options.useFixedLengthPrefixExtractor(8);
        //                options.useCappedPrefixExtractor()

        // table options
        final BlockBasedTableConfig tableCfg;
        options.setTableFormatConfig(tableCfg = new BlockBasedTableConfig());
        tableCfg.setBlockSize(16 * 1024);
        //tableCfg.setBlockCacheSize(32 * 1024 * 1024);
        ClockCache clockCache = new ClockCache(32 * 1024 * 1024);
        tableCfg.setBlockCache(clockCache);
        tableCfg.setCacheIndexAndFilterBlocks(true);
        tableCfg.setPinL0FilterAndIndexBlocksInCache(true);
        tableCfg.setFilterPolicy(new BloomFilter(10, false));

        // read options
        readOpts = new ReadOptions();
        readOpts = readOpts.setPrefixSameAsStart(true).setVerifyChecksums(false);

        try {
          log.debug("Opening database");
          final Path dbPath = getPath();
          if (!Files.isSymbolicLink(dbPath.getParent())) {
            Files.createDirectories(dbPath.getParent());
          }

          if (config.isStoreFromBackup() && backupPath().toFile().canWrite()) {
            log.debug("Restoring database from backup: '{}'", name);
            try (BackupableDBOptions backupOptions =
                    new BackupableDBOptions(backupPath().toString());
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
          System.out.println("db is null");
        }
        db.put(key, val);
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
      log.error("Failed to put into db '{}'", name, e);
      hintOnTooManyOpenFiles(e);
      throw new RuntimeException(e);
    } finally {
      resetDbLock.readLock().unlock();
    }
  }

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
  public void updateBatch(Map<byte[], byte[]> rows) {
    resetDbLock.readLock().lock();
    try {
      if (log.isTraceEnabled()) {
        log.trace("~> RocksDbDataSource.updateBatch(): " + name + ", " + rows.size());
      }
      try {

        try (WriteBatch batch = new WriteBatch();
            WriteOptions writeOptions = new WriteOptions()) {
          for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
            if (entry.getValue() == null) {
              batch.delete(entry.getKey());
            } else {
              batch.put(entry.getKey(), entry.getValue());
            }
          }
          db.write(writeOptions, batch);
        }

        if (log.isTraceEnabled()) {
          log.trace("<~ RocksDbDataSource.updateBatch(): " + name + ", " + rows.size());
        }
      } catch (RocksDBException e) {
        log.error("Error in batch update on db '{}'", name, e);
        hintOnTooManyOpenFiles(e);
        throw new RuntimeException(e);
      }
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
  public List<byte[]> prefixValueLookup(byte[] key, int prefixBytes) {
    //		if (prefixBytes != PREFIX_BYTES)
    //            throw new RuntimeException("RocksdbKVSource.prefixLookup() supports only " +
    // prefixBytes + "-bytes prefix");

    resetDbLock.readLock().lock();
    try {

      if (log.isTraceEnabled()) {
        log.trace(
            "~> RocksdbKVSource.prefixLookup(): " + name + ", key: " + Hex.encodeHexString(key));
      }

      // RocksDB sets initial position of iterator to the first key which is greater or equal to the
      // seek key
      // since keys in RocksDB are ordered in asc order iterator must be initiated with the lowest
      // key
      // thus bytes with indexes greater than PREFIX_BYTES must be nullified
      //            byte[] prefix = new byte[PREFIX_BYTES];
      //            arraycopy(key, 0, prefix, 0, PREFIX_BYTES);

      List<byte[]> retList = new ArrayList<>();
      try (RocksIterator it = db.newIterator(readOpts)) {
        //                it.seek(prefix);
        it.seek(key);
        for (; it.isValid(); it.next()) {
          if (BytesUtils.keyStartsWith(it.key(), key)) {
            retList.add(it.value());
          }
        }
      } catch (Exception e) {
        log.error("Failed to seek by prefix in db '{}'", name, e);
        hintOnTooManyOpenFiles(e);
        throw new RuntimeException(e);
      }

      if (log.isTraceEnabled()) {
        log.trace(
            "<~ RocksdbKVSource.prefixLookup(): "
                + name
                + ", key: "
                + Hex.encodeHexString(key)
                + ", "
                + (retList.isEmpty() ? "null" : retList.size()));
      }

      return retList;

    } finally {
      resetDbLock.readLock().unlock();
    }
  }

  @Override
  public List<byte[]> prefixKeyLookup(byte[] key, int prefixBytes) {
    resetDbLock.readLock().lock();
    try {

      if (log.isTraceEnabled()) {
        log.trace(
            "~> RocksdbKVSource.prefixLookup(): " + name + ", key: " + Hex.encodeHexString(key));
      }

      List<byte[]> retList = new ArrayList<>();
      try (RocksIterator it = db.newIterator(readOpts)) {
        it.seek(key);
        for (; it.isValid(); it.next()) {
          if (BytesUtils.keyStartsWith(it.key(), key)) {
            retList.add(it.key());
          }
        }
      } catch (Exception e) {
        log.error("Failed to seek by prefix in db '{}'", name, e);
        hintOnTooManyOpenFiles(e);
        throw new RuntimeException(e);
      }

      if (log.isTraceEnabled()) {
        log.trace(
            "<~ RocksdbKVSource.prefixLookup(): "
                + name
                + ", key: "
                + Hex.encodeHexString(key)
                + ", "
                + (retList.isEmpty() ? "null" : retList.size()));
      }

      return retList;

    } finally {
      resetDbLock.readLock().unlock();
    }
  }

  @Override
  public boolean flush() {
    return false;
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
    return Paths.get(config.getStoreDir(), name);
  }

  private Path backupPath() {
    return Paths.get(config.getStoreDir(), "backup", name);
  }

  private void hintOnTooManyOpenFiles(Exception e) {
    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("too many open files")) {
      log.info("");
      log.info("       Mitigating 'Too many open files':");
      log.info("       either decrease value of xdag.store.max.openfiles parameter in xdagj.conf");
      log.info("       or set higher limit by using 'ulimit -n' command in command line");
      log.info("");
    }
  }
}
