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
package io.xdag.discovery.peers;

import io.xdag.utils.discoveryutils.bytes.BytesValue;

import java.util.Arrays;

public class PeerDistanceCalculator {

    /**
     * Calculates the XOR distance between two values.
     *
     * @param v1 the first value
     * @param v2 the second value
     * @return the distance
     */
    static int distance(final BytesValue v1, final BytesValue v2) {
        assert (v1.size() == v2.size());
        final byte[] v1b = v1.extractArray();
        final byte[] v2b = v2.extractArray();
        if (Arrays.equals(v1b, v2b)) {
            return 0;
        }
        int distance = v1b.length * 8;
        for (int i = 0; i < v1b.length; i++) {
            final byte xor = (byte) (0xff & (v1b[i] ^ v2b[i]));
            if (xor == 0) {
                distance -= 8;
            } else {
                int p = 7;
                while (((xor >> p--) & 0x01) == 0) {
                    distance--;
                }
                break;
            }
        }
        return distance;
    }
}
