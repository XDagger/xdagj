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
package io.xdag.utils.discoveryutils.bytes;

import static com.google.common.base.Preconditions.checkArgument;

public class WrappingBytes32 extends AbstractBytesValue implements Bytes32 {

    private final BytesValue value;

    public WrappingBytes32(final BytesValue value) {
        checkArgument(
                value.size() == SIZE, "Expected value to be %s bytes, but is %s bytes", SIZE, value.size());
        this.value = value;
    }

    @Override
    public byte get(final int i) {
        return value.get(i);
    }

    @Override
    public BytesValue slice(final int index, final int length) {
        return value.slice(index, length);
    }

    @Override
    public MutableBytes32 mutableCopy() {
        final MutableBytes32 copy = MutableBytes32.create();
        value.copyTo(copy);
        return copy;
    }

    @Override
    public Bytes32 copy() {
        return mutableCopy();
    }

    @Override
    public byte[] getArrayUnsafe() {
        return value.getArrayUnsafe();
    }

    @Override
    public int size() {
        return value.size();
    }
}
