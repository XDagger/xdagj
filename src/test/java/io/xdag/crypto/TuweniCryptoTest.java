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
package io.xdag.crypto;

import static org.junit.Assert.*;

import java.security.Security;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

public class TuweniCryptoTest {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void createKeyPairRandom() {
        SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.random();
        assertNotNull(keyPair);
    }

    @Test
    public void createSecretKeyFromIntegerTest() {
        SECP256K1.SecretKey privKey = SECP256K1.SecretKey.fromInteger(SampleKeys.PRIVATE_KEY);
        assertEquals(privKey.bytes().toUnprefixedHexString(), SampleKeys.PRIVATE_KEY_STRING);
    }

    @Test
    public void createSecretKeyFromBytesTest() {
        SECP256K1.SecretKey privKey = SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(SampleKeys.PRIVATE_KEY_STRING));
        assertEquals(privKey.bytes().toUnprefixedHexString(), SampleKeys.PRIVATE_KEY_STRING);
    }

    @Test
    public void createPublicKeyFromIntegerTest() {
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromInteger(SampleKeys.PUBLIC_KEY);
        assertEquals(publicKey.bytes().toUnprefixedHexString(), SampleKeys.PUBLIC_KEY_STRING);
    }

    @Test
    public void createPublicKeyFromBytesTest() {
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromBytes(Bytes.fromHexString(SampleKeys.PUBLIC_KEY_STRING));
        assertEquals(publicKey.bytes().toUnprefixedHexString(), SampleKeys.PUBLIC_KEY_STRING);
    }

    @Test
    public void createPublicKeyFromSecretKeyTest() {
        SECP256K1.SecretKey privKey = SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(SampleKeys.PRIVATE_KEY_STRING));
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromSecretKey(privKey);
        assertEquals(publicKey.bytes().toUnprefixedHexString(), SampleKeys.PUBLIC_KEY_STRING);
    }

    @Test
    public void createPublicKeyFromHexStringTest() {
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromHexString(SampleKeys.PUBLIC_KEY_STRING);
        assertEquals(publicKey.bytes().toUnprefixedHexString(), SampleKeys.PUBLIC_KEY_STRING);
    }

    @Test
    public void createPublicKeyCompressFromIntegerTest() {
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromInteger(SampleKeys.PUBLIC_KEY);
        Bytes pubBytes = Bytes.wrap(publicKey.asEcPoint().getEncoded(true));
        assertEquals(pubBytes.toUnprefixedHexString(), SampleKeys.PUBLIC_KEY_COMPRESS_STRING);
    }

}
