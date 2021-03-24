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
import io.xdag.crypto.bip32.DeterministicKey;
import io.xdag.crypto.bip32.DeterministicSeed;
import io.xdag.crypto.bip44.HDKeyDerivation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class WalletUtilsTest {

    public static final String PASSWORD = "xdag to the moon!";
    public static final String MNEMONIC = "admit plug canoe inside want give rhythm easily orphan sun crystal memory";
    public static final Config mainnetConfig = new Config();
    public static final Config testnetConfig = new Config();

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Before
    public void setUp() {
        mainnetConfig.dumpedPrivateKeyHeader = 128;
        mainnetConfig.addressHeader = 0;

        testnetConfig.dumpedPrivateKeyHeader = 239;
        testnetConfig.addressHeader = 111;
    }

    @Test
    public void testGenerateBip39Wallets() throws Exception {
        Bip39Wallet wallet = WalletUtils.generateBip39Wallet(mainnetConfig, PASSWORD, root.newFolder());
        System.out.println(wallet.toString());
        DeterministicSeed seed = new DeterministicSeed(wallet.getMnemonic(), null, "", 0);
        DeterministicKey key = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        key.getChainCode();
        assertNotNull(key.getPrivKey());
        assertNotNull(key.getPubKey());
    }

    @Test
    public void testGenerateBip39WalletFromMnemonic() throws Exception {
        Bip39Wallet wallet = WalletUtils.generateBip39WalletFromMnemonic(mainnetConfig, PASSWORD, MNEMONIC, root.newFolder());
        DeterministicSeed seed1 = new DeterministicSeed(wallet.getMnemonic(), null, "", 0);
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey(seed1.getSeedBytes());
        DeterministicSeed seed2 = new DeterministicSeed(MNEMONIC, null, "", 0);
        DeterministicKey key2 = HDKeyDerivation.createMasterPrivateKey(seed2.getSeedBytes());
        assertEquals(key1.getPrivKey(), key2.getPrivKey());
        assertArrayEquals(key1.getPubKey(), key2.getPubKey());
    }

    @Test
    public void testGenerateFullNewWalletFile() throws Exception {
        File destinationDirectory = root.newFolder();
        String fileName = WalletUtils.generateFullNewWalletFile(mainnetConfig, PASSWORD, destinationDirectory);
        testGeneratedNewWalletFile(destinationDirectory, fileName);
    }

    @Test
    public void testGenerateNewWalletFile() throws Exception {
        File destinationDirectory = root.newFolder();
        String fileName = WalletUtils.generateNewWalletFile(mainnetConfig, PASSWORD, destinationDirectory);
        testGeneratedNewWalletFile(destinationDirectory, fileName);
    }

    private void testGeneratedNewWalletFile(File directory, String fileName) throws Exception {
        WalletUtils.loadECKey(PASSWORD, new File(directory, fileName));
    }

}