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

import static io.xdag.crypto.Bip32Test.addChecksum;
import static io.xdag.crypto.Bip32Test.serializePrivate;
import static io.xdag.crypto.Bip32Test.serializePublic;
import static org.junit.Assert.assertEquals;

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Bip32ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.crypto.MnemonicUtils;
import io.xdag.crypto.SampleKeys;
import io.xdag.utils.BytesUtils;
import java.io.IOException;
import java.security.Security;
import java.util.Collections;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.io.Base58;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WalletUtilsTest {

    static { Security.addProvider(new BouncyCastleProvider());  }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private String pwd;
    private Wallet wallet;

    @Before
    public void setUp() {
        pwd = "password";
        Config config = new DevnetConfig();
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        SECP256K1.KeyPair key = SECP256K1.KeyPair.fromSecretKey(SampleKeys.SRIVATE_KEY);

        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();
        wallet.lock();
    }

    @Test
    public void generateBip44KeyPair() {

        String mnemonic = "spider elbow fossil truck deal circle divert sleep safe report laundry above";
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        String seedStr = BytesUtils.toHexString(seed);
        assertEquals(
                "f0d2ab78b96acd147119abad1cd70eb4fec4f0e0a95744cf532e6a09347b08101213b4cbf50eada0eb89cba444525fe28e69707e52aa301c6b47ce1c5ef82eb5",
                seedStr);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        assertEquals(
                "xprv9s21ZrQH143K2yA9Cdad5gjqHRC7apVUgEyYq5jXeXigDZ3PfEnps44tJprtMXr7PZivEsin6Qrbad7PuiEy4tn5jAEK6A3U46f9KvfRCmD",
                Base58.encode(addChecksum(serializePrivate(masterKeypair))));

        Bip32ECKeyPair bip44Keypair = WalletUtils.generateBip44KeyPair(masterKeypair, 0);

        assertEquals(
                "xprvA3bRNS6bxNHSZQvJrLiPhVePhqy69cdmsJ2oa2XuMcyuiMDn13ZAVsVDWyQRHZLJrQMMs3qUEf6GDarnJpzBKHXVFcLZgvkD9oGDR845BTL",
                Base58.encode(addChecksum(serializePrivate(bip44Keypair))));
        assertEquals(
                "xpub6GammwdVnjqjmtzmxNFQ4db8FsoaZ5MdEWxQNQwWuxWtb9YvYasR3fohNEiSmcG4pzTziN62M3LZvEowb74cgqW78BLZayCgBDRuGH89xni",
                Base58.encode(addChecksum(serializePublic(bip44Keypair))));

        // Verify address according to https://iancoleman.io/bip39/
        Bip32ECKeyPair key = WalletUtils.importMnemonic(wallet, pwd, mnemonic, 0);
        assertEquals("58d246a56a26c31b75c164e8ab45af13028757fb", BytesUtils.toHexString(Keys.toBytesAddress(key.getKeyPair())));
    }

    @Test
    public void generateBip44KeyPairTestNet() {
        String mnemonic =
                "spider elbow fossil truck deal circle divert sleep safe report laundry above";
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        String seedStr = BytesUtils.toHexString(seed);
        assertEquals(
                "f0d2ab78b96acd147119abad1cd70eb4fec4f0e0a95744cf532e6a09347b08101213b4cbf50eada0eb89cba444525fe28e69707e52aa301c6b47ce1c5ef82eb5",
                seedStr);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        assertEquals(
                "xprv9s21ZrQH143K2yA9Cdad5gjqHRC7apVUgEyYq5jXeXigDZ3PfEnps44tJprtMXr7PZivEsin6Qrbad7PuiEy4tn5jAEK6A3U46f9KvfRCmD",
                Base58.encode(addChecksum(serializePrivate(masterKeypair))));

        Bip32ECKeyPair bip44Keypair = WalletUtils.generateBip44KeyPair(masterKeypair, 0);

        assertEquals(
                "xprvA3bRNS6bxNHSZQvJrLiPhVePhqy69cdmsJ2oa2XuMcyuiMDn13ZAVsVDWyQRHZLJrQMMs3qUEf6GDarnJpzBKHXVFcLZgvkD9oGDR845BTL",
                Base58.encode(addChecksum(serializePrivate(bip44Keypair))));
        assertEquals(
                "xpub6GammwdVnjqjmtzmxNFQ4db8FsoaZ5MdEWxQNQwWuxWtb9YvYasR3fohNEiSmcG4pzTziN62M3LZvEowb74cgqW78BLZayCgBDRuGH89xni",
                Base58.encode(addChecksum(serializePublic(bip44Keypair))));
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }

}

