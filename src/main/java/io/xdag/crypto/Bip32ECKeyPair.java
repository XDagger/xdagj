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

import io.xdag.utils.HashUtils;
import io.xdag.utils.Numeric;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static io.xdag.utils.HashUtils.*;

/**
 * BIP-32 key pair.
 *
 * <p>Adapted from:
 * https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/crypto/DeterministicKey.java
 */
@Getter
public class Bip32ECKeyPair {
    public static final int HARDENED_BIT = 0x80000000;

    private final boolean parentHasPrivate;
    private final int childNumber;
    private final int depth;
    private final byte[] chainCode;
    private final int parentFingerprint;

    private final SECP256K1.KeyPair keyPair;

    public Bip32ECKeyPair(
            BigInteger privateKey,
            BigInteger publicKey,
            int childNumber,
            byte[] chainCode,
            Bip32ECKeyPair parent) {

        this.keyPair = SECP256K1.KeyPair.create(SECP256K1.SecretKey.fromInteger(privateKey), SECP256K1.PublicKey.fromInteger(publicKey));

        this.parentHasPrivate = parent != null && parent.hasPrivateKey();
        this.childNumber = childNumber;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.chainCode = Arrays.copyOf(chainCode, chainCode.length);
        this.parentFingerprint = parent != null ? parent.getFingerprint() : 0;
    }

    public static Bip32ECKeyPair create(BigInteger privateKey, byte[] chainCode) {
        SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromInteger(privateKey);
        return new Bip32ECKeyPair(privateKey,  publicKey.bytes().toUnsignedBigInteger(), 0, chainCode, null);
    }

    public static Bip32ECKeyPair create(byte[] privateKey, byte[] chainCode) {
        return create(Numeric.toBigInt(privateKey), chainCode);
    }

    public static Bip32ECKeyPair generateKeyPair(byte[] seed) {
        byte[] i = HashUtils.hmacSha512("Bitcoin seed".getBytes(), seed);
        byte[] il = Arrays.copyOfRange(i, 0, 32);
        byte[] ir = Arrays.copyOfRange(i, 32, 64);
        Arrays.fill(i, (byte) 0);
        Bip32ECKeyPair keypair = Bip32ECKeyPair.create(il, ir);
        Arrays.fill(il, (byte) 0);
        Arrays.fill(ir, (byte) 0);

        return keypair;
    }

    public static Bip32ECKeyPair deriveKeyPair(Bip32ECKeyPair master, int[] path) {
        Bip32ECKeyPair curr = master;
        if (path != null) {
            for (int childNumber : path) {
                curr = curr.deriveChildKey(childNumber);
            }
        }

        return curr;
    }

    private Bip32ECKeyPair deriveChildKey(int childNumber) {
        if (!hasPrivateKey()) {
            byte[] parentPublicKey = getPublicKeyPoint().getEncoded(true);
            ByteBuffer data = ByteBuffer.allocate(37);
            data.put(parentPublicKey);
            data.putInt(childNumber);
            byte[] i = hmacSha512(getChainCode(), data.array());
            byte[] il = Arrays.copyOfRange(i, 0, 32);
            byte[] chainCode = Arrays.copyOfRange(i, 32, 64);
            Arrays.fill(i, (byte) 0);
            BigInteger ilInt = new BigInteger(1, il);
            Arrays.fill(il, (byte) 0);
            SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromInteger(ilInt);
            ECPoint ki = publicKey.asEcPoint().add(getPublicKeyPoint());
            byte[] kiBytes = ki.getEncoded(true);
            return new Bip32ECKeyPair(null, new BigInteger(1, Arrays.copyOfRange(kiBytes, 1, kiBytes.length)), childNumber, chainCode, this);
        } else {
            ByteBuffer data = ByteBuffer.allocate(37);
            if (isHardened(childNumber)) {
                data.put(getPrivateKeyBytes33());
            } else {
                byte[] parentPublicKey = getPublicKeyPoint().getEncoded(true);
                data.put(parentPublicKey);
            }
            data.putInt(childNumber);
            byte[] i = hmacSha512(getChainCode(), data.array());
            byte[] il = Arrays.copyOfRange(i, 0, 32);
            byte[] chainCode = Arrays.copyOfRange(i, 32, 64);
            Arrays.fill(i, (byte) 0);
            BigInteger ilInt = new BigInteger(1, il);
            Arrays.fill(il, (byte) 0);

            BigInteger privateKey = keyPair.secretKey().bytes().toUnsignedBigInteger().add(ilInt).mod(SECP256K1.Parameters.CURVE.getN());
            SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromInteger(privateKey);

            return new Bip32ECKeyPair(
                    privateKey,
                    publicKey.bytes().toUnsignedBigInteger(),
                    childNumber,
                    chainCode,
                    this);
        }
    }

    private int getFingerprint() {
        byte[] id = getIdentifier();
        return id[3] & 0xFF | (id[2] & 0xFF) << 8 | (id[1] & 0xFF) << 16 | (id[0] & 0xFF) << 24;
    }

    public int getDepth() {
        return depth;
    }

    public int getParentFingerprint() {
        return parentFingerprint;
    }

    public byte[] getChainCode() {
        return chainCode;
    }

    public int getChildNumber() {
        return childNumber;
    }

    private byte[] getIdentifier() {
        return sha256hash160(Bytes.wrap(getPublicKeyPoint().getEncoded(true)));
    }

    public ECPoint getPublicKeyPoint() {
        return Keys.publicPointFromPrivate(keyPair.secretKey().bytes().toUnsignedBigInteger());
    }

    public byte[] getPrivateKeyBytes33() {
        final int numBytes = 33;
        MutableBytes bytes33  = MutableBytes.create(numBytes);
        Bytes32 priv = keyPair.secretKey().bytes();
        bytes33.set(numBytes - priv.size(), priv);
        return bytes33.toArray();
    }

    private boolean hasPrivateKey() {
        return keyPair.secretKey() != null || parentHasPrivate;
    }

    private static boolean isHardened(int a) {
        return (a & HARDENED_BIT) != 0;
    }
}
