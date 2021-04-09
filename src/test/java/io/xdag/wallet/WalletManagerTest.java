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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static io.xdag.crypto.SampleKeys.PASSWORD;
import static org.junit.Assert.*;


public class WalletManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testCreateBip44Wallet() throws IOException, CipherException {
        Optional<Credentials> c =  WalletManager.createBip44Wallet("xdagTotheMoon", temporaryFolder.newFolder());
        assertNotNull(c.get());
    }

    @Test
    public void testImportBip44WalletFromMnemonic() {
        String mnemonic = "spider elbow fossil truck deal circle divert sleep safe report laundry above";
        Optional<Credentials> c = WalletManager.importBip44WalletFromMnemonic("", mnemonic);
        Credentials credentials = c.get();
        assertEquals("0xddc049a60750affe6f53b1d77208e4108f14d742", credentials.getAddress().toLowerCase());
    }

    @Test
    public void testImportBip44WalletFromKeystore() throws IOException, CipherException {
        InputStream in = WalletUtilsTest.class.getResourceAsStream(
                "/keyfiles/UTC--2021-04-08T13-00-52.556433000Z--a15339b55796b3585127c180fd4cbc54612122cf.json");
        String keystore = WalletUtilsTest.convertStreamToString(in);
        Optional<Credentials> c = WalletManager.importBip44WalletFromKeystore(PASSWORD, keystore);
        assertEquals(c.get(), SampleKeys.CREDENTIALS);
    }
}
