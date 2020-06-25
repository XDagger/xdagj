package io.xdag.utils;

import io.xdag.crypto.jce.XdagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.util.encoders.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Random;

import static java.util.Arrays.copyOfRange;

public class HashUtils {

  private static final Logger LOG = LoggerFactory.getLogger(HashUtils.class);

  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final byte[] ZERO_BYTE_ARRAY = new byte[] {0};

  public static final byte[] EMPTY_DATA_HASH;

  private static final Provider CRYPTO_PROVIDER;

  private static final String HASH_256_ALGORITHM_NAME;
  private static final String HASH_512_ALGORITHM_NAME;

  static {
    Security.addProvider(XdagProvider.getInstance());
    CRYPTO_PROVIDER = Security.getProvider(XdagProvider.getInstance().getName());
    HASH_256_ALGORITHM_NAME = "SHA-256";
    HASH_512_ALGORITHM_NAME = "SHA-512";
    EMPTY_DATA_HASH = sha3(EMPTY_BYTE_ARRAY);
  }

  /**
   * @param input - data for hashing
   * @return - sha256 hash of the data
   */
  public static byte[] sha256(byte[] input) {
    try {
      MessageDigest sha256digest = MessageDigest.getInstance("SHA-256");
      return sha256digest.digest(input);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Can't find such algorithm", e);
      throw new RuntimeException(e);
    }
  }

  public static byte[] sha3(byte[] input) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME, CRYPTO_PROVIDER);
      digest.update(input);
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Can't find such algorithm", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * hashing chunk of the data
   *
   * @param input - data for hash
   * @param start - start of hashing chunk
   * @param length - length of hashing chunk
   * @return - keccak hash of the chunk
   */
  public static byte[] sha3(byte[] input, int start, int length) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME, CRYPTO_PROVIDER);
      digest.update(input, start, length);
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Can't find such algorithm", e);
      throw new RuntimeException(e);
    }
  }

  public static byte[] sha512(byte[] input) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(HASH_512_ALGORITHM_NAME, CRYPTO_PROVIDER);
      digest.update(input);
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Can't find such algorithm", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * @param data - message to hash
   * @return - reipmd160 hash of the message
   */
  public static byte[] ripemd160(byte[] data) {
    Digest digest = new RIPEMD160Digest();
    if (data != null) {
      byte[] resBuf = new byte[digest.getDigestSize()];
      digest.update(data, 0, data.length);
      digest.doFinal(resBuf, 0);
      return resBuf;
    }
    throw new NullPointerException("Can't hash a NULL value");
  }

  /**
   * Calculates RIGTMOST160(SHA3(input)). This is used in address calculations. *
   *
   * @param input - data
   * @return - 20 right bytes of the hash keccak of the data
   */
  public static byte[] sha3omit12(byte[] input) {
    byte[] hash = sha3(input);
    return copyOfRange(hash, 12, hash.length);
  }

  /**
   * @see #doubleDigest(byte[], int, int)
   * @param input -
   * @return -
   */
  public static byte[] doubleDigest(byte[] input) {
    return doubleDigest(input, 0, input.length);
  }

  //    /**每计算一次就生成一个对象的话就太费事了 **/
  //    //TODO:修改成随着线程增加而生成一个XdagSha256Digest对象
  //    public static byte[] doubleSha256(byte[] input) throws IOException {
  //        return new XdagSha256Digest().getSha256d(input);
  //    }

  /**
   * Calculates the SHA-256 hash of the given byte range, and then hashes the resulting hash again.
   * This is standard procedure in Bitcoin. The resulting hash is in big endian form.
   *
   * @param input -
   * @param offset -
   * @param length -
   * @return -
   */
  public static byte[] doubleDigest(byte[] input, int offset, int length) {
    try {
      MessageDigest sha256digest = MessageDigest.getInstance("SHA-256");
      sha256digest.reset();
      sha256digest.update(input, offset, length);
      byte[] first = sha256digest.digest();
      return sha256digest.digest(first);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Can't find such algorithm", e);
      throw new RuntimeException(e);
    }
  }

  /** @return - generate random 32 byte hash */
  public static byte[] randomHash() {

    byte[] randomHash = new byte[32];
    Random random = new Random();
    random.nextBytes(randomHash);
    return randomHash;
  }

  public static String shortHash(byte[] hash) {
    return Hex.toHexString(hash).substring(0, 6);
  }
}
