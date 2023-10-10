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

import static io.xdag.utils.Preconditions.check;
import static io.xdag.utils.Preconditions.checkArgument;

import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.SecureRandomProvider;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.UnsignedLong;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

public class BytesUtils {

    /**
     * Empty byte array.
     */
    public static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Empty 4 bytes array.
     */
    public static final byte[] EMPTY_4BYTES = new byte[4];

    /**
     * Empty address.
     */
    public static final byte[] EMPTY_ADDRESS = new byte[20];

    /**
     * Empty 256-bit hash.
     * <p>
     * Note: this is not the hash of empty byte array.
     */
    public static final byte[] EMPTY_HASH = new byte[32];

    /** Maximum unsigned value that can be expressed by 32 bits. */
    public static final long MAX_UNSIGNED_INTEGER = Integer.toUnsignedLong(-1);

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

    public static byte[] shortToBytes(short value, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        buffer.putShort(value);
        return buffer.array();
    }

    public static short bytesToShort(byte[] input, int offset, boolean littleEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(input, offset, 2);
        if (littleEndian) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getShort();
    }

    public static Pointer bytesToPointer(byte[] bytes) {
        Pointer pointer = new Memory(bytes.length);
        pointer.write(0, bytes, 0, bytes.length);
        return pointer;
    }

    public static String toHexString(byte[] data) {
        return data == null ? "" : BaseEncoding.base16().lowerCase().encode(data);
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
        if (hexString == null || hexString.isEmpty()) {
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
     * Convert a byte into a byte array.
     */
    public static byte[] of(byte b) {
        return new byte[]{b};
    }

    /**
     * Convert a short integer into a byte array.
     */
    public static byte[] of(short s) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((s >> 8) & 0xff);
        bytes[1] = (byte) (s & 0xff);
        return bytes;
    }

    /**
     * Convert an integer into a byte array.
     */
    public static byte[] of(int i) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((i >> 24) & 0xff);
        bytes[1] = (byte) ((i >> 16) & 0xff);
        bytes[2] = (byte) ((i >> 8) & 0xff);
        bytes[3] = (byte) (i & 0xff);
        return bytes;
    }

    /**
     * Convert a long integer into a byte array.
     */
    public static byte[] of(long i) {
        byte[] bytes = new byte[8];
        bytes[0] = (byte) ((i >> 56) & 0xff);
        bytes[1] = (byte) ((i >> 48) & 0xff);
        bytes[2] = (byte) ((i >> 40) & 0xff);
        bytes[3] = (byte) ((i >> 32) & 0xff);
        bytes[4] = (byte) ((i >> 24) & 0xff);
        bytes[5] = (byte) ((i >> 16) & 0xff);
        bytes[6] = (byte) ((i >> 8) & 0xff);
        bytes[7] = (byte) (i & 0xff);
        return bytes;
    }

