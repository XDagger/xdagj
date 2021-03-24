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
package io.xdag.crypto.bip38;

import io.xdag.config.Config;
import io.xdag.crypto.bip32.DumpedPrivateKey;
import io.xdag.crypto.Base58;
import io.xdag.crypto.ECKey;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class DumpedPrivateKeyTest {
    private static final Config mainnetConfig = new Config();
    private static final Config testnetConfig = new Config();

    @Before
    public void setUp() {
        mainnetConfig.dumpedPrivateKeyHeader = 128;
        testnetConfig.dumpedPrivateKeyHeader = 239;
    }

    @Test
    public void checkNetwork() {
        DumpedPrivateKey.fromBase58(mainnetConfig, "5HtUCLMFWNueqN9unpgX2DzjMg6SDNZyKRb8s3LJgpFg5ubuMrk");
    }

    @Test(expected = AddressFormatException.WrongNetwork.class)
    public void checkNetworkWrong() {
        DumpedPrivateKey.fromBase58(testnetConfig, "5HtUCLMFWNueqN9unpgX2DzjMg6SDNZyKRb8s3LJgpFg5ubuMrk");
    }

    @Test
    public void testJavaSerialization() throws Exception {
        DumpedPrivateKey key = new DumpedPrivateKey(mainnetConfig, new ECKey().getPrivKeyBytes(), true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(key);
        DumpedPrivateKey keyCopy = (DumpedPrivateKey) new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()))
                .readObject();
        assertEquals(key.toBase58(), keyCopy.toBase58());
    }

    @Test
    public void cloning() throws Exception {
        DumpedPrivateKey a = new DumpedPrivateKey(mainnetConfig, new ECKey().getPrivKeyBytes(), true);
        // TODO: Consider overriding clone() in DumpedPrivateKey to narrow the type
        DumpedPrivateKey b = (DumpedPrivateKey) a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() {
        String base58 = "5HtUCLMFWNueqN9unpgX2DzjMg6SDNZyKRb8s3LJgpFg5ubuMrk"; // 32-bytes key
        DumpedPrivateKey dumpedPrivateKey = DumpedPrivateKey.fromBase58(mainnetConfig, base58);
        assertFalse(dumpedPrivateKey.isPubKeyCompressed());
        assertEquals(base58, dumpedPrivateKey.toBase58());
    }

    @Test
    public void roundtripBase58_compressed() {
        String base58 = "cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4"; // 33-bytes, compressed == true
        DumpedPrivateKey dumpedPrivateKey = DumpedPrivateKey.fromBase58(testnetConfig, base58);
        assertTrue(dumpedPrivateKey.isPubKeyCompressed());
        assertEquals(base58, dumpedPrivateKey.toBase58());
    }

    @Test(expected = AddressFormatException.class)
    public void roundtripBase58_invalidCompressed() {
        String base58 = "5Kg5shEQWrf1TojaHTdc2kLuz5Mfh4uvp3cYu8uJHaHgfTGUbTD"; // 32-bytes key
        byte[] bytes = Base58.decodeChecked(base58);
        bytes = Arrays.copyOf(bytes, bytes.length + 1); // append a "compress" byte
        bytes[bytes.length - 1] = 0; // set it to false
        base58 = Base58.encode(bytes); // 33-bytes key, compressed == false
        DumpedPrivateKey.fromBase58(mainnetConfig, base58); // fail
    }

    @Test(expected = AddressFormatException.InvalidDataLength.class)
    public void fromBase58_tooShort() {
        String base58 = Base58.encodeChecked(mainnetConfig.dumpedPrivateKeyHeader, new byte[31]);
        DumpedPrivateKey.fromBase58(mainnetConfig, base58);
    }

    @Test(expected = AddressFormatException.InvalidDataLength.class)
    public void fromBase58_tooLong() {
        String base58 = Base58.encodeChecked(mainnetConfig.dumpedPrivateKeyHeader, new byte[34]);
        DumpedPrivateKey.fromBase58(mainnetConfig, base58);
    }

    @Test
    public void roundtripBase58_getKey() {
        ECKey k = new ECKey().decompress();
        assertFalse(k.isCompressed());
        assertEquals(k.getPrivKey(),
                DumpedPrivateKey.fromBase58(testnetConfig, k.getPrivateKeyAsWiF(testnetConfig)).getKey().getPrivKey());
    }

    @Test
    public void roundtripBase58_compressed_getKey() {
        ECKey k = new ECKey();
        assertTrue(k.isCompressed());
        assertEquals(k.getPrivKey(),
                DumpedPrivateKey.fromBase58(testnetConfig, k.getPrivateKeyAsWiF(testnetConfig)).getKey().getPrivKey());
    }
}

