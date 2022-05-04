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
import static org.junit.Assert.assertTrue;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import io.xdag.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.bouncycastle.math.ec.ECPoint;
import org.hyperledger.besu.crypto.SECPSignature;
import org.junit.Before;
import org.junit.Test;

public class SignTest {

    private static final byte[] TEST_MESSAGE = "A test message".getBytes();

    @Before
    public void setup() {
    }

    @Test
    public void testPublicKeyFromPrivateKey() {
        assertEquals(Sign.publicKeyFromPrivate(SampleKeys.PRIVATE_KEY), (SampleKeys.PUBLIC_KEY));
    }

    @Test
    public void testPublicKeyFromPrivatePoint() {
        ECPoint point = Sign.publicPointFromPrivate(SampleKeys.PRIVATE_KEY);
        assertEquals(Sign.publicFromPoint(point.getEncoded(false)), (SampleKeys.PUBLIC_KEY));
    }

    @Test
    public void testVerifySpend()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        long n = 1;
        byte[] encoded = new byte[]{0};
        long start1 = 0;
        long start2 = 0;
        for (int i = 0; i < n; i++) {
            KeyPair poolKey = Keys.createEcKeyPair();
            byte[] pubkeyBytes = poolKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
            byte[] digest = BytesUtils.merge(encoded, pubkeyBytes);
            Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
            SECPSignature signature = Sign.SECP256K1.sign(hash, poolKey);

            long first = first(hash.toArray(), signature, poolKey);
            start1 += first;
            long second = second(hash.toArray(), signature, poolKey);
            start2 += second;
        }

    }

    public long first(byte[] hash, SECPSignature sig, KeyPair key) {
        long start = System.currentTimeMillis();
        assertTrue(Sign.SECP256K1.verify(Bytes32.wrap(hash), sig, key.getPublicKey()));
        long end = System.currentTimeMillis();
        return end - start;
    }

    public long second(byte[] hash, SECPSignature sig, KeyPair key) {
        long start = System.currentTimeMillis();
        assertTrue(Sign.SECP256K1.verify(Bytes32.wrap(hash), sig, key.getPublicKey()));
        long end = System.currentTimeMillis();
        return end - start;
    }

}

