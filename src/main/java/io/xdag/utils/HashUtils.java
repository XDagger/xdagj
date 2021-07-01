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
package io.xdag.utils;

import org.apache.commons.codec.digest.HmacUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.apache.tuweni.crypto.Hash.*;

public final class HashUtils {

    public static byte[] hmacSha512(byte[] key, byte[] input) {
        HMac hMac = new HMac(new SHA512Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(input, 0, input.length);
        byte[] out = new byte[64];
        hMac.doFinal(out, 0);
        return out;
    }

    public static Bytes32 hashTwice(Bytes input) {
        return sha2_256(sha2_256(input));
    }

    public static byte[] sha256hash160(Bytes input) {
        Bytes32 sha256 = sha2_256(input);
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
     * Keccak-256 hash function.
     *
     * @param hexInput hex encoded input data with optional 0x prefix
     * @return hash value as hex encoded string
     */
    public static String sha3(String hexInput) {
        byte[] bytes = Numeric.hexStringToByteArray(hexInput);
        byte[] result = sha3_256(bytes);
        return Numeric.toHexString(result);
    }

    /**
     * Keccak-256 hash function.
     *
     * @param input binary encoded input data
     * @param offset of start of data
     * @param length of data
     * @return hash value
     */
    public static byte[] sha3(byte[] input, int offset, int length) {
        Keccak.DigestKeccak kecc = new Keccak.Digest256();
        kecc.update(input, offset, length);
        return kecc.digest();
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

