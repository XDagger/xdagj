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

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.crypto.Sign;
import io.xdag.utils.Numeric;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;
import io.xdag.utils.FileUtils;

public class OldWallet {

    private List<KeyInternalItem> keyLists = new ArrayList<>();
    private KeyInternalItem defKey;
    private int keysNum = 0;

//    @Override
    public int init(Config config) throws Exception {
        File dnetDatFile = new File(Config.DNET_KEY_FILE);
        Native.dfslib_random_init();
        Native.crc_init();
        boolean fileNotExist = !dnetDatFile.exists() || dnetDatFile.length() == 0;
//        Pair<String, String> pair = getPassword(fileNotExist);
        Pair<String, String> pair = Pair.of("123456", "123456");
        if (pair == null) {
            System.out.println("wallet init fail");
            System.exit(1);
        }
        if (fileNotExist) {
            // 文件不存在 创建
            byte[] dnetKeyBytes = Native.general_dnet_key(pair.getLeft(), pair.getRight());
            config.setDnetKeyBytes(dnetKeyBytes);
            FileOutputStream fileOutputStream = new FileOutputStream(dnetDatFile);
            IOUtils.write(dnetKeyBytes, fileOutputStream);
            fileOutputStream.close();
        } else {
            // 文件存在 进行校验
            byte[] dnetKeyBytes = FileUtils.readDnetDat(dnetDatFile);
            int res = Native.verify_dnet_key(pair.getLeft(), dnetKeyBytes);

            if (res < 0) {
                return res;
            }
            config.setDnetKeyBytes(dnetKeyBytes);
        }
        pasreWalletDat();
        return 0;
    }

//    @Override
    public KeyInternalItem getDefKey() {
        return defKey;
    }

//    @Override
    public void createNewKey() {
        addKey(null);
    }

//    @Override
    public ECKeyPair getKeyByIndex(int index) {
        return keyLists.get(index).ecKey;
    }

//    @Override
    public List<KeyInternalItem> getKey_internal() {
        return keyLists;
    }

    private void addKey(BigInteger priv)  {
        if (priv == null) {
            File walletDatFile = new File(Config.WALLET_KEY_FILE);
            ECKeyPair ecKey = Keys.createEcKeyPair();
            byte[] publicKeyBytes = Sign.publicKeyBytesFromPrivate(ecKey.getPrivateKey(), false);
            byte lastByte = publicKeyBytes[publicKeyBytes.length - 1];
            // 奇偶
            boolean pubKeyParity = (lastByte & 1) == 0;
            KeyInternalItem newKey = new KeyInternalItem();
            newKey.ecKey = ecKey;
            newKey.pubKeyParity = pubKeyParity;
            defKey = newKey;
            keyLists.add(newKey);
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
                byte[] priv32 = Numeric.toBytesPadded(ecKey.getPrivateKey(), 32);
                byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, keysNum++);
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
    }

    private Pair<String, String> getPassword(boolean fileNotExist) {
        char[] passwordB;
        if (fileNotExist) {
            passwordB = System.console().readPassword("Please set password: ");
        } else {
            passwordB = System.console().readPassword("Please input password: ");
        }
        String password = Arrays.toString(passwordB);
        String random = null;
        // 文件存在 仅需要输入一次密码 不存在 则需要重复输入一次
        if (fileNotExist) {
            char[] repeatPasswordB = System.console().readPassword("Please repeat password: ");
            if (!Arrays.equals(repeatPasswordB, passwordB)) {
                System.out.println("passwords are inconsistent, please check");
                return null;
            }
            char[] randomB = System.console().readPassword("Please Input random: ");
            random = Arrays.toString(randomB);
        }

        return Pair.of(password, random);
    }

    private void pasreWalletDat() throws Exception {
        File walletDatFile = new File(Config.WALLET_KEY_FILE);
        if (!walletDatFile.exists() || walletDatFile.length() == 0) {
            // if wallet.dat not exist create it
            ECKeyPair ecKey = Keys.createEcKeyPair();
            byte[] publicKeyBytes = Sign.publicKeyBytesFromPrivate(ecKey.getPrivateKey(), false);
            byte lastByte = publicKeyBytes[publicKeyBytes.length - 1];
            // 奇偶
            boolean pubKeyParity = (lastByte & 1) == 0;
            KeyInternalItem newKey = new KeyInternalItem();
            newKey.ecKey = ecKey;
            newKey.pubKeyParity = pubKeyParity;
            defKey = newKey;
            keyLists.add(newKey);
            if (!walletDatFile.exists()) {
                if (!walletDatFile.createNewFile()) {
                    System.out.println("create new file wallet.dat failed");
                    throw new Exception();
                }
            }
            // encrypted the priv byte with user's password
            FileOutputStream fileOutputStream = new FileOutputStream(walletDatFile);
            byte[] priv32 = Numeric.toBytesPadded(ecKey.getPrivateKey(), 32);
            byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, keysNum++);
            IOUtils.write(priv32Encrypted, fileOutputStream);
            fileOutputStream.close();
        } else {
            // read wallet.dat
            FileInputStream fileInputStream = new FileInputStream(walletDatFile);
            byte[] priv32Encrypted = new byte[32];
            while (fileInputStream.read(priv32Encrypted) != -1) {
                byte[] priv32 = Native.uncrypt_wallet_key(priv32Encrypted, keysNum++);
                ECKeyPair ecKey = ECKeyPair.create(Numeric.toBigInt(priv32));
                byte[] publicKeyBytes = Sign.publicKeyBytesFromPrivate(ecKey.getPrivateKey(), false);
                byte lastByte = publicKeyBytes[publicKeyBytes.length - 1];
                // 奇偶
                boolean pubKeyParity = (lastByte & 1) == 0;
                KeyInternalItem newKey = new KeyInternalItem();
                newKey.ecKey = ecKey;
                newKey.pubKeyParity = pubKeyParity;
                keyLists.add(newKey);
            }
            // 最后一个
            defKey = keyLists.get(keyLists.size() - 1);
            fileInputStream.close();
        }
    }
}
