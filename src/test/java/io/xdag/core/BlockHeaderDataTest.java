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
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bouncycastle.util.encoders.Hex;
import org.junit.BeforeClass;
import org.junit.Test;

public class BlockHeaderDataTest {

    private static final Fork[] onePendingFork = new Fork[1];
    private static final Fork[] eightPendingForks = new Fork[8];

    private static final byte[] v1_0xPendingForkEncoded = Hex.decode("010100");

    private static final byte[] v1_1xPendingForkEncoded = Hex.decode("0103010001");

    private static final byte[] v1_8xPendingForksEncoded = Hex.decode("01110800010002000300040005000600070008");

    @BeforeClass
    public static void beforeClass() {
        onePendingFork[0] = Fork.APOLLO_FORK;

        for (short i = 1; i <= 8; i++) {
            Fork a = mock(Fork.class);
            when(a.id()).thenReturn(i);
            eightPendingForks[i - 1] = a;
        }
    }

    @Test
    public void testV0HeaderData() {
        BlockHeaderData blockHeaderData = new BlockHeaderData();
        assertThat(blockHeaderData.toBytes()).hasSize(0);
        assertFalse(blockHeaderData.parseForkSignals().contains(Fork.APOLLO_FORK));
    }

    @Test
    public void testV1HeaderDataEncoding() {
        // zero pending fork
        BlockHeaderData blockHeaderData = new BlockHeaderData(ForkSignalSet.of());
        assertThat(blockHeaderData.toBytes()).isEqualTo(v1_0xPendingForkEncoded).hasSize(3);
        // writeByte(1) +
        // writeSize(1) +
        // writeByte(1)

        // one pending fork
        blockHeaderData = new BlockHeaderData(ForkSignalSet.of(onePendingFork));
        assertThat(blockHeaderData.toBytes()).isEqualTo(v1_1xPendingForkEncoded).hasSize(5);
        // writeByte(1) +
        // writeSize(1) +
        // writeByte(1) +
        // writeShort(2)

        // eight pending forks
        blockHeaderData = new BlockHeaderData(ForkSignalSet.of(eightPendingForks));
        assertThat(blockHeaderData.toBytes()).isEqualTo(v1_8xPendingForksEncoded).hasSize(19);
        // writeByte(1) +
        // writeSize(1) +
        // writeByte(1) +
        // writeShort(2) * 8
    }
}
