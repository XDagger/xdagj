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

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.SampleKeys;
import io.xdag.utils.Numeric;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class WalletTest {

    @Test
    public void testCreateStandard() throws Exception {
        testCreate(Wallet.createStandard(SampleKeys.PASSWORD, SampleKeys.KEY_PAIR));
    }

    @Test
    public void testCreateLight() throws Exception {
        testCreate(Wallet.createLight(SampleKeys.PASSWORD, SampleKeys.KEY_PAIR));
    }

    private void testCreate(WalletFile walletFile) throws Exception {
        assertEquals(walletFile.getAddress(), (SampleKeys.ADDRESS_NO_PREFIX));
    }

    @Test
    public void testEncryptDecryptStandard() throws Exception {
        testEncryptDecrypt(Wallet.createStandard(SampleKeys.PASSWORD, SampleKeys.KEY_PAIR));
    }

    @Test
    public void testEncryptDecryptLight() throws Exception {
        testEncryptDecrypt(Wallet.createLight(SampleKeys.PASSWORD, SampleKeys.KEY_PAIR));
    }

    private void testEncryptDecrypt(WalletFile walletFile) throws Exception {
        assertEquals(Wallet.decrypt(SampleKeys.PASSWORD, walletFile), (SampleKeys.KEY_PAIR));
    }

    @Test
    public void testDecryptScrypt() throws Exception {
        WalletFile walletFile = load(SCRYPT);
        ECKeyPair ecKeyPair = Wallet.decrypt(PASSWORD, walletFile);
        assertEquals(Numeric.toHexStringNoPrefix(ecKeyPair.getPrivateKey()), (SECRET));
    }

    @Test
    public void testGenerateRandomBytes() {
        assertArrayEquals(Wallet.generateRandomBytes(0), (new byte[] {}));
        assertEquals(Wallet.generateRandomBytes(10).length, (10));
    }

    private WalletFile load(String source) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(source, WalletFile.class);
    }

    private static final String PASSWORD = "Insecure Pa55w0rd";
    private static final String SECRET =
            "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

    private static final String SCRYPT =
            "{\n"
                    + "    \"crypto\" : {\n"
                    + "        \"cipher\" : \"aes-128-ctr\",\n"
                    + "        \"cipherparams\" : {\n"
                    + "            \"iv\" : \"525d33d781892fc3c743520a248c9cd5\"\n"
                    + "        },\n"
                    + "        \"ciphertext\" : \"3c56840c492c27efeb6ded513b5816996ae10589bbd9c9662b3f1e6158729b98\",\n"
                    + "        \"kdf\" : \"scrypt\",\n"
                    + "        \"kdfparams\" : {\n"
                    + "            \"dklen\" : 32,\n"
                    + "            \"n\" : 262144,\n"
                    + "            \"r\" : 8,\n"
                    + "            \"p\" : 1,\n"
                    + "            \"salt\" : \"08cbb63ad8140ea2a6dedd69a723d668e83f950008049601f726660d6d50c2c8\"\n"
                    + "        },\n"
                    + "        \"mac\" : \"4691e03401d5b095a16c20cf185628cfa2d0b159021d7bc3fccac6478ce21c32\"\n"
                    + "    },\n"
                    + "    \"id\" : \"18153e96-657a-498e-a954-cba7e85d47b9\",\n"
                    + "    \"version\" : 3\n"
                    + "}";

}

