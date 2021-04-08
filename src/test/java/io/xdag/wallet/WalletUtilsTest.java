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

import io.xdag.crypto.*;
import io.xdag.utils.Numeric;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;




import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.xdag.crypto.Hash.sha256;
import static io.xdag.crypto.SampleKeys.CREDENTIALS;
import static io.xdag.crypto.SampleKeys.KEY_PAIR;
import static io.xdag.crypto.SampleKeys.MNEMONIC;
import static io.xdag.crypto.SampleKeys.PASSWORD;
import static io.xdag.wallet.WalletUtils.isValidAddress;
import static io.xdag.wallet.WalletUtils.isValidPrivateKey;

public class WalletUtilsTest {

    private File tempDir;

    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Before
    public void setUp() throws Exception {
        tempDir = createTempDir();
    }

    @After
    public void tearDown() throws Exception {
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
    }

    @Test
    public void testGenerateBip39Wallets() throws Exception {
        Bip39Wallet wallet = WalletUtils.generateBip39Wallet(PASSWORD, tempDir);
        byte[] seed = MnemonicUtils.generateSeed(wallet.getMnemonic(), PASSWORD);
        Credentials credentials = Credentials.create(ECKeyPair.create(sha256(seed)));

        assertEquals(credentials, WalletUtils.loadBip39Credentials(PASSWORD, wallet.getMnemonic()));
    }

    @Test
    public void testGenerateBip39WalletFromMnemonic() throws Exception {
        Bip39Wallet wallet =
                WalletUtils.generateBip39WalletFromMnemonic(PASSWORD, MNEMONIC, tempDir);
        byte[] seed = MnemonicUtils.generateSeed(wallet.getMnemonic(), PASSWORD);
        Credentials credentials = Credentials.create(ECKeyPair.create(sha256(seed)));

        assertEquals(credentials, WalletUtils.loadBip39Credentials(PASSWORD, wallet.getMnemonic()));
    }

    @Test
    public void testGenerateFullNewWalletFile() throws Exception {
        String fileName = WalletUtils.generateFullNewWalletFile(PASSWORD, tempDir);
        testGeneratedNewWalletFile(fileName);
    }

    @Test
    public void testGenerateNewWalletFile() throws Exception {
        String fileName = WalletUtils.generateNewWalletFile(PASSWORD, tempDir);
        testGeneratedNewWalletFile(fileName);
    }

    @Test
    public void testGenerateLightNewWalletFile() throws Exception {
        String fileName = WalletUtils.generateLightNewWalletFile(PASSWORD, tempDir);
        testGeneratedNewWalletFile(fileName);
    }

    private void testGeneratedNewWalletFile(String fileName) throws Exception {
        WalletUtils.loadCredentials(PASSWORD, new File(tempDir, fileName));
    }

    @Test
    public void testGenerateFullWalletFile() throws Exception {
        String fileName = WalletUtils.generateWalletFile(PASSWORD, KEY_PAIR, tempDir, true);
        testGenerateWalletFile(fileName);
    }

    @Test
    public void testGenerateLightWalletFile() throws Exception {
        String fileName = WalletUtils.generateWalletFile(PASSWORD, KEY_PAIR, tempDir, false);
        testGenerateWalletFile(fileName);
    }

    private void testGenerateWalletFile(String fileName) throws Exception {
        Credentials credentials =
                WalletUtils.loadCredentials(PASSWORD, new File(tempDir, fileName));

        assertEquals(credentials, (CREDENTIALS));
    }

    @Test
    public void testLoadCredentialsFromFile() throws Exception {
        Credentials credentials =
                WalletUtils.loadCredentials(
                        PASSWORD,
                        new File(
                                WalletUtilsTest.class
                                        .getResource(
                                        "/keyfiles/UTC--2021-04-08T13-00-52.556433000Z--a15339b55796b3585127c180fd4cbc54612122cf.json")
                                        .getFile()));

        assertEquals(credentials, (CREDENTIALS));
    }

