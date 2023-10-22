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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Cryptographic hash functions.
 */
public class Hash {

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

    public static byte[] h256(byte[] input) {
        MessageDigest digest = newDigest();

        return digest.digest(input);
    }

//    public static byte[] randomXHash(byte[] input, RandomX randomX) {
//        randomX.randomXBlockHash()
//    }

    /**
     * Merge two byte arrays and compute the 256-bit hash.
     */
    public static byte[] h256(byte[] one, byte[] two) {
        byte[] all = new byte[one.length + two.length];
        System.arraycopy(one, 0, all, 0, one.length);
        System.arraycopy(two, 0, all, one.length, two.length);

        return Hash.h256(all);
    }


    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }

    public static Bytes32 hashTwice(Bytes input) {
        return sha256(sha256(input));
    }

    public static byte[] hashTwice(byte[] input) {
        MessageDigest digest = newDigest();
        digest.update(input);
        return digest.digest(digest.digest());
    }

    public static Bytes32 sha256(Bytes input) {
        return org.hyperledger.besu.crypto.Hash.sha256(input);
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
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256.toArray(), 0, sha256.size());
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }

}

