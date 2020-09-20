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

import io.xdag.utils.BytesUtils;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;

import static io.xdag.crypto.ECKey.ECDSASignature;
import static org.junit.Assert.*;

public class ECKeyTest {

    private static final SecureRandom secureRandom = new SecureRandom();

    private String privString = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
    private byte[] privKey = Hex.decode(privString);
    private BigInteger privateKey = new BigInteger(privString, 16);

    private String pubString = "040947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad75aa17564ae80a20bb044ee7a6d903e8e8df624b089c95d66a0570f051e5a05b";
    private String compressedPubString = "030947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad";
    private byte[] pubKey = Hex.decode(pubString);
    private byte[] compressedPubKey = Hex.decode(compressedPubString);
    private String address = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";

    private String exampleMessage = "This is an example of a signed message.";
    private String sigBase64 = "HNLOSI9Nop5o8iywXKwbGbdd8XChK0rRvdRTG46RFcb7dcH+UKlejM/8u1SCoeQvu91jJBMd/nXDs7f5p8ch7Ms=";
    private String signatureHex = "d2ce488f4da29e68f22cb05cac1b19b75df170a12b4ad1bdd4531b8e9115c6fb75c1fe50a95e8ccffcbb5482a1e42fbbdd6324131dfe75c3b3b7f9a7c721eccb01";


