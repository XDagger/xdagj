package io.xdag.crypto.jce;

import java.security.Provider;
import java.security.Security;
import org.spongycastle.jce.provider.BouncyCastleProvider;

public final class XdagProvider {

  public static Provider getInstance() {
    return Holder.INSTANCE;
  }

  private static class Holder {
    private static final Provider INSTANCE;

    static {
      Provider p = Security.getProvider("SC");
      INSTANCE = (p != null) ? p : new BouncyCastleProvider();
    }
  }
}
