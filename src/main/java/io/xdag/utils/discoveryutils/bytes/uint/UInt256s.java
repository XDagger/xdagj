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
package io.xdag.utils.discoveryutils.bytes.uint;

public class UInt256s {

    /**
     * Returns the maximum of 2 UInt256 values.
     *
     * @param v1 The first value.
     * @param v2 The second value.
     * @return The maximum of {@code v1} and {@code v2}.
     * @param <T> The concrete type of the two values.
     */
    public static <T extends UInt256Value<T>> T max(final T v1, final T v2) {
        return (v1.compareTo(v2)) >= 0 ? v1 : v2;
    }

    /**
     * Returns the minimum of 2 UInt256 values.
     *
     * @param v1 The first value.
     * @param v2 The second value.
     * @return The minimum of {@code v1} and {@code v2}.
     * @param <T> The concrete type of the two values.
     */
    public static <T extends UInt256Value<T>> T min(final T v1, final T v2) {
        return (v1.compareTo(v2)) < 0 ? v1 : v2;
    }

    public static <T extends UInt256Value<T>> boolean greaterThanOrEqualTo256(final T uint256) {
        return uint256.bitLength() > 8;
    }
}
