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
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class BaseUInt256Value<T extends UInt256Value<T>> extends AbstractUInt256Value<T> {

    protected BaseUInt256Value(final Bytes32 bytes, final Supplier<Counter<T>> mutableCtor) {
        super(bytes, mutableCtor);
    }

    protected BaseUInt256Value(final long v, final Supplier<Counter<T>> mutableCtor) {
        this(UInt256Bytes.of(v), mutableCtor);
        checkArgument(v >= 0, "Invalid negative value %s for an unsigned scalar", v);
    }

    protected BaseUInt256Value(final BigInteger v, final Supplier<Counter<T>> mutableCtor) {
        this(UInt256Bytes.of(v), mutableCtor);
        checkArgument(v.signum() >= 0, "Invalid negative value %s for an unsigned scalar", v);
    }

    protected BaseUInt256Value(final String hexString, final Supplier<Counter<T>> mutableCtor) {
        this(Bytes32.fromHexStringLenient(hexString), mutableCtor);
    }

    public T times(final UInt256 value) {
        return binaryOp(value, UInt256Bytes::multiply);
    }

    public T mod(final UInt256 value) {
        return binaryOp(value, UInt256Bytes::modulo);
    }

    public int compareTo(final UInt256 other) {
        return UInt256Bytes.compareUnsigned(this.bytes, other.getBytes());
    }

    @Override
    public UInt256 asUInt256() {
        return new DefaultUInt256(bytes);
    }
}
