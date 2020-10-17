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

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class XdagStats {
    public BigInteger difficulty;
    public BigInteger maxdifficulty;
    public long nblocks;
    public long totalnblocks;
    public long nmain;
    public long totalnmain;
    public int nhosts;
    public int totalnhosts;
    public long nwaitsync;
    public long nnoref;
    public long nextra;
    public long maintime;
    public long balance;

    private BigInteger topDiff;
    private byte[] topMainChain;
    private byte[] preTop;
    private BigInteger preTopDiff;
    private byte[] globalMiner;
    private byte[] ourLastBlockHash;

    public XdagStats(){
        difficulty = BigInteger.ZERO;
        maxdifficulty = BigInteger.ZERO;
        topDiff = BigInteger.ZERO;
        preTopDiff = BigInteger.ZERO;
    }

    /** 用于记录remote node的 */
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

    public void init(BigInteger diff, long totalnmain, long totalnblocks) {
        this.difficulty = this.maxdifficulty = diff;
        this.nblocks = this.totalnblocks = totalnblocks;
        this.nmain = this.totalnmain = totalnmain;
    }

    public void update(XdagStats remoteXdagStats) {
        this.totalnhosts = Math.max(this.totalnhosts, remoteXdagStats.totalnhosts);
        this.totalnblocks = Math.max(this.totalnblocks, remoteXdagStats.totalnblocks);
        this.totalnmain = Math.max(this.totalnmain, remoteXdagStats.totalnmain);
        if (this.maxdifficulty != null && remoteXdagStats.maxdifficulty != null && remoteXdagStats.maxdifficulty.compareTo(this.maxdifficulty) > 0) {
            this.maxdifficulty = remoteXdagStats.maxdifficulty;
        }
    }

    public BigInteger getMaxdifficulty() {
        return maxdifficulty;
    }

//    @Override
//    public String toString() {
//        return "NetStatus:[ total block size:"
//                + this.totalnblocks
//                + ",total mainblock size:"
//                + this.totalnmain
//                + ",max diff:"
//                + this.maxdifficulty.toString(16)
//                + " ]";
//    }

//    /** 仅在新block加入时执行 可以不考虑并行的情况 */
//    public void incMain() {
//        this.nmain++;
//    }
//
//    public void decMain() {
//        this.nmain--;
//    }
//
//    public void incBlock() {
//        this.nblocks++;
//    }
//
//    public void decBlock() {
//        this.nblocks--;
//    }
//
//    public void incWaitsync() {
//        this.nwaitsync++;
//    }
//
//    public void decWaitsync() {
//        this.nwaitsync--;
//    }
}
