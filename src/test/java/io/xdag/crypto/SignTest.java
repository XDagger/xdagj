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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.xdag.utils.BytesUtils;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECPoint;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.junit.Before;
import org.junit.Test;

public class SignTest {

    private static final byte[] TEST_MESSAGE = "A test message".getBytes(StandardCharsets.UTF_8);

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

//    @Test
//    public void testToCanonical(){
//        String encode = "00000000000000002163550000000000a40348a18a0100000000000000000000"+
//                "7adbde3a13f1dbc6e8ae2a54a2b27800cd285cc442a4ceb19a99999999030000"+
//                "cfe8b35209f43803be371314bd16a84b7c82bae24f33d1c29a99999999030000"+
//                "6719c111daa88a9af4821acad7f9ff48b57958d19d77ee736f935c7f74af5a28"+
//                "7240fe49a3115abdacd24c52ab7db33ebe7880635bf786f73f92196fc398e6ea"+
//                "a2079fd4149c6ad272384eda7d97c93fdd292824a1280bc0af708338fde9d80f"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000"+
//                "0000000000000000000000000000000000000000000000000000000000000000";
//        Block block = new Block(new XdagBlock(Bytes.fromHexString(encode).mutableCopy()));
//        SECPPublicKey publicKey = block.getPubKeys().get(0);
//        Bytes digest = block.getSubRawData(block.getOutsigIndex()-2);
//        byte[] pubkeyBytes = publicKey.asEcPoint(Sign.CURVE).getEncoded(true);
//        Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest, Bytes.wrap(pubkeyBytes)));
//
//        assertTrue(Sign.SECP256K1.verify(hash, Sign.toCanonical(block.getOutsig()), publicKey));
//        assertFalse(Sign.SECP256K1.verify(hash, block.getOutsig(), publicKey));
//    }
}

