package io.xdag.crypto.jce;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;

public final class XdagProvider {

  private static class Holder {
    private static final Provider INSTANCE;

    static {
      Provider p = Security.getProvider("SC");
      INSTANCE = (p != null) ? p : new BouncyCastleProvider();
    }
  }

  public static Provider getInstance() {
    return Holder.INSTANCE;
  }
}
