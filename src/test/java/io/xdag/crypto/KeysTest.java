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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.xdag.utils.Numeric;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1;
import org.junit.Test;

public class KeysTest {

    private static final byte[] ENCODED;

    static {
        byte[] privateKey = Numeric.hexStringToByteArray(SampleKeys.PRIVATE_KEY_STRING);
        byte[] publicKey = Numeric.hexStringToByteArray(SampleKeys.PUBLIC_KEY_STRING);
        ENCODED = Arrays.copyOf(privateKey, privateKey.length + publicKey.length);
        System.arraycopy(publicKey, 0, ENCODED, privateKey.length, publicKey.length);
    }

    @Test
    public void testCreateSecp256k1KeyPair() throws Exception {
        KeyPair keyPair = Keys.createSecp256k1KeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        assertNotNull(privateKey);
        assertNotNull(publicKey);

        assertEquals(privateKey.getEncoded().length, (144));
        assertEquals(publicKey.getEncoded().length, (88));
    }

    @Test
    public void testCreateEcKeyPair() {
        SECP256K1.KeyPair key = Keys.createEcKeyPair();
        assertEquals(key.publicKey().bytes().toUnsignedBigInteger().signum(), (1));
        assertEquals(key.secretKey().bytes().toUnsignedBigInteger().signum(), (1));
    }

    @Test
    public void testTransform() {
        for (int i = 0; i < 1000; i++) {
            SECP256K1.KeyPair key = Keys.createEcKeyPair();
            assertEquals(Bytes.wrap(key.publicKey().asEcPoint().getEncoded(true)),
                    Bytes.wrap(Sign.publicKeyBytesFromPrivate(key.secretKey().bytes().toUnsignedBigInteger(), true)));
            assertEquals(Bytes.wrap(key.publicKey().asEcPoint().getEncoded(false)),
                    Bytes.wrap(Sign.publicKeyBytesFromPrivate(key.secretKey().bytes().toUnsignedBigInteger(), false)));
        }
    }

}
