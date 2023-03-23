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

package io.xdag.core;

import io.xdag.utils.exception.SimpleCodecException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SimpleEncoder {

    private final ByteArrayOutputStream out;

    public SimpleEncoder() {
        this.out = new ByteArrayOutputStream(512);
    }

    public void writeField(byte[] field) {
        try {
            out.write(field);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    public void writeSignature(byte[] sig) {
        try {
            out.write(sig);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    public void write(byte[] input) {
        try {
            out.write(input);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    public int getWriteFieldIndex() {
        return getWriteIndex() / 32;
    }

    public void writeBoolean(boolean b) {
        out.write(b ? 1 : 0);
    }

    public void writeByte(byte b) {
        out.write(b);
    }

    public void writeShort(short s) {
        out.write(0xFF & (s >>> 8));
        out.write(0xFF & s);
    }

    public void writeInt(int i) {
        out.write(0xFF & (i >>> 24));
        out.write(0xFF & (i >>> 16));
        out.write(0xFF & (i >>> 8));
        out.write(0xFF & i);
    }

    public void writeString(String s) {
        writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    public void writeLong(long l) {
        int i1 = (int) (l >>> 32);
        int i2 = (int) l;

        writeInt(i1);
        writeInt(i2);
    }

    /**
     * Encode a byte array.
     *
     * @param bytes the byte array to encode
     * @param vlq should always be true unless we're providing pre-mainnet support.
     */
    public void writeBytes(byte[] bytes, boolean vlq) {
        if (vlq) {
            writeSize(bytes.length);
        } else {
            writeInt(bytes.length);
        }

        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    public void writeBytes(byte[] bytes) {
        writeBytes(bytes, true);
    }

    public byte[] toBytes() {
        return out.toByteArray();
    }

    private int getWriteIndex() {
        return out.size();
    }

    /**
     * Writes a size into the output byte array.
     *
     * @throws IllegalArgumentException when the input size is negative
     */
    protected void writeSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size can't be negative: " + size);
        } else if (size > 0x0FFFFFFF) {
            throw new IllegalArgumentException("Size can't be larger than 0x0FFFFFFF: " + size);
        }

        int[] buf = new int[4];
        int i = buf.length;
        do {
            buf[--i] = size & 0x7f;
            size >>>= 7;
        } while (size > 0);

        while (i < buf.length) {
            if (i != buf.length - 1) {
                out.write((byte) (buf[i++] | 0x80));
            } else {
                out.write((byte) buf[i++]);
            }
        }
    }
}
