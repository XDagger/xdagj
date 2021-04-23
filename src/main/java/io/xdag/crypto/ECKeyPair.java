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
import io.xdag.utils.Numeric;
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util;
import org.bitcoin.Secp256k1Context;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Objects;

public class ECKeyPair {

    private final BigInteger privateKey;

    /** 非压缩+去前缀0x04 **/
    private final BigInteger publicKey;

    public ECKeyPair(BigInteger privateKey, BigInteger publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public BigInteger getPrivateKey() {
        return privateKey;
    }

    public BigInteger getPublicKey() {
        return publicKey;
    }

    /**
     * Sign a hash with the private key of this key pair.
     *
     * @param transactionHash the hash to sign
     * @return An {@link ECDSASignature} of the hash
     */
    public ECDSASignature sign(byte[] transactionHash) {
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKey, Sign.CURVE);
        signer.init(true, privKey);
        BigInteger[] components = signer.generateSignature(transactionHash);
        return new ECDSASignature(components[0], components[1]).toCanonicalised();
    }

    /**
     * <p>Verifies the given ECDSA signature against the message bytes using the public key bytes.</p>
     *
     * <p>When using native ECDSA verification, data must be 32 bytes, and no element may be
     * larger than 520 bytes.</p>
     *
     * @param data      Hash of the data to verify.
     * @param signature ASN.1 encoded signature.
     * @param pub       The public key bytes to use.
     */
    public static boolean verify(byte[] data, ECDSASignature signature, byte[] pub) {
        if (Secp256k1Context.isEnabled()) {
            try {
                return NativeSecp256k1.verify(data, signature.encodeToDER(), pub);
            } catch (NativeSecp256k1Util.AssertFailException e) {
                throw new RuntimeException("Caught AssertFailException inside secp256k1", e);
            }
        }

        ECDSASigner signer = new ECDSASigner();
        ECPublicKeyParameters params = new ECPublicKeyParameters(Sign.CURVE.getCurve().decodePoint(pub), Sign.CURVE);
        signer.init(false, params);
        try {
            return signer.verifySignature(data, signature.r, signature.s);
        } catch (NullPointerException e) {
            // Bouncy Castle contains a bug that can cause NPEs given specially crafted signatures. Those signatures
            // are inherently invalid/attack sigs so we just fail them here rather than crash the thread.
            throw new RuntimeException("Caught NPE inside bouncy castle", e);
        }
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the public key.
     *
     * @param hash      Hash of the data to verify.
     * @param signature ASN.1 encoded signature.
     */
    public boolean verify(byte[] hash, byte[] signature) {
        return ECKeyPair.verify(hash, ECDSASignature.decodeFromDER(signature), Sign.publicKeyBytesFromPrivate(this.privateKey,false));
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     */
    public boolean verify(byte[] hash, ECDSASignature signature) {
        return ECKeyPair.verify(hash, signature, Sign.publicKeyBytesFromPrivate(this.privateKey,false));
    }

    public static ECKeyPair create(KeyPair keyPair) {
        BCECPrivateKey privateKey = (BCECPrivateKey) keyPair.getPrivate();
        BCECPublicKey publicKey = (BCECPublicKey) keyPair.getPublic();

        BigInteger privateKeyValue = privateKey.getD();

        // Ethereum does not use encoded public keys like bitcoin - see
        // https://en.bitcoin.it/wiki/Elliptic_Curve_Digital_Signature_Algorithm for details
        // Additionally, as the first bit is a constant prefix (0x04) we ignore this value
        byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
        BigInteger publicKeyValue =
                new BigInteger(1, Arrays.copyOfRange(publicKeyBytes, 1, publicKeyBytes.length));

        return new ECKeyPair(privateKeyValue, publicKeyValue);
    }

    public static ECKeyPair create(BigInteger privateKey) {
        return new ECKeyPair(privateKey, Sign.publicKeyFromPrivate(privateKey));
    }

    public static ECKeyPair create(byte[] privateKey) {
        return create(Numeric.toBigInt(privateKey));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ECKeyPair ecKeyPair = (ECKeyPair) o;

        if (!Objects.equals(privateKey, ecKeyPair.privateKey)) {
            return false;
        }

        return Objects.equals(publicKey, ecKeyPair.publicKey);
    }

    @Override
    public int hashCode() {
        int result = privateKey != null ? privateKey.hashCode() : 0;
        result = 31 * result + (publicKey != null ? publicKey.hashCode() : 0);
        return result;
    }


    /**
     *
     * ECKey 存储公钥类型为 非压缩+去前缀（0x04）
     * 验证签名的时候需要获得压缩公钥
     * 添加compressPubKey方法，将非压缩公钥解析成压缩公钥
     *
     * @param pubKey
     * @return
     */
    public static byte[] compressPubKey(BigInteger pubKey) {
        byte pubKeyYPrefix;
        // pubkey 是奇数公钥
        if (pubKey.testBit(0)) {
            pubKeyYPrefix = 0x03;
        } else {
            pubKeyYPrefix = 0x02;
        }
        return BytesUtils.merge(pubKeyYPrefix,BytesUtils.subArray(Numeric.toBytesPadded(pubKey,64),0,32));
    }

}
