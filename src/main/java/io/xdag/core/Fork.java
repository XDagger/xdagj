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

import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;

/**
 * This class represents a Validator Activated Soft Fork inspired by Miner Activated Soft Fork (MASF) in BIP-34.
 */
public enum Fork implements Comparable<Fork> {

    APOLLO_FORK((short) 1, 100, 101);

    /**
     * A unique number of this fork.
     */
    private final short id;

    /**
     * The number of blocks which are required to activate this fork.
     */
    private final int blocksRequired;

    /**
     * The number of blocks to check fork signal in block header.
     */
    private final int blocksToCheck;

    Fork(short id, int blocksRequired, int blocksToCheck) {
        this.id = id;
        this.blocksRequired = blocksRequired;
        this.blocksToCheck = blocksToCheck;
    }

    public static Fork of(short id) {
        for (Fork f : Fork.values()) {
            if (f.id == id) {
                return f;
            }
        }
        return null;
    }

    public short id() {
        return id;
    }

    public int blocksRequired() {
        return blocksRequired;
    }

    public int blocksToCheck() {
        return blocksToCheck;
    }

    public static class Activation {

        public final Fork fork;

        public final long effectiveFrom;

        public Activation(Fork fork, long effectiveFrom) {
            this.fork = fork;
            this.effectiveFrom = effectiveFrom;
        }

        public byte[] toBytes() {
            SimpleEncoder encoder = new SimpleEncoder();
            encoder.writeShort(fork.id);
            encoder.writeLong(effectiveFrom);
            return encoder.toBytes();
        }

        public static Activation fromBytes(byte[] bytes) {
            SimpleDecoder decoder = new SimpleDecoder(bytes);
            Fork fork = Fork.of(decoder.readShort());
            long activatedAt = decoder.readLong();
            return new Activation(fork, activatedAt);
        }
    }
}
