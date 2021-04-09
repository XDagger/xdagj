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

import io.xdag.crypto.Base58;
import io.xdag.crypto.Bip32ECKeyPair;
import io.xdag.crypto.Credentials;
import io.xdag.crypto.MnemonicUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;



import static org.junit.Assert.assertEquals;
import static io.xdag.crypto.Bip32Test.addChecksum;
import static io.xdag.crypto.Bip32Test.serializePrivate;
import static io.xdag.crypto.Bip32Test.serializePublic;
import static io.xdag.crypto.SampleKeys.PASSWORD;
import static io.xdag.wallet.WalletUtilsTest.createTempDir;

public class Bip44WalletUtilsTest {

    private File tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = createTempDir();
    }

    @After
    public void tearDown() {
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
    }

    @Test
    public void generateBip44KeyPair() {
        String mnemonic =
                "spider elbow fossil truck deal circle divert sleep safe report laundry above";
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        String seedStr = bytesToHex(seed);
        assertEquals(
                "f0d2ab78b96acd147119abad1cd70eb4fec4f0e0a95744cf532e6a09347b08101213b4cbf50eada0eb89cba444525fe28e69707e52aa301c6b47ce1c5ef82eb5",
                seedStr);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        assertEquals(
                "xprv9s21ZrQH143K2yA9Cdad5gjqHRC7apVUgEyYq5jXeXigDZ3PfEnps44tJprtMXr7PZivEsin6Qrbad7PuiEy4tn5jAEK6A3U46f9KvfRCmD",
                Base58.encode(addChecksum(serializePrivate(masterKeypair))));

        Bip32ECKeyPair bip44Keypair = Bip44WalletUtils.generateBip44KeyPair(masterKeypair);

        assertEquals(
                "xprvA3bRNS6bxNHSZQvJrLiPhVePhqy69cdmsJ2oa2XuMcyuiMDn13ZAVsVDWyQRHZLJrQMMs3qUEf6GDarnJpzBKHXVFcLZgvkD9oGDR845BTL",
                Base58.encode(addChecksum(serializePrivate(bip44Keypair))));
        assertEquals(
                "xpub6GammwdVnjqjmtzmxNFQ4db8FsoaZ5MdEWxQNQwWuxWtb9YvYasR3fohNEiSmcG4pzTziN62M3LZvEowb74cgqW78BLZayCgBDRuGH89xni",
                Base58.encode(addChecksum(serializePublic(bip44Keypair))));

        // Verify address according to https://iancoleman.io/bip39/
        Credentials credentials = Bip44WalletUtils.loadBip44Credentials("", mnemonic);
        assertEquals("0xddc049a60750affe6f53b1d77208e4108f14d742", credentials.getAddress().toLowerCase());
    }

    @Test
    public void generateBip44KeyPairTestNet() {
        String mnemonic =
                "spider elbow fossil truck deal circle divert sleep safe report laundry above";
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        String seedStr = bytesToHex(seed);
        assertEquals(
                "f0d2ab78b96acd147119abad1cd70eb4fec4f0e0a95744cf532e6a09347b08101213b4cbf50eada0eb89cba444525fe28e69707e52aa301c6b47ce1c5ef82eb5",
                seedStr);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        assertEquals(
                "xprv9s21ZrQH143K2yA9Cdad5gjqHRC7apVUgEyYq5jXeXigDZ3PfEnps44tJprtMXr7PZivEsin6Qrbad7PuiEy4tn5jAEK6A3U46f9KvfRCmD",
                Base58.encode(addChecksum(serializePrivate(masterKeypair))));

        Bip32ECKeyPair bip44Keypair = Bip44WalletUtils.generateBip44KeyPair(masterKeypair);

        assertEquals(
                "xprvA3bRNS6bxNHSZQvJrLiPhVePhqy69cdmsJ2oa2XuMcyuiMDn13ZAVsVDWyQRHZLJrQMMs3qUEf6GDarnJpzBKHXVFcLZgvkD9oGDR845BTL",
                Base58.encode(addChecksum(serializePrivate(bip44Keypair))));
        assertEquals(
                "xpub6GammwdVnjqjmtzmxNFQ4db8FsoaZ5MdEWxQNQwWuxWtb9YvYasR3fohNEiSmcG4pzTziN62M3LZvEowb74cgqW78BLZayCgBDRuGH89xni",
                Base58.encode(addChecksum(serializePublic(bip44Keypair))));
    }

    @Test
    public void testGenerateBip44Wallets() throws Exception {
        Bip39Wallet wallet = Bip44WalletUtils.generateBip44Wallet(PASSWORD, tempDir);
        byte[] seed = MnemonicUtils.generateSeed(wallet.getMnemonic(), PASSWORD);
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        Bip32ECKeyPair bip44Keypair = Bip44WalletUtils.generateBip44KeyPair(masterKeypair);
        Credentials credentials = Credentials.create(bip44Keypair);

        assertEquals(
                credentials, Bip44WalletUtils.loadBip44Credentials(PASSWORD, wallet.getMnemonic()));
    }

    private String bytesToHex(byte[] bytes) {
        final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

        char[] chars = new char[2 * bytes.length];
        for (int i = 0; i < bytes.length; ++i) {
            chars[2 * i] = HEX_CHARS[(bytes[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[bytes[i] & 0x0F];
        }
        return new String(chars);
    }
}

