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
package io.xdag.utils.discoveryutils;


import io.xdag.utils.discoveryutils.bytes.BytesValue;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface RLPInput {

    boolean isDone();

    boolean nextIsList();

    boolean nextIsNull();

    boolean isEndOfCurrentList();

    void skipNext();


    int enterList();

    void leaveList();


    void leaveList(boolean ignoreRest);

    long readLongScalar();

    BigInteger readBigIntegerScalar();

    short readShort();

    default int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    InetAddress readInetAddress();

    BytesValue readBytesValue();


    BytesValue raw();

    void reset();

    default <T> List<T> readList(final Function<RLPInput, T> valueReader) {
        final int size = enterList();
        final List<T> res = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            try {
                res.add(valueReader.apply(this));
            } catch (final Exception ignored) {

            }
        }
        leaveList();
        return res;
    }
}
