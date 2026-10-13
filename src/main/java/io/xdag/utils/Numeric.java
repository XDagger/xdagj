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

import java.math.BigInteger;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for numeric and hex string conversions
 */
public class Numeric {

    private static final String HEX_PREFIX = "0x";
    private static final char[] HEX_CHAR_MAP = "0123456789abcdef".toCharArray();

    private Numeric() {
    }

    /**
     * Removes "0x" prefix from hex string if present
     */
    public static String cleanHexPrefix(String input) {
        if (containsHexPrefix(input)) {
            return input.substring(2);
        } else {
            return input;
        }
    }

    /**
     * Checks if string starts with "0x" prefix
     */
    public static boolean containsHexPrefix(String input) {
        return !StringUtils.isEmpty(input)
                && input.length() > 1
                && input.charAt(0) == '0'
                && input.charAt(1) == 'x';
    }

    /**
     * Converts byte array to BigInteger
     */
    public static BigInteger toBigInt(byte[] value) {
        return new BigInteger(1, value);
    }

    /**
     * Converts hex string to BigInteger, handling "0x" prefix
     */
    public static BigInteger toBigInt(String hexValue) {
        String cleanValue = cleanHexPrefix(hexValue);
        return toBigIntNoPrefix(cleanValue);
    }

    /**
     * Converts hex string without prefix to BigInteger
     */
    public static BigInteger toBigIntNoPrefix(String hexValue) {
        return new BigInteger(hexValue, 16);
    }

    /**
     * Converts byte array to hex string with offset and length
     */
    public static String toHexString(byte[] input, int offset, int length, boolean withPrefix) {
        final String output = new String(toHexCharArray(input, offset, length, withPrefix));
        return withPrefix ? HEX_PREFIX + output : output;
    }

    /**
     * Helper method to convert byte array to hex char array
     */
    private static char[] toHexCharArray(byte[] input, int offset, int length, boolean withPrefix) {
        final char[] output = new char[length << 1];
        for (int i = offset, j = 0; i < length; i++, j++) {
            final int v = input[i] & 0xFF;
            output[j++] = HEX_CHAR_MAP[v >>> 4];
            output[j] = HEX_CHAR_MAP[v & 0x0F];
        }
        return output;
    }

    /**
     * Converts byte array to hex string with "0x" prefix
     */
    public static String toHexString(byte[] input) {
        return toHexString(input, 0, input.length, true);
    }

}
