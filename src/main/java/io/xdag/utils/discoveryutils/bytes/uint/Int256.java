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

import io.xdag.utils.discoveryutils.bytes.Bytes32;
import io.xdag.utils.discoveryutils.bytes.Bytes32Backed;

public interface Int256 extends Bytes32Backed, Comparable<Int256> {

    int SIZE = 32;

    /** The value -1. */
    Int256 MINUS_ONE = DefaultInt256.minusOne();

    static Int256 wrap(final Bytes32 bytes) {
        return new DefaultInt256(bytes);
    }

    default boolean isZero() {
        return getBytes().isZero();
    }

    /** @return True if the value is negative. */
    default boolean isNegative() {
        return getBytes().get(0) < 0;
    }

    Int256 dividedBy(Int256 value);

    Int256 mod(Int256 value);

    /** @return A view of the bytes of this number as signed (two's complement). */
    default UInt256 asUnsigned() {
        return new DefaultUInt256(getBytes());
    }
}