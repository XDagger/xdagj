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

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

class MutableArrayWrappingBytesValue extends ArrayWrappingBytesValue implements MutableBytesValue {

    MutableArrayWrappingBytesValue(final byte[] bytes) {
        super(bytes);
    }

    MutableArrayWrappingBytesValue(final byte[] bytes, final int offset, final int length) {
        super(bytes, offset, length);
    }

    @Override
    public void set(final int i, final byte b) {
        // Check bounds because while the array access would throw, the error message would be confusing
        // for the caller.
        checkElementIndex(i, size());
        this.bytes[offset + i] = b;
    }

    @Override
    public MutableBytesValue mutableSlice(final int i, final int length) {
        if (i == 0 && length == size()) return this;
        if (length == 0) return MutableBytesValue.EMPTY;

        checkElementIndex(i, size());
        checkArgument(
                i + length <= size(),
                "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
                length,
                size(),
                size() - i,
                i);
        return length == Bytes32.SIZE
                ? new MutableArrayWrappingBytes32(bytes, offset + i)
                : new MutableArrayWrappingBytesValue(bytes, offset + i, length);
    }

    @Override
    public void fill(final byte b) {
        Arrays.fill(bytes, offset, offset + length, b);
    }

    @Override
    public BytesValue copy() {
        // We *must* override this method because ArrayWrappingBytesValue assumes that it is the case.
        return new ArrayWrappingBytesValue(arrayCopy());
    }
}
