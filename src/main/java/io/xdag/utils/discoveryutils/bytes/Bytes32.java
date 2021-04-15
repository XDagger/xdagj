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

import io.xdag.utils.discoveryutils.bytes.uint.Int256;
import io.xdag.utils.discoveryutils.bytes.uint.UInt256;
import io.xdag.utils.discoveryutils.bytes.uint.UInt256Bytes;

import static com.google.common.base.Preconditions.checkArgument;

public interface Bytes32 extends BytesValue {
    int SIZE = 32;

    Bytes32 FALSE = UInt256Bytes.of(0);
    Bytes32 TRUE = UInt256Bytes.of(1);
    Bytes32 ZERO = wrap(new byte[32]);

    /**
     * Wraps the provided byte array, which must be of length 32, as a {@link Bytes32}.
     *
     * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
     * will be reflected in the returned value.
     *
     * @param bytes The bytes to wrap.
     * @return A {@link Bytes32} wrapping {@code value}.
     * @throws IllegalArgumentException if {@code value.length != 32}.
     */
    static Bytes32 wrap(final byte[] bytes) {
        checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
        return wrap(bytes, 0);
    }

    /**
     * Wraps a slice/sub-part of the provided array as a {@link Bytes32}.
     *
     * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
     * within the wrapped parts will be reflected in the returned value.
     *
     * @param bytes The bytes to wrap.
     * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
     *     value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
     * @return A {@link Bytes32} that exposes the bytes of {@code value} from {@code offset}
     *     (inclusive) to {@code offset + 32} (exclusive).
     * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
     *     value.length)}.
     * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 32 &gt; value.length}.
     */
    static Bytes32 wrap(final byte[] bytes, final int offset) {
        return new ArrayWrappingBytes32(bytes, offset);
    }

    /**
     * Wraps a slice/sub-part of the provided value as a {@link Bytes32}.
     *
     * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
     * within the wrapped parts will be reflected in the returned value.
     *
     * @param bytes The bytes to wrap.
     * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
     *     value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
     * @return A {@link Bytes32} that exposes the bytes of {@code value} from {@code offset}
     *     (inclusive) to {@code offset + 32} (exclusive).
     * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
     *     value.size())}.
     * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 32 &gt; value.size()}.
     */
    static Bytes32 wrap(final BytesValue bytes, final int offset) {
        final BytesValue slice = bytes.slice(offset, Bytes32.SIZE);
        return slice instanceof Bytes32 ? (Bytes32) slice : new WrappingBytes32(slice);
    }

    /**
     * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes32}
     *
     * @param value The bytes value pad.
     * @return A {@link Bytes32} that exposes the left-padded bytes of {@code value}.
     * @throws IllegalArgumentException if {@code value.size() &gt; 32}.
     */
    static Bytes32 leftPad(final BytesValue value) {
        checkArgument(
                value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

        final MutableBytes32 bytes = MutableBytes32.create();
        value.copyTo(bytes, SIZE - value.size());
        return bytes;
    }

    /**
     * Parse an hexadecimal string into a {@link Bytes32}.
     *
     * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
     * behave exactly as if it had an additional 0 in front.
     *
     * @param str The hexadecimal string to parse, which may or may not start with "0x". That
     *     representation may contain less than 32 bytes, in which case the result is left padded with
     *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
     * @return The value corresponding to {@code str}.
     * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
     *     representation or contains more than 32 bytes.
     */
    static Bytes32 fromHexStringLenient(final String str) {
        return wrap(BytesValues.fromRawHexString(str, SIZE, true));
    }

    /**
     * Parse an hexadecimal string into a {@link Bytes32}.
     *
     * <p>This method is strict in that {@code str} must of an even length.
     *
     * @param str The hexadecimal string to parse, which may or may not start with "0x". That
     *     representation may contain less than 32 bytes, in which case the result is left padded with
     *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
     * @return The value corresponding to {@code str}.
     * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
     *     representation, is of an odd length, or contains more than 32 bytes.
     */
    static Bytes32 fromHexString(final String str) {
        return wrap(BytesValues.fromRawHexString(str, SIZE, false));
    }

    /**
     * Parse an hexadecimal string into a {@link Bytes32}.
     *
     * <p>This method is extra strict in that {@code str} must of an even length and the provided
     * representation must have exactly 32 bytes.
     *
     * @param str The hexadecimal string to parse, which may or may not start with "0x".
     * @return The value corresponding to {@code str}.
     * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
     *     representation, is of an odd length or does not contain exactly 32 bytes.
     */
    static Bytes32 fromHexStringStrict(final String str) {
        return wrap(BytesValues.fromRawHexString(str, -1, false));
    }

    @Override
    default int size() {
        return SIZE;
    }

    @Override
    Bytes32 copy();

    @Override
    MutableBytes32 mutableCopy();

    /**
     * Return a {@link UInt256} that wraps these bytes.
     *
     * <p>Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if
     * this value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
     *
     * @return A {@link UInt256} that wraps these bytes.
     */
    default UInt256 asUInt256() {
        return UInt256.wrap(this);
    }

    /**
     * Return a {@link Int256} that wraps these bytes.
     *
     * <p>Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if
     * this value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
     *
     * @return A {@link Int256} that wraps these bytes.
     */
    default Int256 asInt256() {
        return Int256.wrap(this);
    }
}
