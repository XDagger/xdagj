package io.xdag.core;

public enum ImportResult {
  //
  IMPORTED_BEST,
  IMPORTED_NOT_BEST,
  // 已存在
  EXIST,
  // 无法找到这个块对应的输入
  NO_PARENT,
  INVALID_BLOCK,
  CONSENSUS_BREAK;

  byte[] hashLow;

  public byte[] getHashLow() {
    return hashLow;
  }

  public void setHashLow(byte[] hashLow) {
    this.hashLow = hashLow;
  }

  public boolean isSuccessful() {
    return equals(IMPORTED_BEST) || equals(IMPORTED_NOT_BEST);
  }
}
