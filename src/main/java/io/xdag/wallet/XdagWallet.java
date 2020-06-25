package io.xdag.wallet;

import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class XdagWallet {

  private int nkeys = 0;
  private List<key_internal_item> key_internal = new ArrayList<>();

  private key_internal_item defKey;

  public static final int DNET_KEY_SIZE = 4096;
  public static final int DNET_KEYLEN = 32;
  // TODO：File 路径 修改至resources
  public static final String DNET_KEY_FILE = Config.root + "dnet_key.dat";
  public static final String WALLET_KEY_FILE = Config.root + "wallet-testnet.dat";

  public XdagWallet() {}

  public List<key_internal_item> getKey_internal() {
    return key_internal;
  }

  public int init() throws Exception {
    // open dnet.dat
    File dnetDatFile = new File(DNET_KEY_FILE);
    if (!dnetDatFile.exists() || dnetDatFile.length() == 0) {

      // TODO: ask user to input password and confirm password，and input random keys
      System.out.println("Input password:");
      Scanner scanner = new Scanner(System.in);
      String password = scanner.nextLine();
      System.out.println("Input random:");
      String random = scanner.nextLine();

      //            Native.set_user_dnet_crypt("123");
      Native.set_user_dnet_crypt(password);
      //            Native.set_user_random("123");
      Native.set_user_random(random);
      byte[] dnetKeyBytes = Native.make_dnet_keys(DNET_KEYLEN);

      FileOutputStream fileOutputStream = new FileOutputStream(dnetDatFile);
      IOUtils.write(dnetKeyBytes, fileOutputStream);
      fileOutputStream.close();
    }

    File walletDatFile = new File(WALLET_KEY_FILE);
    if (!walletDatFile.exists() || walletDatFile.length() == 0) {
      // if wallet.dat not exist create it
      ECKey ecKey = new ECKey();
      byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
      boolean pubKeyParity = (lastByte & 1) == 0; // 奇偶

      key_internal_item newKey = new key_internal_item();
      newKey.ecKey = ecKey;
      newKey.pubKeyParity = pubKeyParity;

      defKey = newKey;

      key_internal.add(newKey);

      if (!walletDatFile.exists()) {
        if (!walletDatFile.createNewFile()) {
          System.out.println("create new file wallet.dat failed");
          throw new Exception();
        }
      }

      // encrypted the priv byte with user's password
      FileOutputStream fileOutputStream = new FileOutputStream(walletDatFile);
      byte[] priv32 = ecKey.getPrivKeyBytes();
      byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, nkeys++);
      IOUtils.write(priv32Encrypted, fileOutputStream);
      fileOutputStream.close();
    } else {
      // read wallet.dat
      FileInputStream fileInputStream = new FileInputStream(walletDatFile);
      byte[] priv32Encrypted = new byte[32];
      while (fileInputStream.read(priv32Encrypted) != -1) {

        System.out.println("this is priv32Encrypted [");
        byte[] priv32 = Native.uncrypt_wallet_key(priv32Encrypted, nkeys++);
        ECKey ecKey = ECKey.fromPrivate(priv32);

        byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
        boolean pubKeyParity = (lastByte & 1) == 0; // 奇偶

        key_internal_item newKey = new key_internal_item();
        newKey.ecKey = ecKey;
        newKey.pubKeyParity = pubKeyParity;
        key_internal.add(newKey);
      }
      defKey = key_internal.get(key_internal.size() - 1); // 最后一个
    }
    return 0;
  }

  public key_internal_item getDefKey() {
    return defKey;
  }

  public void createNewKey() {
    addKey(null);
  }

  private int addKey(BigInteger priv) {
    if (priv == null) {
      File walletDatFile = new File(WALLET_KEY_FILE);

      ECKey ecKey = new ECKey();
      byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
      boolean pubKeyParity = (lastByte & 1) == 0; // 奇偶

      key_internal_item newKey = new key_internal_item();
      newKey.ecKey = ecKey;
      newKey.pubKeyParity = pubKeyParity;

      defKey = newKey;
      key_internal.add(newKey);
      FileOutputStream fileOutputStream = null;

      try {
        fileOutputStream = new FileOutputStream(walletDatFile, true);
        if (!walletDatFile.exists()) {
          if (!walletDatFile.createNewFile()) {
            System.out.println("create new file wallet.dat failed");
            throw new Exception();
          }
        }
        // encrypted the priv byte with user's password
        byte[] priv32 = ecKey.getPrivKeyBytes();
        byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, nkeys++);
        IOUtils.write(priv32Encrypted, fileOutputStream);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (fileOutputStream != null) {
          try {
            fileOutputStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
    return 0;
  }

  public ECKey getKeyByIndex(int index) {
    return key_internal.get(index).ecKey;
  }
}
