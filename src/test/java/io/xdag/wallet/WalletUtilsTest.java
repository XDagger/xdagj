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

import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.*;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MnemonicUtils;
import io.xdag.utils.WalletUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Base58;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Collections;

import static io.xdag.crypto.Bip32Test.*;
import static io.xdag.utils.WalletUtils.checkAddress;
import static org.junit.Assert.*;

public class WalletUtilsTest {

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
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);

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
        assertEquals("6a52a623fc36974cb3c67c3558694584eb39008a", BytesUtils.toHexString(Keys.toBytesAddress(key.getKeyPair())));
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
    @Test
    public void testCheckIsAddress() {
        String walletAddress="KD77RGFihFaqrJQrKK8MJ21hocJeq32Pf";
        assertTrue(io.xdag.crypto.Base58.checkAddress(walletAddress));
    }
    @Test
    public void testHashlowIsAddress(){
        Bytes32 addressHashlow1 = Bytes32.fromHexString(
                "0x00000000000000000007dcdf530ce2d6db89e6ce126a192c24813e9b3208abcf");//not a wallet address hash
        Bytes32 addressHashlow2 = Bytes32.fromHexString(
                "0x000000000000000046a2a0fe035c413d92be9c79a11cfc3695780f6500000000");//a wallet address hash
        assertNotEquals(0, addressHashlow1.slice(28, 4).toInt());
        assertEquals(0,addressHashlow2.slice(28,4).toInt());
        assertFalse(checkAddress(addressHashlow1));
        assertTrue(checkAddress(addressHashlow2));
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }

}

