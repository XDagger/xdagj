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

package io.xdag.consensus;

import io.xdag.crypto.randomx.RandomXTemplate;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the memory state for RandomX, holding 
 * relevant parameters for the computation.
 */
@Getter
@Setter
public class RandomXMemory {

    protected byte[] seed; // The seed used for RandomX
    protected long seedHeight; // The height at which the seed was created
    protected long seedTime; // The time when the seed was created
    protected long switchTime; // The time when the algorithm switched
    protected int isSwitched; // Flag to indicate if the algorithm has switched

    protected RandomXTemplate poolTemplate; // Template for the pool
    protected RandomXTemplate blockTemplate; // Template for the block

    public RandomXMemory() {
        this.switchTime = -1; // Initialize switchTime to -1 indicating no switch
        this.isSwitched = -1; // Initialize isSwitched to -1 indicating not switched
    }
}
