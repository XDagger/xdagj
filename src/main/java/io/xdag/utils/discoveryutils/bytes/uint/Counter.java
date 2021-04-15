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
import io.xdag.utils.discoveryutils.bytes.MutableBytes32;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

public class Counter<T extends UInt256Value<T>> {

    private final MutableBytes32 bytes;
    private final T value;

    // Kept around for copy()
    private final Function<Bytes32, T> wrapFct;

    protected Counter(final Function<Bytes32, T> wrapFct) {
        this(MutableBytes32.create(), wrapFct);
    }

    protected Counter(final MutableBytes32 bytes, final Function<Bytes32, T> wrapFct) {
        this.bytes = bytes;
        this.value = wrapFct.apply(bytes);
        this.wrapFct = wrapFct;
    }

    public T get() {
        return value;
    }

    public MutableBytes32 getBytes() {
        return bytes;
    }

    public Counter<T> copy() {
        return new Counter<>(bytes.mutableCopy(), wrapFct);
    }

    public void increment() {
        increment(1);
    }

    public void increment(final long increment) {
        checkArgument(increment >= 0, "Invalid negative increment %s", increment);
        UInt256Bytes.add(bytes, increment, bytes);
    }

    public void increment(final T increment) {
        UInt256Bytes.add(bytes, increment.getBytes(), bytes);
    }

    public void decrement() {
        decrement(1);
    }

    public void decrement(final long decrement) {
        checkArgument(decrement >= 0, "Invalid negative decrement %s", decrement);
        UInt256Bytes.subtract(bytes, decrement, bytes);
    }

    public void decrement(final T decrement) {
        UInt256Bytes.subtract(bytes, decrement.getBytes(), bytes);
    }

    public void set(final T value) {
        value.getBytes().copyTo(bytes);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
