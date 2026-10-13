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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A simple encoder for writing primitive types and byte arrays to a byte stream
 */
public class SimpleEncoder {

    private final ByteArrayOutputStream out;

    /**
     * Creates a new encoder with a default buffer size of 512 bytes
     */
    public SimpleEncoder() {
        this.out = new ByteArrayOutputStream(512);
    }

    /**
     * Writes a field (byte array) to the stream
     * @param field byte array to write
     */
    public void writeField(byte[] field) {
        try {
            out.write(field);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    /**
     * Writes a signature (byte array) to the stream
     * @param sig signature byte array to write
     */
    public void writeSignature(byte[] sig) {
        try {
            out.write(sig);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    /**
     * Writes raw bytes to the stream
     * @param input byte array to write
     */
    public void write(byte[] input) {
        try {
            out.write(input);
        } catch (IOException e) {
            throw new SimpleCodecException(e);
        }
    }

    /**
     * Gets the current field index based on 32-byte field size
     * @return current field index
     */
    public int getWriteFieldIndex() {
        return getWriteIndex() / 32;
    }

    /**
     * Writes a boolean value as a single byte
     * @param b boolean value to write
     */
    public void writeBoolean(boolean b) {
        out.write(b ? 1 : 0);
    }

    /**
     * Writes a single byte
     * @param b byte to write
     */
    public void writeByte(byte b) {
        out.write(b);
    }

    /**
     * Writes a short value as 2 bytes
     * @param s short value to write
     */
    public void writeShort(short s) {
        out.write(0xFF & (s >>> 8));
        out.write(0xFF & s);
    }

    /**
     * Writes an integer value as 4 bytes
     * @param i integer value to write
     */
    public void writeInt(int i) {
        out.write(0xFF & (i >>> 24));
        out.write(0xFF & (i >>> 16));
        out.write(0xFF & (i >>> 8));
        out.write(0xFF & i);
    }

    /**
     * Writes a string as UTF-8 encoded bytes
     * @param s string to write
     */
    public void writeString(String s) {
        writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a long value as 8 bytes
     * @param l long value to write
     */
    public void writeLong(long l) {
        int i1 = (int) (l >>> 32);
        int i2 = (int) l;

        writeInt(i1);
        writeInt(i2);
    }

    /**
     * Encodes a byte array with length prefix
     * @param bytes byte array to encode
     * @param vlq if true, use variable length quantity encoding for length
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

    /**
     * Encodes a byte array using variable length quantity encoding for length
     * @param bytes byte array to encode
     */
    public void writeBytes(byte[] bytes) {
        writeBytes(bytes, true);
    }

    /**
     * Returns the encoded bytes as array
     * @return encoded byte array
     */
    public byte[] toBytes() {
        return out.toByteArray();
    }

    /**
     * Gets current write position
     * @return current write position
     */
    private int getWriteIndex() {
        return out.size();
    }

    /**
     * Writes a size value using variable length quantity encoding
     * @param size size value to write
     * @throws IllegalArgumentException if size is negative or too large
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
