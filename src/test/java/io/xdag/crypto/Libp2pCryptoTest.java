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

import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.xdag.utils.Numeric;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class Libp2pCryptoTest {

    private PrivKey libp2pPrivKey;
    private PubKey libp2pPubKey;

    @Before
    public void setUp() {
        libp2pPrivKey = Secp256k1Kt.unmarshalSecp256k1PrivateKey(Numeric.hexStringToByteArray(SampleKeys.PRIVATE_KEY_STRING));
        libp2pPubKey = Secp256k1Kt.unmarshalSecp256k1PublicKey(Numeric.hexStringToByteArray(SampleKeys.PUBLIC_KEY_COMPRESS_STRING));
    }

    @Test
    public void testUnmarshalSecp256k1PrivateKey() {
        assertArrayEquals(libp2pPrivKey.raw(), SampleKeys.KEY_PAIR.getPrivateKey().toByteArray());
    }

    @Test
    public void testUnmarshalSecp256k1PublicKey() {
        assertArrayEquals(libp2pPubKey.raw(), SampleKeys.KEY_PAIR.getCompressPubKeyBytes());
    }
}