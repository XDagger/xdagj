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

public class BIUtils {

    /**
     * @param value
     *            - not null
     * @return true - if the param is zero
     */
    public static boolean isZero(BigInteger value) {
        return value.compareTo(BigInteger.ZERO) == 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is equal to valueB is zero
     */
    public static boolean isEqual(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) == 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is not equal to valueB is zero
     */
    public static boolean isNotEqual(BigInteger valueA, BigInteger valueB) {
        return !isEqual(valueA, valueB);
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is less than valueB is zero
     */
    public static boolean isLessThan(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) < 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is more than valueB is zero
     */
    public static boolean isMoreThan(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) > 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return sum - valueA + valueB
     */
    public static BigInteger sum(BigInteger valueA, BigInteger valueB) {
        return valueA.add(valueB);
    }

    /**
     * @param data
     *            = not null
     * @return new positive BigInteger
     */
    public static BigInteger toBI(byte[] data) {
        return new BigInteger(1, data);
    }

    /**
     * @param data
     *            = not null
     * @return new positive BigInteger
     */
    public static BigInteger toBI(long data) {
        return BigInteger.valueOf(data);
    }

    public static boolean isPositive(BigInteger value) {
        return value.signum() > 0;
    }

    public static boolean isCovers(BigInteger covers, BigInteger value) {
        return !isNotCovers(covers, value);
    }

    public static boolean isNotCovers(BigInteger covers, BigInteger value) {
        return covers.compareTo(value) < 0;
    }

    public static boolean exitLong(BigInteger value) {

        return (value.compareTo(new BigInteger(Long.MAX_VALUE + ""))) > -1;
    }

    public static boolean isIn20PercentRange(BigInteger first, BigInteger second) {
        BigInteger five = BigInteger.valueOf(5);
        BigInteger limit = first.add(first.divide(five));
        return !isMoreThan(second, limit);
    }

    public static BigInteger max(BigInteger first, BigInteger second) {
        return first.compareTo(second) < 0 ? second : first;
    }

    /**
     * Returns a result of safe addition of two {@code int} values
     * {@code Integer.MAX_VALUE} is returned if overflow occurs
     */
    public static int addSafely(int a, int b) {
        long res = (long) a + (long) b;
        return res > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) res;
    }
}
