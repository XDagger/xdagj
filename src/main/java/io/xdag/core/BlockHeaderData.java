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


import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlockHeaderData {

    public static final int MAX_SIZE = 32;

    private final byte[] raw;

    public BlockHeaderData() {
        this(BytesUtils.EMPTY_BYTES);
    }

    public BlockHeaderData(byte[] raw) {
        if (raw == null || raw.length > MAX_SIZE) {
            throw new IllegalArgumentException("Invalid header data");
        }
        this.raw = raw.clone();
    }

    public BlockHeaderData(ForkSignalSet forks) {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeByte((byte) 0x01);
        encoder.writeBytes(forks.toBytes());
        this.raw = encoder.toBytes();
    }

    /**
     * Parse the fork signal set in this header, in a passive and exception-free
     * way.
     *
     * @return a fork signal set
     */
    public ForkSignalSet parseForkSignals() {
        if (raw.length > 0 && raw[0] == 0x01) {
            try {
                SimpleDecoder decoder = new SimpleDecoder(raw);
                decoder.readByte();
                return ForkSignalSet.fromBytes(decoder.readBytes());
            } catch (Exception e) {
                log.debug("Failed to parse fork signals", e);
            }
        }

        return ForkSignalSet.of();
    }

    public byte[] toBytes() {
        return this.raw.clone();
    }
}
