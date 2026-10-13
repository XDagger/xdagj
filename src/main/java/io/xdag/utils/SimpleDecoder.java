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

/**
 * A simple decoder for reading primitive types and byte arrays from a byte array
 */
public class SimpleDecoder {

    private static final String ENCODING = "UTF-8";

    private final byte[] in;
    private final int from;
    private final int to;

    private int index;

    /**
     * Creates a decoder for the entire byte array
     * @param in byte array to decode
     */
    public SimpleDecoder(byte[] in) {
        this(in, 0, in.length);
    }

    /**
     * Creates a decoder starting from specified position
     * @param in byte array to decode
     * @param from starting position
     */
    public SimpleDecoder(byte[] in, int from) {
        this(in, from, in.length);
    }

    /**
     * Creates a decoder for a range in the byte array
     * @param in byte array to decode
     * @param from starting position
     * @param to ending position
     */
    public SimpleDecoder(byte[] in, int from, int to) {
        this.in = in;
        this.from = from;
        this.to = to;
        this.index = from;
    }

    /**
     * Reads a boolean value
     * @return decoded boolean value
     */
    public boolean readBoolean() {
        require(1);
        return in[index++] != 0;
    }

    /**
     * Reads a single byte
     * @return decoded byte value
     */
    public byte readByte() {
        require(1);
        return in[index++];
    }

    /**
     * Reads a short value (2 bytes)
     * @return decoded short value
     */
    public short readShort() {
        require(2);
        return (short) ((in[index++] & 0xFF) << 8 | (in[index++] & 0xFF));
    }

    /**
     * Reads an integer value (4 bytes)
     * @return decoded integer value
     */
    public int readInt() {
        require(4);
        return in[index++] << 24 | (in[index++] & 0xFF) << 16 | (in[index++] & 0xFF) << 8 | (in[index++] & 0xFF);
    }

    /**
     * Reads a long value (8 bytes)
     * @return decoded long value
     */
    public long readLong() {
        int i1 = readInt();
        int i2 = readInt();

        return (unsignedInt(i1) << 32) | unsignedInt(i2);
    }

    /**
     * Reads a byte array with length prefix
     * @param vlq if true, use variable length quantity encoding for length
     * @return decoded byte array
     */
    public byte[] readBytes(boolean vlq) {
        int len = vlq ? readSize() : readInt();

        require(len);
        byte[] buf = new byte[len];
        System.arraycopy(in, index, buf, 0, len);
        index += len;

        return buf;
    }

    /**
     * Reads a byte array using variable length quantity encoding for length
     * @return decoded byte array
     */
    public byte[] readBytes() {
        return readBytes(true);
    }

    /**
     * Reads a UTF-8 encoded string
     * @return decoded string
     * @throws SimpleCodecException if encoding is not supported
     */
    public String readString() {
        try {
            return new String(readBytes(), ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new SimpleCodecException(e);
        }
    }

    /**
     * Reads a size value using variable length quantity encoding
     * @return decoded size value
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
     * Checks if there are enough remaining bytes to read
     * @param n number of bytes required
     * @throws IndexOutOfBoundsException if there are not enough bytes
     */
    protected void require(int n) {
        if (to - index < n) {
            String msg = String.format("input [%d, %d], require: [%d %d]", from, to, index, index + n);
            throw new IndexOutOfBoundsException(msg);
        }
    }

    /**
     * Converts a signed integer to an unsigned long value
     * @param i signed integer value
     * @return unsigned long value
     */
    protected long unsignedInt(int i) {
        return i & 0x00000000ffffffffL;
    }
}
