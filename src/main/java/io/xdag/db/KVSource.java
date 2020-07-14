package io.xdag.db;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface KVSource<K, V> {

    String getName();

    void setName(String name);

    boolean isAlive();

    void init();

    void close();

    void reset();

    void put(K key, V val);

    V get(K key);

    void delete(K key);

    boolean flush();

    Set<byte[]> keys() throws RuntimeException;

    List<K> prefixKeyLookup(byte[] key, int prefixBytes);

    List<V> prefixValueLookup(byte[] key, int prefixBytes);

    void updateBatch(Map<K, V> rows);
}
