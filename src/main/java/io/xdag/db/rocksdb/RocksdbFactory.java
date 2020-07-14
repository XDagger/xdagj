package io.xdag.db.rocksdb;

import io.xdag.config.Config;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.KVSource;
import io.xdag.db.SimpleFileStore;
import java.util.EnumMap;

public class RocksdbFactory implements DatabaseFactory {

    private final EnumMap<DatabaseName, KVSource<byte[], byte[]>> databases = new EnumMap<>(DatabaseName.class);

    protected Config config;

    public RocksdbFactory(Config config) {
        this.config = config;
    }

    @Override
    public KVSource<byte[], byte[]> getDB(DatabaseName name) {
        return databases.computeIfAbsent(
                name,
                k -> {
                    RocksdbKVSource dataSource = new RocksdbKVSource(name.toString());
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

    @Override
    public SimpleFileStore getSumsDB() {
        return new SimpleFileStore(config.getStoreDir());
    }
}
