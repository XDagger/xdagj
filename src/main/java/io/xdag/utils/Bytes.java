/**
 * Copyright (c) 2017-2020 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package io.xdag.utils;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class Bytes {

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Default charset.
     */
    public static final String CHARSET = "UTF-8";

    /**
     * Empty byte array.
     */
    public static final byte[] EMPTY_BYTES = new byte[0];

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

    private Bytes() {
    }

    /**
     * Generate a random byte array of required length.
     *
     * @param n
     * @return
     */
    public static byte[] random(int n) {
        byte[] bytes = new byte[n];
        secureRandom.nextBytes(bytes);

        return bytes;
    }

    /**
     * Merge two byte arrays into one.
     *
     * @param b1
     * @param b2
     * @return
     */
    public static byte[] merge(byte[] b1, byte[] b2) {
        byte[] res = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, res, 0, b1.length);
        System.arraycopy(b2, 0, res, b1.length, b2.length);

        return res;
    }

    /**
     * Merge byte array and byte
     *
     * @param b1
     * @param b2
     * @return
     */
    public static byte[] merge(byte[] b1, byte b2) {
        byte[] res = new byte[b1.length + 1];
        System.arraycopy(b1, 0, res, 0, b1.length);
        res[b1.length] = b2;

        return res;
    }

    /**
     * Merge byte and byte array.
     *
     * @param b1
     * @param b2
     * @return
     */
    public static byte[] merge(byte b1, byte[] b2) {
        byte[] res = new byte[1 + b2.length];
        res[0] = b1;
        System.arraycopy(b2, 0, res, 1, b2.length);

        return res;
    }

    /**
     * Merge byte arrays into one.
     *
     * @param bytes
     *            byte arrays
     * @return
     */
    public static byte[] merge(byte[]... bytes) {
        return merge(Arrays.asList(bytes));
    }

    /**
     * Merge byte arrays into one.
     *
     * @param bytes
     *            byte arrays
     * @return
     */
    public static byte[] merge(List<byte[]> bytes) {
        int length = 0;
        for (byte[] b : bytes) {
            length += b.length;
        }

        byte[] res = new byte[length];
        int i = 0;
        for (byte[] b : bytes) {
            System.arraycopy(b, 0, res, i, b.length);
            i += b.length;
        }

        return res;
    }

    /**
     * Convert string into an byte array.
     *
     * @param str
     * @return
     */
    public static byte[] of(String str) {
        try {
            return str.getBytes(CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert a byte into an byte array.
     *
     * @param b
     * @return
     */
    public static byte[] of(byte b) {
        return new byte[] { b };
    }

    /**
     * Convert a short integer into an byte array.
     *
     * @param s
     * @return
     */
    public static byte[] of(short s) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((s >> 8) & 0xff);
        bytes[1] = (byte) (s & 0xff);
        return bytes;
    }

    /**
     * Convert an integer into an byte array.
     *
     * @param i
     * @return
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
     * Convert a long integer into an byte array.
     *
     * @param i
     * @return
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
     * Convert byte array into string.
     *
     * @param bytes
     * @return
     */
    public static String toString(byte[] bytes) {
        try {
            return new String(bytes, CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Covert byte array into a byte.
     *
     * @param bytes
     * @return
     */
    public static byte toByte(byte[] bytes) {
        return bytes[0];
    }

    /**
     * Covert byte array into a short integer.
     *
     * @param bytes
     * @return
     */
    public static short toShort(byte[] bytes) {
        return (short) (((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff));
    }

    /**
     * Covert byte array into an integer.
     *
     * @param bytes
     * @return
     */
    public static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    /**
     * Covert byte array into a long integer.
     *
     * @param bytes
     * @return
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


}
