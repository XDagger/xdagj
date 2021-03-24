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
import io.xdag.utils.discoveryutils.bytes.BytesValue;
import io.xdag.utils.discoveryutils.bytes.BytesValues;

import java.math.BigInteger;

public class BytesValueRLPInput extends AbstractRLPInput {

    // The RLP encoded data.
    private final BytesValue value;

    public BytesValueRLPInput(final BytesValue value, final boolean lenient) {
        this(value, lenient, true);
    }

    public BytesValueRLPInput(
            final BytesValue value, final boolean lenient, final boolean shouldFitExactly) {
        super(lenient);
        this.value = value;
        init(value.size(), shouldFitExactly);
    }

    @Override
    protected byte inputByte(final long offset) {
        return value.get(offset);
    }

    @Override
    protected BytesValue inputSlice(final long offset, final int length) {
        return value.slice(Math.toIntExact(offset), length);
    }

    @Override
    protected Bytes32 inputSlice32(final long offset) {
        return Bytes32.wrap(value, Math.toIntExact(offset));
    }

    @Override
    protected String inputHex(final long offset, final int length) {
        return value.slice(Math.toIntExact(offset), length).toString().substring(2);
    }

    @Override
    protected BigInteger getUnsignedBigInteger(final long offset, final int length) {
        return BytesValues.asUnsignedBigInteger(value.slice(Math.toIntExact(offset), length));
    }

    @Override
    protected int getInt(final long offset) {
        return value.getInt(Math.toIntExact(offset));
    }

    @Override
    protected long getLong(final long offset) {
        return value.getLong(Math.toIntExact(offset));
    }

    @Override
    public BytesValue raw() {
        return value;
    }
}
