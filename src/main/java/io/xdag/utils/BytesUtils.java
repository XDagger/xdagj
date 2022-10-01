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

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.UnsignedLong;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BytesUtils {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    public static byte[] intToBytes(int value, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        buffer.putInt(value);
        return buffer.array();
    }

    /**
     * 改为了4字节 原先为length 8
     */
    public static int bytesToInt(byte[] input, int offset, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(input, offset, 4);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getInt();
    }

    public static byte[] longToBytes(long value, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        buffer.putLong(value);
        return buffer.array();
    }

    public static long bytesToLong(byte[] input, int offset, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(input, offset, 8);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getLong();
    }

    public static byte[] shortToBytes(short value, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        buffer.putShort(value);
        return buffer.array();
    }

    public static byte[] byteToBytes(byte value, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        buffer.put(value);
        return buffer.array();
    }

    public static short bytesToShort(byte[] input, int offset, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(input, offset, 2);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getShort();
    }

    public static String toHexString(byte[] data) {
        return data == null ? "" : BaseEncoding.base16().lowerCase().encode(data);
    }

    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null) {
            return null;
        }
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = long2UnsignedLong(b.longValue()).bigIntegerValue().toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes, boolean littleEndian) {
        byte[] bytes = bigIntegerToBytes(b, numBytes);
        if (littleEndian) {
            arrayReverse(bytes);
        }
        return bytes;
    }

    /**
     * @param arrays - arrays to merge
     * @return - merged array
     */
    public static byte[] merge(byte[]... arrays) {
        int count = 0;
        for (byte[] array : arrays) {
            count += array.length;
        }

        // Create new array and copy all array contents
        byte[] mergedArray = new byte[count];
        int start = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, start, array.length);
            start += array.length;
        }
        return mergedArray;
    }

    /**
     * Merge byte and byte array.
     */
    public static byte[] merge(byte b1, byte[] b2) {
        byte[] res = new byte[1 + b2.length];
        res[0] = b1;
        System.arraycopy(b2, 0, res, 1, b2.length);
        return res;
    }

    /**
     * @param arrays - 字节数组
     * @param index - 起始索引
     * @param length - 长度
     * @return - 子数组
     */
    public static byte[] subArray(byte[] arrays, int index, int length) {
        byte[] arrayOfByte = new byte[length];
        int i = 0;
        while (true) {
            if (i >= length) {
                return arrayOfByte;
            }
            arrayOfByte[i] = arrays[(i + index)];
            i += 1;
        }
    }

    /**
     * Hex字符串转byte
     *
     * @param hexString 待转换的Hex字符串
     * @return 转换后的byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || "".equals(hexString)) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    /**
     * 数组逆序
     */
    public static void arrayReverse(byte[] origin) {
        byte temp;
        for (int i = 0; i < origin.length / 2; i++) {
            temp = origin[i];
            origin[i] = origin[origin.length - i - 1];
            origin[origin.length - i - 1] = temp;
        }
    }

    /**
     * Convert a byte into an byte array.
     */
    public static byte[] of(byte b) {
        return new byte[]{b};
    }

    public static byte toByte(byte[] bytes) {
        return bytes[0];
    }

    public static boolean keyStartsWith(byte[] key, byte[] part) {
        if (part.length > key.length) {
            return false;
        }
        for (int i = 0; i < part.length; i++) {
            if (key[i] != part[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean isFullZero(byte[] input) {
        for (byte b : input) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 直接将十六进制的byte[]数组转换为都变了的数据
     *
     * @param input byte[]类型的hash 这里的hash 是正向排序了的
     * @param offset 偏移位置
     * @param littleEndian 是否为大小端
     */
    public static double hexBytesToDouble(byte[] input, int offset, boolean littleEndian) {
        byte[] data = new byte[8];
        System.arraycopy(input, offset, data, 0, 8);
        if (littleEndian) {
            ArrayUtils.reverse(data);
        }
        return Numeric.toBigInt(data).doubleValue();
    }

    public static byte[] stripLeadingZeroes(byte[] data) {
        return stripLeadingZeroes(data, ZERO_BYTE_ARRAY);
    }

    public static byte[] stripLeadingZeroes(byte[] data, byte[] valueForZero) {
        if (data == null) {
            return null;
        }

        final int firstNonZero = firstNonZeroByte(data);
        switch (firstNonZero) {
            case -1:
                return valueForZero;

            case 0:
                return data;

            default:
                byte[] result = new byte[data.length - firstNonZero];
                System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);

                return result;
        }
    }

    public static int firstNonZeroByte(byte[] data) {
        for (int i = 0; i < data.length; ++i) {
            if (data[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    public static boolean equalBytes(byte[] b1, byte[] b2) {
        return b1.length == b2.length && compareTo(b1, 0, b1.length, b2, 0, b2.length) == 0;
    }

    /**
     * Lexicographically compare two byte arrays.
     *
     * @param b1 buffer1
     * @param s1 offset1
     * @param l1 length1
     * @param b2 buffer2
     * @param s2 offset2
     * @param l2 length2
     * @return int
     */
    public static int compareTo(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
        return LexicographicalComparerHolder.BEST_COMPARER.compareTo(b1, s1, l1, b2, s2, l2);
    }

    private static Comparer<byte[]> lexicographicalComparerJavaImpl() {
        return LexicographicalComparerHolder.PureJavaComparer.INSTANCE;
    }

    private interface Comparer<T> {

        int compareTo(T buffer1, int offset1, int length1, T buffer2, int offset2, int length2);
    }

    /**
     * Uses reflection to gracefully fall back to the Java implementation if
     * {@code Unsafe} isn't available.
     */
    private static class LexicographicalComparerHolder {

        static final String UNSAFE_COMPARER_NAME = LexicographicalComparerHolder.class.getName() + "$UnsafeComparer";

        static final Comparer<byte[]> BEST_COMPARER = getBestComparer();

        /**
         * Returns the Unsafe-using Comparer, or falls back to the pure-Java
         * implementation if unable to do so.
         */
        static Comparer<byte[]> getBestComparer() {
            try {
                Class<?> theClass = Class.forName(UNSAFE_COMPARER_NAME);

                // yes, UnsafeComparer does implement Comparer<byte[]>
                @SuppressWarnings("unchecked")
                Comparer<byte[]> comparer = (Comparer<byte[]>) theClass.getEnumConstants()[0];
                return comparer;
            } catch (Throwable t) { // ensure we really catch *everything*
                return lexicographicalComparerJavaImpl();
            }
        }

        private enum PureJavaComparer implements Comparer<byte[]> {
            INSTANCE;

            @Override
            public int compareTo(
                    byte[] buffer1, int offset1, int length1, byte[] buffer2, int offset2, int length2) {
                // Short circuit equalBytes case
                if (buffer1 == buffer2 && offset1 == offset2 && length1 == length2) {
                    return 0;
                }
                int end1 = offset1 + length1;
                int end2 = offset2 + length2;
                for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
                    int a = (buffer1[i] & 0xff);
                    int b = (buffer2[j] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return length1 - length2;
            }
        }
    }



    public static UnsignedLong long2UnsignedLong(long number) {
        return UnsignedLong.valueOf(toHexString((ByteBuffer.allocate(8).putLong(number).array())),16);
    }
}
