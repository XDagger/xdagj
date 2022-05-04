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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

import java.nio.ByteBuffer;

import io.xdag.crypto.Hash;

public final class HashUtils {

    public static Bytes32 hashTwice(Bytes input) {
        return Hash.sha256(Hash.sha256(input));
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
        Bytes32 keccak256 = Hash.sha3(Bytes.wrap(buffer.array()));
        return keccak256.slice(12, 20).toArray();
    }

    public static Bytes sha3omit12(Bytes input) {
        Bytes32 hash = Hash.sha3(input);
        return hash.slice(12);
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
    public static Bytes calcSaltAddress(Bytes senderAddr, Bytes initCode, Bytes salt) {
        // 1 - 0xff length, 32 bytes - keccak-256
//        byte[] data = new byte[1 + senderAddr.length + salt.length + 32];
        MutableBytes data = MutableBytes.create(1 + senderAddr.size() + salt.size() + 32);
//        data[0] = (byte) 0xff;
        data.set(0, (byte) 0xff);

        int currentOffset = 1;
//        System.arraycopy(senderAddr, 0, data, currentOffset, senderAddr.length);
        data.set(currentOffset, senderAddr);
        currentOffset += senderAddr.size();
//        System.arraycopy(salt, 0, data, currentOffset, salt.length);
        data.set(currentOffset, salt);
        currentOffset += salt.size();
        Bytes sha3InitCode = Hash.sha3(Bytes.wrap(initCode));
//        System.arraycopy(sha3InitCode, 0, data, currentOffset, sha3InitCode.length);
        data.set(currentOffset, sha3InitCode);
        return sha3omit12(data);
    }

}