    @Test
    public void generKeyTest() {
        String prvkey = "93c930b7a7260f4f896561417fbe54ff16b9e49112fb41a69f230c179f2048d0";
        String pubkey = "04ee41b7d17d8b8cfd8a8a6d7bb25f575cc627da758be3cabcbd7909619cd64a453d1da60294c1037322f5fbf572725d89af4a7cf1dfa223b04cc4d85c611f5b1f";
        String pubkeyCompress = "03ee41b7d17d8b8cfd8a8a6d7bb25f575cc627da758be3cabcbd7909619cd64a45";
        ECKey ecKey = ECKey.fromPrivate(Hex.decode(prvkey));
        assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPubKey()), pubkey));
        assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPrivKeyBytes()), prvkey));
        assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPubKeybyCompress()), pubkeyCompress));
    }


    @Test
    public void testHashCode() {
        assertEquals(-351262686, ECKey.fromPrivate(privateKey).hashCode());
    }

    @Test
    public void testECKey() {
        ECKey key = new ECKey();
        assertTrue(key.isPubKeyCanonical());
        assertNotNull(key.getPubKey());
        assertNotNull(key.getPrivKeyBytes());
//        log.debug(Hex.toHexString(key.getPrivKeyBytes()) + " :Generated privkey");
//        log.debug(Hex.toHexString(key.getPubKey()) + " :Generated pubkey");
    }

    @Test
    public void testFromPrivateKey() {
        ECKey key = ECKey.fromPrivate(privateKey);
        assertTrue(key.isPubKeyCanonical());
        assertTrue(key.hasPrivKey());
        assertArrayEquals(pubKey, key.getPubKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPrivatePublicKeyBytesNoArg() {
        new ECKey((BigInteger) null, null);
        fail("Expecting an IllegalArgumentException for using only null-parameters");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPrivateKey() throws Exception {
        new ECKey(
                Security.getProvider("SunEC"),
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate(),
                ECKey.fromPublicOnly(pubKey).getPubKeyPoint());
        fail("Expecting an IllegalArgumentException for using an non EC private key");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPrivateKey2() throws Exception {
        ECKey.fromPrivate(new byte[32]);
        fail("Expecting an IllegalArgumentException for using an non EC private key");
    }

    @Test
    public void testIsPubKeyOnly() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        assertTrue(key.isPubKeyCanonical());
        assertTrue(key.isPubKeyOnly());
        assertArrayEquals(key.getPubKey(), pubKey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSignIncorrectInputSize() {
        ECKey key = new ECKey();
        String message = "The quick brown fox jumps over the lazy dog.";
        ECKey.ECDSASignature sig = key.doSign(message.getBytes());
        fail("Expecting an IllegalArgumentException for a non 32-byte input");
    }

    @Test(expected = ECKey.MissingPrivateKeyException.class)
    public void testSignWithPubKeyOnly() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        String message = "The quick brown fox jumps over the lazy dog.";
        byte[] input = Sha256Hash.hashTwice((message.getBytes()));
        ECKey.ECDSASignature sig = key.doSign(input);
        fail("Expecting an MissingPrivateKeyException for a public only ECKey");
    }

    @Test(expected = SignatureException.class)
    public void testBadBase64Sig() throws SignatureException {
        byte[] messageHash = new byte[32];
        ECKey.signatureToKey(messageHash, "This is not valid Base64!");
        fail("Expecting a SignatureException for invalid Base64");
    }

    @Test(expected = SignatureException.class)
    public void testInvalidSignatureLength() throws SignatureException {
        byte[] messageHash = new byte[32];
        ECKey.signatureToKey(messageHash, "abcdefg");
        fail("Expecting a SignatureException for invalid signature length");
    }

    @Test
    public void testPublicKeyFromPrivate() {
        byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, false);
        assertArrayEquals(pubKey, pubFromPriv);
    }

    @Test
    public void testPublicKeyFromPrivateCompressed() {
        byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, true);
        assertArrayEquals(compressedPubKey, pubFromPriv);
    }

    @Test
    public void testToString() {
        ECKey key = ECKey.fromPrivate(BigInteger.TEN); // An example private key.
        assertEquals("pub:04a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7", key.toString());
    }

    @Test // result is a point at infinity
    public void testVerifySignature4() {

        byte[] hash = Hex.decode("acb1c19ac0832320815b5e886c6b73ad7d6177853d44b026f2a7a9e11bb899fc");
        byte[] r = Hex.decode("89ea49159b334f9aebbf54481b69d000d285baa341899db355a4030f6838394e");
        byte[] s = Hex.decode("540e9f9fa17bef441e32d98d5f4554cfefdc6a56101352e4b92efafd0d9646e8");
        byte v = (byte) 28;

        ECDSASignature sig = ECKey.ECDSASignature.fromComponents(r, s, v);

        try {
            ECKey.signatureToKey(hash, sig);
            fail("Result is a point at infinity, recovery must fail");
        } catch (SignatureException e) {
        }
    }

    @Test
    public void testIsPubKeyCanonicalCorect() {
        // Test correct prefix 4, right length 65
        byte[] canonicalPubkey1 = new byte[65];
        canonicalPubkey1[0] = 0x04;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey1));
        // Test correct prefix 2, right length 33
        byte[] canonicalPubkey2 = new byte[33];
        canonicalPubkey2[0] = 0x02;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey2));
        // Test correct prefix 3, right length 33
        byte[] canonicalPubkey3 = new byte[33];
        canonicalPubkey3[0] = 0x03;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey3));
    }

    @Test
    public void testIsPubKeyCanonicalWrongLength() {
        // Test correct prefix 4, but wrong length !65
        byte[] nonCanonicalPubkey1 = new byte[64];
        nonCanonicalPubkey1[0] = 0x04;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey1));
        // Test correct prefix 2, but wrong length !33
        byte[] nonCanonicalPubkey2 = new byte[32];
        nonCanonicalPubkey2[0] = 0x02;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey2));
        // Test correct prefix 3, but wrong length !33
        byte[] nonCanonicalPubkey3 = new byte[32];
        nonCanonicalPubkey3[0] = 0x03;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey3));
    }

    @Test
    public void testIsPubKeyCanonicalWrongPrefix() {
        // Test wrong prefix 4, right length 65
        byte[] nonCanonicalPubkey4 = new byte[65];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey4));
        // Test wrong prefix 2, right length 33
        byte[] nonCanonicalPubkey5 = new byte[33];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey5));
        // Test wrong prefix 3, right length 33
        byte[] nonCanonicalPubkey6 = new byte[33];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey6));
    }

    @Test
    public void testGetPrivKeyBytes() {
        ECKey key = new ECKey();
        assertNotNull(key.getPrivKeyBytes());
        assertEquals(32, key.getPrivKeyBytes().length);
    }

    @Test
    public void testEqualsObject() {
        ECKey key0 = new ECKey();
        ECKey key1 = ECKey.fromPrivate(privateKey);
        ECKey key2 = ECKey.fromPrivate(privateKey);

        assertFalse(key0.equals(key1));
        assertTrue(key1.equals(key1));
        assertTrue(key1.equals(key2));
    }

    @Test
    public void testNodeId() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        assertEquals(key, ECKey.fromNodeId(key.getNodeId()));
    }

    @Test
    public void testVerify() {
        ECKey key = ECKey.fromPrivate(privateKey);
        ECDSASignature sign = key.sign(Sha256Hash.hashTwice(exampleMessage.getBytes()));
        assertTrue(ECKey.verify(Sha256Hash.hashTwice(exampleMessage.getBytes()), sign, pubKey));
        for(int i = 0; i < 10000; i++) {
            ECDSASignature testSign = new ECDSASignature(sign.r, sign.s);
            assertTrue(ECKey.verify(Sha256Hash.hashTwice(exampleMessage.getBytes()), testSign, pubKey));
        }
    }

    @Test
    public void testVerify2() {
        String pubkeyStr = "0468e1d91b0bfc1ded5f0887286d196ad04b46b921d7a3272d7a8f95b45cfd9c8bc5a4537b772ff4d56805c28ac9d3e5215d57490c87f007a159f99ed46239bed9";
        String signR = "02e47aea40ac424f0672b25d395268a0a4a6e9864ff7bec255372faabe96e1fe";
        String signS = "2ff7590b29b6d9f24735e98f1ada8047dfce7405e987c4b34ce3323996110cbe";
        String hashStr = "c130d057ccc601c71100b61f7cece9e233f6e5df0e839d930426d316589f5c8d";
        ECKey key = ECKey.fromPublicOnly(Hex.decode(pubkeyStr));
        BigInteger r = BytesUtils.bytesToBigInteger(Hex.decode(signR));
        BigInteger s = BytesUtils.bytesToBigInteger(Hex.decode(signS));
        ECDSASignature sign = new ECDSASignature(r, s);
        assertTrue(key.verify(Hex.decode(hashStr), sign));
    }
}
