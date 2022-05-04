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

import static org.junit.Assert.assertArrayEquals;

import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.xdag.utils.Numeric;
import java.security.Security;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

public class Libp2pCryptoTest {

    static { Security.addProvider(new BouncyCastleProvider());  }

    private PrivKey libp2pPrivKey;
    private PubKey libp2pPubKey;

    @Before
    public void setUp() {
        libp2pPrivKey = Secp256k1Kt
                .unmarshalSecp256k1PrivateKey(Numeric.hexStringToByteArray(SampleKeys.PRIVATE_KEY_STRING));
        libp2pPubKey = Secp256k1Kt
                .unmarshalSecp256k1PublicKey(Numeric.hexStringToByteArray(SampleKeys.PUBLIC_KEY_COMPRESS_STRING));
    }

    @Test
    public void testUnmarshalSecp256k1PrivateKey() {
        Bytes libp2pBytes = Bytes.wrap(libp2pPrivKey.raw()).slice(1, 33 -1);
        assertArrayEquals(libp2pBytes.toArray(), SampleKeys.KEY_PAIR.getPrivateKey().getEncoded());
    }

    @Test
    public void testUnmarshalSecp256k1PublicKey() {
        assertArrayEquals(libp2pPubKey.raw(), SampleKeys.KEY_PAIR.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true));
    }
}