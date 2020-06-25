package io.xdag.db;

public interface DatabaseFactory {
  KVSource<byte[], byte[]> getDB(DatabaseName name);

  /** Close all opened resources. */
  void close();

  SimpleFileStore getSumsDB();
}
