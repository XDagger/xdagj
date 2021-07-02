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
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class WalletTest {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private String pwd;
    private Wallet wallet;
    private Config config;

    @Before
    public void setUp() {
        pwd = "password";
        config = new DevnetConfig();
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        SECP256K1.KeyPair key = SampleKeys.KEY_PAIR;
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();
        wallet.lock();
    }

    @Test
    public void testGetters() {
        wallet.unlock(pwd);
        assertEquals(pwd, wallet.getPassword());
    }

    @Test
    public void testUnlock() {
        assertFalse(wallet.isUnlocked());

        wallet.unlock(pwd);
        assertTrue(wallet.isUnlocked());

        assertEquals(1, wallet.getAccounts().size());
    }

    @Test
    public void testLock() {
        wallet.unlock(pwd);
        wallet.lock();
        assertFalse(wallet.isUnlocked());
    }

    @Test
    public void testAddAccounts() {
        wallet.unlock(pwd);
        wallet.setAccounts(Collections.emptyList());
        SECP256K1.KeyPair key1 = Keys.createEcKeyPair();
        SECP256K1.KeyPair key2 = Keys.createEcKeyPair();
        wallet.addAccounts(Arrays.asList(key1, key2));
        List<SECP256K1.SecretKey> accounts = wallet.getAccounts();
        SECP256K1.SecretKey k1 = accounts.get(0);
        SECP256K1.SecretKey k2 = accounts.get(1);
        assertEquals(k1, key1.secretKey());
        assertEquals(k2, key2.secretKey());
    }

    @Test
    public void testFlush() throws InterruptedException {
        File file = wallet.getFile();
        long sz = wallet.getFile().length();
        Thread.sleep(500);

        wallet.unlock(pwd);
        wallet.setAccounts(Collections.emptyList());
        assertEquals(sz, file.length());

        wallet.flush();
        assertTrue(file.length() < sz);
    }

    @Test
    public void testChangePassword() {
        String pwd2 = "passw0rd2";

        wallet.unlock(pwd);
        wallet.changePassword(pwd2);
        wallet.flush();
        wallet.lock();

        assertFalse(wallet.unlock(pwd));
        assertTrue(wallet.unlock(pwd2));
    }

    @Test
    public void testAddAccountRandom() {
        wallet.unlock(pwd);
        int oldAccountSize = wallet.getAccounts().size();
        wallet.addAccountRandom();
        assertEquals(oldAccountSize + 1, wallet.getAccounts().size());
    }

    @Test
    public void testRemoveAccount() {
        wallet.unlock(pwd);
        int oldAccountSize = wallet.getAccounts().size();
        SECP256K1.KeyPair key = Keys.createEcKeyPair();
        wallet.addAccount(key.secretKey());
        assertEquals(oldAccountSize + 1, wallet.getAccounts().size());
        wallet.removeAccount(key);
        assertEquals(oldAccountSize, wallet.getAccounts().size());
        wallet.addAccount(key.secretKey());
        assertEquals(oldAccountSize + 1, wallet.getAccounts().size());
        wallet.removeAccount(Keys.getAddress(key.publicKey()));
        assertEquals(oldAccountSize, wallet.getAccounts().size());
    }

    @Test
    public void testInitializeHdWallet() {
        wallet.initializeHdWallet(SampleKeys.MNEMONIC);
        assertEquals(0, wallet.getNextAccountIndex());
        assertEquals(SampleKeys.MNEMONIC,wallet.getMnemonicPhrase());
    }

    @Test
    public void testAddAccountWithNextHdKey() {
        wallet.unlock(pwd);
        wallet.initializeHdWallet(SampleKeys.MNEMONIC);
        int hdkeyCount = 5;
        for(int i = 0; i < hdkeyCount; i++) {
            wallet.addAccountWithNextHdKey();
        }
        assertEquals(hdkeyCount, wallet.getNextAccountIndex());
    }

    @Test
    public void testHDKeyRecover() {
        wallet.unlock(pwd);
        wallet.initializeHdWallet(SampleKeys.MNEMONIC);
        List<SECP256K1.SecretKey> keyPairList1 = new ArrayList<>();
        int hdkeyCount = 5;
        for(int i = 0; i < hdkeyCount; i++) {
            SECP256K1.SecretKey key = wallet.addAccountWithNextHdKey();
            keyPairList1.add(key);
        }

        assertEquals(hdkeyCount, wallet.getNextAccountIndex());
        Wallet wallet2 = new Wallet(config);
        // use different password and same mnemonic
        wallet2.unlock(pwd + pwd);
        wallet2.initializeHdWallet(SampleKeys.MNEMONIC);
        List<SECP256K1.SecretKey> keyPairList2 = new ArrayList<>();
        for(int i = 0; i < hdkeyCount; i++) {
            SECP256K1.SecretKey key = wallet2.addAccountWithNextHdKey();
            keyPairList2.add(key);
        }
        assertTrue(CollectionUtils.isEqualCollection(keyPairList1, keyPairList2));
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }
}