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

import java.math.BigInteger;

public interface UInt256 extends UInt256Value<UInt256> {
    /** The value 0. */
    UInt256 ZERO = of(0);
    /** The value 1. */
    UInt256 ONE = of(1);
    /** The value 32. */
    UInt256 U_32 = of(32);

    static UInt256 of(final long value) {
        return new DefaultUInt256(UInt256Bytes.of(value));
    }

    static UInt256 of(final BigInteger value) {
        return new DefaultUInt256(UInt256Bytes.of(value));
    }

    static UInt256 wrap(final Bytes32 value) {
        return new DefaultUInt256(value);
    }

    static Counter<UInt256> newCounter() {
        return DefaultUInt256.newVar();
    }

    static Counter<UInt256> newCounter(final UInt256Value<?> initialValue) {
        final Counter<UInt256> c = DefaultUInt256.newVar();
        initialValue.getBytes().copyTo(c.getBytes());
        return c;
    }

    static UInt256 fromHexString(final String str) {
        return new DefaultUInt256(Bytes32.fromHexStringLenient(str));
    }
}
