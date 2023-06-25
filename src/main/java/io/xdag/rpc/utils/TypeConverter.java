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

package io.xdag.rpc.utils;

import java.math.BigInteger;
import java.util.regex.Pattern;
import org.bouncycastle.util.encoders.Hex;

public class TypeConverter {

    private static final Pattern LEADING_ZEROS_PATTERN = Pattern.compile("0x(0)+");

    private TypeConverter() {
        throw new IllegalAccessError("Utility class");
    }

    public static String toJsonHex(byte[] x) {
        String result = toUnformattedJsonHex(x);

        if ("0x".equals(result)) {
            return "0x00";
        }

        return result;
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(long n) {
        return "0x" + Long.toHexString(n);
    }

    public static String toQuantityJsonHex(double n) {
        return String.valueOf(n);
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(BigInteger n) {
        return "0x" + n.toString(16);
    }

    /**
     * Converts a byte array to a string according to ethereum json-rpc specifications, null and empty
     * convert to 0x.
     *
     * @param x An unformatted byte array
     * @return A hex representation of the input with two hex digits per byte
     */
    public static String toUnformattedJsonHex(byte[] x) {
        return "0x" + (x == null ? "" : Hex.toHexString(x));
    }

    /**
     * Converts a byte array representing a quantity according to ethereum json-rpc specifications.
     *
     * <p>
     * 0x000AEF -> 0x2AEF
     * <p>
     * 0x00 -> 0x0
     *
     * @param x A hex string with or without leading zeroes ("0x00AEF"). If null, it is considered as zero.
     * @return A hex string without leading zeroes ("0xAEF")
     */
    public static String toQuantityJsonHex(byte[] x) {
        String withoutLeading = LEADING_ZEROS_PATTERN.matcher(toJsonHex(x)).replaceFirst("0x");
        if ("0x".equals(withoutLeading)) {
            return "0x0";
        }

        return withoutLeading;
    }
}

