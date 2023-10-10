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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.xdag.Network;
import io.xdag.core.Genesis;
import io.xdag.core.state.ByteArray;
import io.xdag.utils.BytesUtils;
import org.bouncycastle.util.encoders.Hex;

public class GenesisTest {

    private static final byte[] ZERO_ADDRESS = Hex.decode("0000000000000000000000000000000000000000");
    private static final byte[] ZERO_HASH = BytesUtils.EMPTY_HASH;

    Genesis genesis;

    @Before
    public void setUp() {
        genesis = Genesis.load(Network.DEVNET);
    }

    @Test
    public void testIsGenesis() {
        assertEquals(0, genesis.getNumber());
    }

    @Test
    public void testBlock() {
        assertEquals(0, genesis.getNumber());
        assertArrayEquals(ZERO_ADDRESS, genesis.getCoinbase());
        assertArrayEquals(ZERO_HASH, genesis.getParentHash());
        assertTrue(genesis.getTimestamp() > 0);
        assertFalse(Arrays.equals(ZERO_ADDRESS, genesis.getHash()));
    }

    @Test
    public void testSnapshots() {
        Map<ByteArray, Genesis.XSnapshot> snapshots = genesis.getSnapshots();

        assertFalse(snapshots.isEmpty());
        for (Genesis.XSnapshot s : snapshots.values()) {
            assertTrue(s.getAmount().isPositive());
        }
    }

    @Test
    public void testConfig() {
        assertTrue(genesis.getConfig().isEmpty());
    }
}
