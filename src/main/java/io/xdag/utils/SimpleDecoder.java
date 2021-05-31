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

import io.xdag.utils.exception.SimpleCodecException;

import java.io.UnsupportedEncodingException;

public class SimpleDecoder {
    private static final String ENCODING = "UTF-8";

    private final byte[] in;
    private final int from;
    private final int to;

    private int index;

    public SimpleDecoder(byte[] in) {
        this(in, 0, in.length);
    }

    public SimpleDecoder(byte[] in, int from) {
        this(in, from, in.length);
    }

    public SimpleDecoder(byte[] in, int from, int to) {
        this.in = in;
        this.from = from;
        this.to = to;
        this.index = from;
    }

    public boolean readBoolean() {
        require(1);
        return in[index++] != 0;
    }

    public byte readByte() {
        require(1);
        return in[index++];
    }

    public short readShort() {
        require(2);
        return (short) ((in[index++] & 0xFF) << 8 | (in[index++] & 0xFF));
    }

    public int readInt() {
        require(4);
        return in[index++] << 24 | (in[index++] & 0xFF) << 16 | (in[index++] & 0xFF) << 8 | (in[index++] & 0xFF);
    }

    public long readLong() {
        int i1 = readInt();
        int i2 = readInt();

        return (unsignedInt(i1) << 32) | unsignedInt(i2);
    }

    /**
     * Decode a byte array.
     *
     * @param vlq
     *            should always be true unless we're providing pre-mainnet support.
     */
    public byte[] readBytes(boolean vlq) {
        int len = vlq ? readSize() : readInt();

        require(len);
        byte[] buf = new byte[len];
        System.arraycopy(in, index, buf, 0, len);
        index += len;

        return buf;
    }

    public byte[] readBytes() {
        return readBytes(true);
    }

    public String readString() {
        try {
            return new String(readBytes(), ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new SimpleCodecException(e);
        }
    }

    public int getReadIndex() {
        return index;
    }

    /**
     * Reads size from the input.
     *
     * @return
     */
    protected int readSize() {
        int size = 0;
        for (int i = 0; i < 4; i++) {
            require(1);
            byte b = in[index++];

            size = (size << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return size;
    }

    /**
     * Checks if the required bytes is satisfied.
     *
     * @param n
     */
    protected void require(int n) {
        if (to - index < n) {
            String msg = String.format("input [%d, %d], require: [%d %d]", from, to, index, index + n);
            throw new IndexOutOfBoundsException(msg);
        }
    }

    /**
     * Re-interprets an integer as unsigned integer.
     *
     * @param i
     *            an integer
     * @return the unsigned value, represented in long
     */
    protected long unsignedInt(int i) {
        return i & 0x00000000ffffffffL;
    }
}
