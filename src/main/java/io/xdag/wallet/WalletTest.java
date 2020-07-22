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

import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;
import io.xdag.utils.RSAUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import org.spongycastle.util.encoders.Hex;

public class WalletTest {

    public static void main(String[] args) throws Exception {
        // Config config = new Config();
        // Native.init();
        // if (Native.dnet_crypt_init() < 0) {
        // throw new Exception("dnet crypt init failed");
        // }
        //
        // WalletImpl wallet = new WalletImpl();
        // wallet.init(config);

//        WalletTest walletTest = new WalletTest();
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

    public static void main1(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedEncodingException {
        Map<String, String> keyPairMap = RSAUtils.createKeys(1024);
        RSAPublicKey pub = RSAUtils.getPublicKey(keyPairMap.get("publicKey"));
        System.out.println("getModulus length:" + pub.getModulus().bitLength() + " bits");
    }
}
