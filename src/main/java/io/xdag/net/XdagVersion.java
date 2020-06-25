package io.xdag.net;

import java.util.ArrayList;
import java.util.List;

public enum XdagVersion {
  V03((byte) 03);

  public static final byte LOWER = V03.getCode();
  public static final byte UPPER = V03.getCode();

  private byte code;

  XdagVersion(byte code) {
    this.code = code;
  }

  public byte getCode() {
    return code;
  }

  public static XdagVersion fromCode(int code) {
    for (XdagVersion v : values()) {
      if (v.code == code) {
        return v;
      }
    }

    return null;
  }

  public static boolean isSupported(byte code) {
    return code >= LOWER && code <= UPPER;
  }

  public static List<XdagVersion> supported() {
    List<XdagVersion> supported = new ArrayList<>();
    for (XdagVersion v : values()) {
      if (isSupported(v.code)) {
        supported.add(v);
      }
    }
    return supported;
  }

  public boolean isCompatible(XdagVersion version) {
    if (version.getCode() >= V03.getCode()) {
      return this.getCode() >= V03.getCode();
    } else {
      return this.getCode() < V03.getCode();
    }
  }
}