    @Test
    public void testLoadCredentialsFromString() throws Exception {
        Credentials credentials =
                WalletUtils.loadCredentials(
                        PASSWORD,
                        WalletUtilsTest.class
                                .getResource(
                                "/keyfiles/UTC--2021-04-08T13-00-52.556433000Z--a15339b55796b3585127c180fd4cbc54612122cf.json")
                                .getFile());

        assertEquals(credentials, (CREDENTIALS));
    }

    @Test
    public void testLoadCredentialsMyEtherWallet() throws Exception {
        Credentials credentials =
                WalletUtils.loadCredentials(
                        PASSWORD,
                        new File(
                                WalletUtilsTest.class
                                        .getResource(
                                        "/keyfiles/UTC--2021-04-08T13-00-52.556433000Z--a15339b55796b3585127c180fd4cbc54612122cf.json")
                                        .getFile()));

        assertEquals(
                credentials,
                (Credentials.create(
                        "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6")));
    }

    @Test
    public void testLoadJsonCredentials() throws Exception {
        Credentials credentials =
                WalletUtils.loadJsonCredentials(
                        PASSWORD,
                        convertStreamToString(
                                WalletUtilsTest.class.getResourceAsStream(
                                        "/keyfiles/UTC--2021-04-08T13-00-52.556433000Z--a15339b55796b3585127c180fd4cbc54612122cf.json")));

        assertEquals(credentials, (CREDENTIALS));
    }

    @Test
    public void testGetDefaultKeyDirectory() {
        assertTrue(
                WalletUtils.getDefaultKeyDirectory("Mac OS X")
                        .endsWith(
                                String.format(
                                        "%sLibrary%sXdag", File.separator, File.separator)));
        assertTrue(
                WalletUtils.getDefaultKeyDirectory("Windows")
                        .endsWith(String.format("%sXdag", File.separator)));
        assertTrue(
                WalletUtils.getDefaultKeyDirectory("Linux")
                        .endsWith(String.format("%s.xdag", File.separator)));
    }

    @Test
    public void testGetTestnetKeyDirectory() {
        assertTrue(
                WalletUtils.getMainnetKeyDirectory()
                        .endsWith(String.format("%skeystore", File.separator)));
        assertTrue(
                WalletUtils.getTestnetKeyDirectory()
                        .endsWith(
                                String.format(
                                        "%stestnet%skeystore", File.separator, File.separator)));
    }

    static File createTempDir() throws Exception {
        return Files.createTempDirectory(WalletUtilsTest.class.getSimpleName() + "-testkeys")
                .toFile();
    }

    @Test
    public void testIsValidPrivateKey() {
        assertTrue(isValidPrivateKey(SampleKeys.PRIVATE_KEY_STRING));
        assertTrue(isValidPrivateKey(Numeric.prependHexPrefix(SampleKeys.PRIVATE_KEY_STRING)));

        assertFalse(isValidPrivateKey(""));
        assertFalse(isValidPrivateKey(SampleKeys.PRIVATE_KEY_STRING + "a"));
        assertFalse(isValidPrivateKey(SampleKeys.PRIVATE_KEY_STRING.substring(1)));
    }

    @Test
    public void testIsValidAddress() {
        assertTrue(isValidAddress(SampleKeys.ADDRESS));
        assertTrue(isValidAddress(SampleKeys.ADDRESS_NO_PREFIX));

        assertFalse(isValidAddress(""));
        assertFalse(isValidAddress(SampleKeys.ADDRESS + 'a'));
        assertFalse(isValidAddress(SampleKeys.ADDRESS.substring(1)));
    }

//    public static void main(String[] args) throws Exception {
//        System.out.println(WalletUtils.generateWalletFile(PASSWORD, KEY_PAIR, new File("/Users/reymondtu/git/xdagj/"), true));
//    }
}

