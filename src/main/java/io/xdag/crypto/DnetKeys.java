package io.xdag.crypto;

import lombok.Data;

@Data
public class DnetKeys {

  public byte[] prv;
  public byte[] pub;

  public byte[] sect0_encoded;
  public byte[] sect0;

  public DnetKeys() {
    prv = new byte[1024];
    pub = new byte[1024];
    sect0_encoded = new byte[512];
    sect0 = new byte[512];
  }
}
