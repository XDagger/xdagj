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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Cryptographic hash functions. */
public class Hash {
    private Hash() {}

    /**
     * Generates a digest for the given {@code input}.
     *
     * @param input The input to digest
     * @param algorithm The hash algorithm to use
     * @return The hash value for the given input
     * @throws RuntimeException If we couldn't find any provider for the given algorithm
     */
    public static byte[] hash(byte[] input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't find a " + algorithm + " provider", e);
        }
    }

    /**
     * Sha-256 hash function.
     *
     * @param hexInput hex encoded input data with optional 0x prefix
     * @return hash value as hex encoded string
     */
    public static String sha256(String hexInput) {
        Bytes32 result = sha256(Bytes.fromHexString(hexInput));
        return result.toHexString();
    }

    /**
     * Generates SHA-256 digest for the given {@code input}.
     *
     * @param input The input to digest
     * @return The hash value for the given input
     * @throws RuntimeException If we couldn't find any SHA-256 provider
     */
    public static Bytes32 sha256(Bytes input) {
        return Bytes32.wrap(newDigest().digest(input.toArray()));
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Bytes32 hashTwice(Bytes input) {
        return sha256(sha256(input));
    }

    /** MessageDigest not thread safe */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Can't happen.
            throw new RuntimeException(e);
        }
    }

    public static byte[] hmacSha512(byte[] key, byte[] input) {
        HMac hMac = new HMac(new SHA512Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(input, 0, input.length);
        byte[] out = new byte[64];
        hMac.doFinal(out, 0);
        return out;
    }

    public static byte[] sha256hash160(Bytes input) {
        Bytes32 sha256 = sha256(input);
        return ripemd160(sha256.toArray());
    }

    public static byte[] ripemd160(byte[] data) {
        Digest digest = new RIPEMD160Digest();
        byte[] buffer = new byte[digest.getDigestSize()];
        digest.update(data, 0, data.length);
        digest.doFinal(buffer, 0);
        return buffer;
    }

    /**
     * Computes the Keccak-256 hash digest.
     *
     * @param input
     *            the input data
     * @return a 32 bytes digest
     */
    public static byte[] keccak256(byte[] input) {
        Keccak.Digest256 digest = new Keccak.Digest256();
        digest.update(input);
        return digest.digest();
    }

    /**
     * Calculates RIGTMOST160(KECCAK256(input)). This is used in address
     * calculations. *
     *
     * @param input
     *            data
     * @return 20 right bytes of the hash keccak of the data
     */
    public static byte[] sha3omit12(byte[] input) {
        byte[] hash = keccak256(input);
        return Arrays.copyOfRange(hash, 12, hash.length);
    }

    /**
     * Computes the address of a deployed contract.
     *
     * @param address
     *            the sender's address
     * @param nonce
     *            the sender's nonce
     * @return an 20 bytes array
     * @implNote the implementation is slightly different from Ethereum's specs;
     *           we're not using RLP codec to encode the address and nonce.
     */
    public static byte[] calcNewAddress(byte[] address, long nonce) {
        ByteBuffer buffer = ByteBuffer.allocate(20 + 8);
        buffer.put(address).putLong(nonce);
        byte[] keccak256 = keccak256(buffer.array());
        return Arrays.copyOfRange(keccak256, 12, 32);
    }

    /**
     * The way to calculate new address inside ethereum for
     * {@link io.xdag.evm.OpCode#CREATE2} sha3(0xff ++ msg.sender ++ salt ++
     * sha3(init_code)))[12:]
     *
     * @param senderAddr
     *            - creating address
     * @param initCode
     *            - contract init code
     * @param salt
     *            - salt to make different result addresses
     * @return new address
     */
    public static byte[] calcSaltAddress(byte[] senderAddr, byte[] initCode, byte[] salt) {
        // 1 - 0xff length, 32 bytes - keccak-256
        byte[] data = new byte[1 + senderAddr.length + salt.length + 32];
        data[0] = (byte) 0xff;
        int currentOffset = 1;
        System.arraycopy(senderAddr, 0, data, currentOffset, senderAddr.length);
        currentOffset += senderAddr.length;
        System.arraycopy(salt, 0, data, currentOffset, salt.length);
        currentOffset += salt.length;
        byte[] sha3InitCode = keccak256(initCode);
        System.arraycopy(sha3InitCode, 0, data, currentOffset, sha3InitCode.length);
        return sha3omit12(data);
    }

}

