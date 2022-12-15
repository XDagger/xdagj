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

package io.xdag.config;

import io.xdag.crypto.randomx.RandomXFlag;
import io.xdag.crypto.randomx.RandomXJNA;

public class RandomXConstants {

    /**
     * RandomX
     **/
    public static final long SEEDHASH_EPOCH_BLOCKS = 4096;
    public static final long SEEDHASH_EPOCH_LAG = 128;
    public static final long RANDOMX_FORK_HEIGHT = 1540096;
    public static final int XDAG_RANDOMX = 2;

    public static long SEEDHASH_EPOCH_TESTNET_BLOCKS = 2048;
    public static long SEEDHASH_EPOCH_TESTNET_LAG = 64;
    public static long RANDOMX_TESTNET_FORK_HEIGHT = 4096;// 196288

    //randomx full memory mode. With faster calculation speed, hugepage must be configured
    public static final int RANDOMX_FLAGS = RandomXJNA.INSTANCE.randomx_get_flags() +
            RandomXFlag.LARGE_PAGES.getValue() + RandomXFlag.FULL_MEM.getValue();

    //randomx default mode. Calculation speed is slow, no need to configure hugepage
    //public static final int RANDOMX_FLAGS = RandomXJNA.INSTANCE.randomx_get_flags();

}
