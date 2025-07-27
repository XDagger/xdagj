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

import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;

/**
 * Class for tracking XDAG network statistics
 */
@Getter
@Setter
public class XdagStats {

    public BigInteger difficulty;
    public BigInteger maxdifficulty;
    public long nblocks;          // Number of blocks
    public long totalnblocks;     // Total number of blocks
    public long nmain;            // Number of main blocks
    public long totalnmain;       // Total number of main blocks
    public int nhosts;            // Number of hosts
    public int totalnhosts;       // Total number of hosts
    public long nwaitsync;        // Number of blocks waiting for synchronization
    public long nnoref;           // Number of blocks with no references
    public long nextra;           // Number of extra blocks
    public long maintime;         // Timestamp of the main block
    public XAmount balance = XAmount.ZERO;

    private byte[] globalMiner;       // Global miner address
    private byte[] ourLastBlockHash;  // Hash of our last block

    /**
     * Default constructor initializing difficulties to zero
     */
    public XdagStats() {
        difficulty = BigInteger.ZERO;
        maxdifficulty = BigInteger.ZERO;
    }

    /**
     * Constructor for remote node statistics
     */
    public XdagStats(
            BigInteger maxdifficulty,
            long totalnblocks,
            long totalnmain,
            int totalnhosts,
            long maintime) {
        this.maxdifficulty = maxdifficulty;
        this.totalnblocks = totalnblocks;
        this.totalnmain = totalnmain;
        this.totalnhosts = totalnhosts;
        this.maintime = maintime;
    }

    /**
     * Copy constructor
     */
    public XdagStats(XdagStats xdagStats) {
        this.difficulty = xdagStats.difficulty;
        this.maxdifficulty = xdagStats.maxdifficulty;
        this.nblocks = xdagStats.nblocks;
        this.totalnblocks = xdagStats.totalnblocks;
        this.nmain = xdagStats.nmain;
        this.totalnmain = xdagStats.totalnmain;
        this.nhosts = xdagStats.nhosts;
        this.totalnhosts = xdagStats.totalnhosts;
    }

    /**
     * Initialize statistics with initial values
     */
    public void init(BigInteger diff, long totalnmain, long totalnblocks) {
        this.difficulty = this.maxdifficulty = diff;
        this.nblocks = this.totalnblocks = totalnblocks;
        this.nmain = this.totalnmain = totalnmain;
    }

    /**
     * Update statistics with data from remote node
     */
    public void update(XdagStats remoteXdagStats) {
        this.totalnhosts = Math.max(this.totalnhosts, remoteXdagStats.totalnhosts);
        this.totalnblocks = Math.max(this.totalnblocks, remoteXdagStats.totalnblocks);
        this.totalnmain = Math.max(this.totalnmain, remoteXdagStats.totalnmain);
        if (this.maxdifficulty != null && remoteXdagStats.maxdifficulty != null
                && remoteXdagStats.maxdifficulty.compareTo(this.maxdifficulty) > 0) {
            this.maxdifficulty = remoteXdagStats.maxdifficulty;
        }
    }

    /**
     * Update maximum difficulty if new value is higher
     */
    public void updateMaxDiff(BigInteger maxdifficulty) {
        if (this.getMaxdifficulty().compareTo(maxdifficulty) < 0) {
            this.maxdifficulty = maxdifficulty;
        }
    }

    /**
     * Update current difficulty if new value is higher
     */
    public void updateDiff(BigInteger difficulty) {
        if (this.difficulty.compareTo(difficulty) < 0) {
            this.difficulty = difficulty;
        }
    }

    @Override
    public String toString() {
        return "XdagStatus[nmain:" +
                this.nmain + ",totalmain:" + this.totalnmain + ",nblocks:" + this.nblocks + ",totalblocks:"
                + this.totalnblocks
                + "]";
    }
}
