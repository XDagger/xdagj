package io.xdag.crypto;

import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ECKeyTest {

  @Test
  public void generKeyTest() {

    String priv = "93c930b7a7260f4f896561417fbe54ff16b9e49112fb41a69f230c179f2048d0";
    byte[] priv32 = Hex.decode(priv);
    // priv32 = Arrays.reverse(priv32);
    System.out.println(Hex.toHexString(priv32));
    ECKey ecKey = ECKey.fromPrivate(priv32);

    System.out.println(Hex.toHexString(ecKey.pub.getEncoded(true)));
  }
}
