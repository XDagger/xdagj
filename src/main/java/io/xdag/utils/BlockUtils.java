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

import com.google.common.primitives.UnsignedLong;
import io.xdag.db.BlockStore;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Utility class for block-related operations
 */
public final class BlockUtils {

    /**
     * Generates a key for time-based block lookup
     * @param timestamp Block timestamp
     * @param hashlow Lower 32 bytes of block hash
     * @return Generated key as byte array
     */
    public static byte[] getTimeKey(long timestamp, Bytes32 hashlow) {
        long t = UnsignedLong.fromLongBits(timestamp >> 16).longValue();
        byte[] key = BytesUtils.merge(BlockStore.TIME_HASH_INFO, BytesUtils.longToBytes(t, false));
        if (hashlow == null) {
            return key;
        }
        return BytesUtils.merge(key, hashlow.toArray());
    }

    /**
     * Generates a key for our block lookup by index and hash
     * @param index Block index
     * @param hashlow Lower bytes of block hash
     * @return Generated key as byte array
     */
    public static byte[] getOurKey(int index, byte[] hashlow) {
        byte[] key = BytesUtils.merge(BlockStore.OURS_BLOCK_INFO, BytesUtils.intToBytes(index, false));
        key = BytesUtils.merge(key, hashlow);
        return key;
    }

    /**
     * Generates a key for block height lookup
     * @param height Block height
     * @return Generated key as byte array
     */
    public static byte[] getHeight(long height) {
        return BytesUtils.merge(BlockStore.BLOCK_HEIGHT, BytesUtils.longToBytes(height, false));
    }

    /**
     * Extracts block index from a key
     * @param key Source key
     * @return Block index, or 0 if extraction fails
     */
    public static int getOurIndex(byte[] key) {
        try {
            byte[] index = BytesUtils.subArray(key, 1, 4);
            return BytesUtils.bytesToInt(index, 0, false);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Extracts block hash from a key
     * @param key Source key
     * @return Block hash as byte array, or null if extraction fails
     */
    public static byte[] getOurHash(byte[] key) {
        try {
            return BytesUtils.subArray(key, 5, 32);
        } catch (Exception e) {
            return null;
        }
    }

}
