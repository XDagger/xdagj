/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.wallet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.LegacyAddress;
import io.xdag.utils.HashUtils;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Wallet {

    private static final int N_LIGHT = 1 << 12;
    private static final int P_LIGHT = 6;

    private static final int N_STANDARD = 1 << 18;
    private static final int P_STANDARD = 1;

    static final int PRIVATE_KEY_SIZE = 32;

    private static final int R = 8;
    private static final int DKLEN = 32;

    // old xdag version before 0.4
    private static final int VERSION_031 = 1;
    // new xdag address version from xdagj after 0.5.0
    private static final int VERSION_050 = 2;

    private static final String CIPHER = "aes-128-ctr";
    static final String AES_128_CTR = "pbkdf2";
    static final String SCRYPT = "scrypt";

//    static final int BIP44_COIN_TYPE = 586;

//    /** init wallet */
//    int init(Config config) throws Exception;
//
//    /** 获取到一个新的key */
//    KeyInternalItem getDefKey();
//
//    /** 创建一个新的key */
//    void createNewKey();
//
//    /** 通过编号获取密钥对 */
//    ECKey getKeyByIndex(int index);
//
//    List<KeyInternalItem> getKey_internal();

    public static WalletFile createStandard(Config config, String password, ECKey key)
            throws CipherException {
        return create(config, password, key, N_STANDARD, P_STANDARD);
    }

    public static WalletFile createLight(Config config, String password, ECKey key)
            throws CipherException {
        return create(config, password, key, N_LIGHT, P_LIGHT);
    }

    public static WalletFile create(Config config, String password, ECKey key, int n, int p) throws CipherException {
        byte[] salt = generateRandomBytes(32);
        byte[] derivedKey = generateDerivedScryptKey(password.getBytes(UTF_8), salt, n, R, p, DKLEN);
        byte[] encryptKey = Arrays.copyOfRange(derivedKey, 0, 16);
        byte[] iv = generateRandomBytes(16);
        byte[] privateKeyBytes = WalletUtils.toBytesPadded(key.getPrivKey(), PRIVATE_KEY_SIZE);
        byte[] cipherText = performCipherOperation(Cipher.ENCRYPT_MODE, iv, encryptKey, privateKeyBytes);
        byte[] mac = generateMac(derivedKey, cipherText);
        return createWalletFile(config, key, cipherText, iv, salt, mac, n, p);
    }

    private static WalletFile createWalletFile(
            Config config,
            ECKey key,
            byte[] cipherText,
            byte[] iv,
            byte[] salt,
            byte[] mac,
            int n,
            int p) {

        WalletFile walletFile = new WalletFile();
        LegacyAddress address = LegacyAddress.fromPubKeyHash(config, key.getPubKeyHash(true));
        walletFile.setAddress(address.toBase58());
        WalletFile.Crypto crypto = new WalletFile.Crypto();
        crypto.setCipher(CIPHER);
        crypto.setCiphertext(WalletUtils.toHexStringNoPrefix(cipherText));
        WalletFile.CipherParams cipherParams = new WalletFile.CipherParams();
        cipherParams.setIv(WalletUtils.toHexStringNoPrefix(iv));
        crypto.setCipherparams(cipherParams);
        crypto.setKdf(SCRYPT);
        WalletFile.ScryptKdfParams kdfParams = new WalletFile.ScryptKdfParams();
        kdfParams.setDklen(DKLEN);
        kdfParams.setN(n);
        kdfParams.setP(p);
        kdfParams.setR(R);
        kdfParams.setSalt(WalletUtils.toHexStringNoPrefix(salt));
        crypto.setKdfparams(kdfParams);
        crypto.setMac(WalletUtils.toHexStringNoPrefix(mac));
        walletFile.setCrypto(crypto);
        walletFile.setId(UUID.randomUUID().toString());
        walletFile.setVersion(VERSION_050);
        return walletFile;
    }

    private static byte[] generateDerivedScryptKey(byte[] password, byte[] salt, int n, int r, int p, int dkLen) {
        return SCrypt.generate(password, salt, n, r, p, dkLen);
    }

    private static byte[] generateAes128CtrDerivedKey(byte[] password, byte[] salt, int c, String prf) throws CipherException {
        if (!prf.equals("hmac-sha256")) {
            throw new CipherException("Unsupported prf:" + prf);
        }
        // Java 8 supports this, but you have to convert the password to a character array, see
        // http://stackoverflow.com/a/27928435/3211687
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(password, salt, c);
        return ((KeyParameter) gen.generateDerivedParameters(256)).getKey();
    }

    private static byte[] performCipherOperation(int mode, byte[] iv, byte[] encryptKey, byte[] text) throws CipherException {
        try {
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(encryptKey, "AES");
            cipher.init(mode, secretKeySpec, ivParameterSpec);
            return cipher.doFinal(text);
        } catch (NoSuchPaddingException
                | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | InvalidKeyException
                | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new CipherException("Error performing cipher operation", e);
        }
    }

    private static byte[] generateMac(byte[] derivedKey, byte[] cipherText) {
        byte[] result = new byte[16 + cipherText.length];
        System.arraycopy(derivedKey, 16, result, 0, 16);
        System.arraycopy(cipherText, 0, result, 16, cipherText.length);
        return HashUtils.sha3(result);
    }

    public static ECKey decrypt(String password, WalletFile walletFile) throws CipherException {
        validate(walletFile);
        WalletFile.Crypto crypto = walletFile.getCrypto();
        byte[] mac = WalletUtils.hexStringToByteArray(crypto.getMac());
        byte[] iv = WalletUtils.hexStringToByteArray(crypto.getCipherparams().getIv());
        byte[] cipherText = WalletUtils.hexStringToByteArray(crypto.getCiphertext());
        byte[] derivedKey;
        WalletFile.KdfParams kdfParams = crypto.getKdfparams();
        if (kdfParams instanceof WalletFile.ScryptKdfParams) {
            WalletFile.ScryptKdfParams scryptKdfParams = (WalletFile.ScryptKdfParams) crypto.getKdfparams();
            int dklen = scryptKdfParams.getDklen();
            int n = scryptKdfParams.getN();
            int p = scryptKdfParams.getP();
            int r = scryptKdfParams.getR();
            byte[] salt = WalletUtils.hexStringToByteArray(scryptKdfParams.getSalt());
            derivedKey = generateDerivedScryptKey(password.getBytes(UTF_8), salt, n, r, p, dklen);
        } else if (kdfParams instanceof WalletFile.Aes128CtrKdfParams) {
            WalletFile.Aes128CtrKdfParams aes128CtrKdfParams = (WalletFile.Aes128CtrKdfParams) crypto.getKdfparams();
            int c = aes128CtrKdfParams.getC();
            String prf = aes128CtrKdfParams.getPrf();
            byte[] salt = WalletUtils.hexStringToByteArray(aes128CtrKdfParams.getSalt());
            derivedKey = generateAes128CtrDerivedKey(password.getBytes(UTF_8), salt, c, prf);
        } else {
            throw new CipherException("Unable to deserialize params: " + crypto.getKdf());
        }

        byte[] derivedMac = generateMac(derivedKey, cipherText);
        if (!Arrays.equals(derivedMac, mac)) {
            throw new CipherException("Invalid password provided");
        }

        byte[] encryptKey = Arrays.copyOfRange(derivedKey, 0, 16);
        byte[] privateKey = performCipherOperation(Cipher.DECRYPT_MODE, iv, encryptKey, cipherText);
        return ECKey.fromPrivate(privateKey);
    }

    static void validate(WalletFile walletFile) throws CipherException {
        WalletFile.Crypto crypto = walletFile.getCrypto();
        if (walletFile.getVersion() != VERSION_050) {
            throw new CipherException("Wallet version is not supported");
        }
        if (!crypto.getCipher().equals(CIPHER)) {
            throw new CipherException("Wallet cipher is not supported");
        }
        if (!crypto.getKdf().equals(AES_128_CTR) && !crypto.getKdf().equals(SCRYPT)) {
            throw new CipherException("KDF type is not supported");
        }
    }


    static byte[] generateRandomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public static void main(String[] args) throws CipherException, JsonProcessingException {
        Config mainnetConfig = new Config();
        ECKey key = new ECKey();


        int c = 262144;
        String prf = "hmac-sha256";
        byte[] salt = WalletUtils.hexStringToByteArray("0e4cf3893b25bb81efaae565728b5b7cde6a84e224cbf9aed3d69a31c981b702");
        Wallet.generateAes128CtrDerivedKey("123456".getBytes(UTF_8), salt, c, prf);
        WalletFile walletFile = Wallet.create(mainnetConfig, "123456", key,2,2);
        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println(objectMapper.writeValueAsString(walletFile));
    }
}
