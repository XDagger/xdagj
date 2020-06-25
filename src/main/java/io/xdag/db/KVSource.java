package io.xdag.db;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface KVSource<K, V> {

  public String getName();

  public void setName(String name);

  public boolean isAlive();

  public void init();

  public void close();

  public void reset();

  public void put(K key, V val);

  public V get(K key);

  public void delete(K key);

  boolean flush();

  public Set<byte[]> keys() throws RuntimeException;

  public List<K> prefixKeyLookup(byte[] key, int prefixBytes);

  public List<V> prefixValueLookup(byte[] key, int prefixBytes);

  public void updateBatch(Map<K, V> rows);
}
