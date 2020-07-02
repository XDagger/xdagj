package io.xdag.wallet;

import io.xdag.utils.RSAUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import org.spongycastle.util.encoders.Hex;

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;

public class WalletTest {

  public static void main(String[] args) throws Exception {
//    Config config = new Config();
//    Native.init();
//    if (Native.dnet_crypt_init() < 0) {
//      throw new Exception("dnet crypt init failed");
//    }
//
//    WalletImpl wallet = new WalletImpl();
//    wallet.init(config);

    WalletTest walletTest = new WalletTest();

  }

  public static void readDat(String path) throws IOException {
    File file = new File(path);

    try (FileInputStream inputStream = new FileInputStream(file)) {
      byte[] buffer = new byte[2048];
      while (true) {
        int len = inputStream.read(buffer);
        if (len == -1) {
          break;
        }
        System.out.println(Hex.toHexString(buffer));
      }
    }
  }

  public static void main2(String[] args) throws Exception {
    Native.init();
    if (Native.dnet_crypt_init() < 0) {
      throw new Exception("dnet crypt init failed");
    }
    Wallet xdagWallet = new WalletImpl();
    xdagWallet.init(new Config());

    byte[] array = new byte[8];
    byte[] random = Native.generate_random_bytes(array, 8);
    System.out.println(Hex.toHexString(random));
  }

  public static void main1(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
    Map<String, String> keyPairMap = RSAUtils.createKeys(1024);
    RSAPublicKey pub = RSAUtils.getPublicKey(keyPairMap.get("publicKey"));

    System.out.println("getModulus length:" + pub.getModulus().bitLength() + " bits");
  }
}
