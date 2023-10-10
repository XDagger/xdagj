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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bouncycastle.util.encoders.Hex;
import org.junit.BeforeClass;
import org.junit.Test;

import io.xdag.core.Fork;
import io.xdag.core.ForkSignalSet;

public class ForkSignalSetTest {

    private static final Fork[] eightPendingForks = new Fork[8];

    private static final byte[] eightPendingForksEncoded = Hex.decode("0800010002000300040005000600070008");

    private static final Fork[] onePendingFork = new Fork[1];

    private static final byte[] onePendingForkEncoded = Hex.decode("010001");

    @BeforeClass
    public static void beforeClass() {
        for (short i = 1; i <= 8; i++) {
            Fork a = mock(Fork.class);
            when(a.id()).thenReturn(i);
            eightPendingForks[i - 1] = a;
        }

        onePendingFork[0] = Fork.APOLLO_FORK;
    }

    @Test
    public void testForkSignalSetCodec_onePendingFork() {
        // test decoding
        ForkSignalSet set = ForkSignalSet.fromBytes(onePendingForkEncoded);
        assertTrue(set.contains(onePendingFork[0]));

        // test encoding
        assertArrayEquals(onePendingForkEncoded, ForkSignalSet.of(onePendingFork).toBytes());
    }

    @Test
    public void testForkSignalSetCodec_eightPendingForks() {
        // test decoding
        ForkSignalSet set = ForkSignalSet.fromBytes(eightPendingForksEncoded);
        for (Fork f : eightPendingForks) {
            set.contains(f);
        }

        // test encoding
        set = ForkSignalSet.of(eightPendingForks);
        assertThat(set.toBytes()).hasSize(ForkSignalSet.MAX_PENDING_FORKS * 2 + 1).isEqualTo(eightPendingForksEncoded);
    }
}
