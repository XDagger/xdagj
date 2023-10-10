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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import io.xdag.utils.exception.UnreachableException;

public class ForkSignalSet {

    public static final int MAX_PENDING_FORKS = 8;

    private final Set<Short> pendingForks;

    private ForkSignalSet(Set<Short> pendingForks) {
        this.pendingForks = pendingForks;
    }

    public static ForkSignalSet of(Fork... forks) {
        return of(Arrays.asList(forks));
    }

    public static ForkSignalSet of(Collection<Fork> forks) {
        if (forks.size() > MAX_PENDING_FORKS) {
            throw new UnreachableException("There must be no more than " + MAX_PENDING_FORKS + " pending forks.");
        }

        return new ForkSignalSet(forks.stream().map(Fork::id).collect(Collectors.toSet()));
    }

    public boolean contains(Fork fork) {
        return pendingForks.contains(fork.id());
    }

    public byte[] toBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeByte((byte) pendingForks.size());
        for (Short pendingFork : pendingForks) {
            encoder.writeShort(pendingFork);
        }
        return encoder.toBytes();
    }

    public static ForkSignalSet fromBytes(byte[] bytes) {
        SimpleDecoder decoder = new SimpleDecoder(bytes);
        byte size = decoder.readByte();
        Set<Short> forks = new HashSet<>();
        for (int i = 0; i < size; i++) {
            forks.add(decoder.readShort());
        }
        return new ForkSignalSet(forks);
    }
}