    /**
     * Convert string into a byte array.
     */
    public static byte[] of(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Covert byte array into an integer.
     */
    public static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    /**
     * Covert byte array into a long integer.
     */
    public static long toLong(byte[] bytes) {
        return ((bytes[0] & 0xffL) << 56)
                | ((bytes[1] & 0xffL) << 48)
                | ((bytes[2] & 0xffL) << 40)
                | ((bytes[3] & 0xffL) << 32)
                | ((bytes[4] & 0xffL) << 24)
                | ((bytes[5] & 0xffL) << 16)
                | ((bytes[6] & 0xffL) << 8)
                | (bytes[7] & 0xff);
    }

    public static boolean isFullZero(byte[] input) {
        for (byte b : input) {
            if (b != 0) {
                return false;
            }
        }
        return true;
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

    public static MutableBytes32 arrayToByte32(byte[] value){
        MutableBytes32 mutableBytes32 = MutableBytes32.wrap(new byte[32]);
        mutableBytes32.set(8, Bytes.wrap(value));
        return mutableBytes32;
    }
    public static byte[] byte32ToArray(MutableBytes32 value){
        return value.mutableCopy().slice(8,20).toArray();
    }

    public static UnsignedLong long2UnsignedLong(long number) {
        return UnsignedLong.valueOf(toHexString((ByteBuffer.allocate(8).putLong(number).array())),16);
    }

    public static byte[] random(int n) {
        byte[] bytes = new byte[n];
        SecureRandomProvider.publicSecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     * @param hasLength can be set to false if the given array is missing the 4 byte length field
     */
    public static BigInteger decodeMPI(byte[] mpi, boolean hasLength) {
        byte[] buf;
        if (hasLength) {
            int length = (int) readUint32BE(mpi, 0);
            buf = new byte[length];
            System.arraycopy(mpi, 4, buf, 0, length);
        } else
            buf = mpi;
        if (buf.length == 0)
            return BigInteger.ZERO;
        boolean isNegative = (buf[0] & 0x80) == 0x80;
        if (isNegative)
            buf[0] &= 0x7f;
        BigInteger result = new BigInteger(buf);
        return isNegative ? result.negate() : result;
    }

    /**
     * Read 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in little endian format.
     * @param bytes buffer to be read from
     * @param offset offset into the buffer
     * @throws ArrayIndexOutOfBoundsException if offset points outside of the buffer, or
     *                                        if the read value extends beyond the remaining bytes of the buffer
     */
    public static long readUint32(byte[] bytes, int offset) throws ArrayIndexOutOfBoundsException {
        check(offset >= 0 && offset <= bytes.length - 4, () ->
                new ArrayIndexOutOfBoundsException(offset));
        return readUint32(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
    }

    /**
     * Read 4 bytes from the buffer as unsigned 32-bit integer in little endian format.
     * @param buf buffer to be read from
     * @throws BufferUnderflowException if the read value extends beyond the remaining bytes of the buffer
     */
    public static long readUint32(ByteBuffer buf) throws BufferUnderflowException {
        return Integer.toUnsignedLong(buf.order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    /**
     * Read 4 bytes from the buffer as unsigned 32-bit integer in big endian format.
     * @param buf buffer to be read from
     * @throws BufferUnderflowException if the read value extends beyond the remaining bytes of the buffer
     */
    public static long readUint32BE(ByteBuffer buf) throws BufferUnderflowException {
        return Integer.toUnsignedLong(buf.order(ByteOrder.BIG_ENDIAN).getInt());
    }

    /**
     * Read 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in big endian format.
     * @param bytes buffer to be read from
     * @param offset offset into the buffer
     * @throws ArrayIndexOutOfBoundsException if offset points outside of the buffer, or
     *                                        if the read value extends beyond the remaining bytes of the buffer
     */
    public static long readUint32BE(byte[] bytes, int offset) throws ArrayIndexOutOfBoundsException {
        check(offset >= 0 && offset <= bytes.length - 4, () ->
                new ArrayIndexOutOfBoundsException(offset));
        return readUint32BE(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
    }

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     * @param includeLength indicates whether the 4 byte length field should be included
     */
    public static byte[] encodeMPI(BigInteger value, boolean includeLength) {
        if (value.equals(BigInteger.ZERO)) {
            if (!includeLength)
                return new byte[] {};
            else
                return new byte[] {0x00, 0x00, 0x00, 0x00};
        }
        boolean isNegative = value.signum() < 0;
        if (isNegative)
            value = value.negate();
        byte[] array = value.toByteArray();
        int length = array.length;
        if ((array[0] & 0x80) == 0x80)
            length++;
        if (includeLength) {
            byte[] result = new byte[length + 4];
            System.arraycopy(array, 0, result, length - array.length + 3, array.length);
            writeInt32BE(length, result, 0);
            if (isNegative)
                result[4] |= 0x80;
            return result;
        } else {
            byte[] result;
            if (length != array.length) {
                result = new byte[length];
                System.arraycopy(array, 0, result, 1, array.length);
            }else
                result = array;
            if (isNegative)
                result[0] |= 0x80;
            return result;
        }
    }

    /**
     * Write a 32-bit integer to a given byte array in big-endian format, starting at a given offset.
     * <p>
     * The value is expected as a signed or unsigned {@code int}. If you've got an unsigned {@code long} as per the
     * Java Unsigned Integer API, use {@link #writeInt32BE(long, byte[], int)}.
     *
     * @param val    value to be written
     * @param out    buffer to be written into
     * @param offset offset into the buffer
     * @throws ArrayIndexOutOfBoundsException if offset points outside of the buffer, or
     *                                        if the value doesn't fit the remaining buffer
     */
    public static void writeInt32BE(int val, byte[] out, int offset) throws ArrayIndexOutOfBoundsException {
        writeInt32BE(val, ByteBuffer.wrap(out, offset, out.length - offset));
    }

    /**
     * Write a 32-bit integer to a given byte array in big-endian format, starting at a given offset.
     * <p>
     * The value is expected as an unsigned {@code long} as per the Java Unsigned Integer API.
     *
     * @param val    value to be written
     * @param out    buffer to be written into
     * @param offset offset into the buffer
     * @throws ArrayIndexOutOfBoundsException if offset points outside of the buffer, or
     *                                        if the value doesn't fit the remaining buffer
     */
    public static void writeInt32BE(long val, byte[] out, int offset) throws ArrayIndexOutOfBoundsException {
        check(offset >= 0 && offset <= out.length - 4, () ->
                new ArrayIndexOutOfBoundsException(offset));
        writeInt32BE(val, ByteBuffer.wrap(out, offset, out.length - offset));
    }

    /**
     * Write a 32-bit integer to a given buffer in big-endian format.
     * <p>
     * The value is expected as a signed or unsigned {@code int}. If you've got an unsigned {@code long} as per the
     * Java Unsigned Integer API, use {@link #writeInt32BE(long, ByteBuffer)}.
     *
     * @param val value to be written
     * @param buf buffer to be written into
     * @return the buffer
     * @throws BufferOverflowException if the value doesn't fit the remaining buffer
     */
    public static ByteBuffer writeInt32BE(int val, ByteBuffer buf) throws BufferOverflowException {
        return buf.order(ByteOrder.BIG_ENDIAN).putInt((int) val);
    }

    /**
     * Write a 32-bit integer to a given buffer in big-endian format.
     * <p>
     * The value is expected as an unsigned {@code long} as per the Java Unsigned Integer API.
     *
     * @param val value to be written
     * @param buf buffer to be written into
     * @return the buffer
     * @throws BufferOverflowException if the value doesn't fit the remaining buffer
     */
    public static ByteBuffer writeInt32BE(long val, ByteBuffer buf) throws BufferOverflowException {
        checkArgument(val >= 0 && val <= MAX_UNSIGNED_INTEGER, () ->
                "value out of range: " + val);
        return buf.order(ByteOrder.BIG_ENDIAN).putInt((int) val);
    }

    /**
     * Write a 32-bit integer to a given buffer in little-endian format.
     * <p>
     * The value is expected as an unsigned {@code long} as per the Java Unsigned Integer API.
     *
     * @param val value to be written
     * @param buf buffer to be written into
     * @return the buffer
     * @throws BufferOverflowException if the value doesn't fit the remaining buffer
     */
    public static ByteBuffer writeInt32LE(long val, ByteBuffer buf) throws BufferOverflowException {
        checkArgument(val >= 0 && val <= MAX_UNSIGNED_INTEGER, () ->
                "value out of range: " + val);
        return buf.order(ByteOrder.LITTLE_ENDIAN).putInt((int) val);
    }

    /**
     * Write a 32-bit integer to a given byte array in little-endian format, starting at a given offset.
     * <p>
     * The value is expected as an unsigned {@code long} as per the Java Unsigned Integer API.
     *
     * @param val    value to be written
     * @param out    buffer to be written into
     * @param offset offset into the buffer
     * @throws ArrayIndexOutOfBoundsException if offset points outside of the buffer, or
     *                                        if the value doesn't fit the remaining buffer
     */
    public static void writeInt32LE(long val, byte[] out, int offset) throws ArrayIndexOutOfBoundsException {
        check(offset >= 0 && offset <= out.length - 4, () ->
                new ArrayIndexOutOfBoundsException(offset));
        writeInt32LE(val, ByteBuffer.wrap(out, offset, out.length - offset));
    }

    /**
     * <p>The "compact" format is a representation of a whole number N using an unsigned 32 bit number similar to a
     * floating point format. The most significant 8 bits are the unsigned exponent of base 256. This exponent can
     * be thought of as "number of bytes of N". The lower 23 bits are the mantissa. Bit number 24 (0x800000) represents
     * the sign of N. Therefore, N = (-1^sign) * mantissa * 256^(exponent-3).</p>
     *
     * <p>Satoshi's original implementation used BN_bn2mpi() and BN_mpi2bn(). MPI uses the most significant bit of the
     * first byte as sign. Thus 0x1234560000 is compact 0x05123456 and 0xc0de000000 is compact 0x0600c0de. Compact
     * 0x05c0de00 would be -0x40de000000.</p>
     *
     * <p>Bitcoin only uses this "compact" format for encoding difficulty targets, which are unsigned 256bit quantities.
     * Thus, all the complexities of the sign bit and using base 256 are probably an implementation accident.</p>
     */
    public static BigInteger decodeCompactBits(long compact) {
        int size = ((int) (compact >> 24)) & 0xFF;
        byte[] bytes = new byte[4 + size];
        bytes[3] = (byte) size;
        if (size >= 1) bytes[4] = (byte) ((compact >> 16) & 0xFF);
        if (size >= 2) bytes[5] = (byte) ((compact >> 8) & 0xFF);
        if (size >= 3) bytes[6] = (byte) (compact & 0xFF);
        return decodeMPI(bytes, true);
    }
}
