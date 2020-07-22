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
package io.xdag.net.message;

import java.math.BigInteger;

public class NetStatus {
    protected BigInteger difficulty;
    protected BigInteger maxdifficulty;
    protected long nblocks;
    protected long totalnblocks;
    protected long nmain;
    protected long totalnmain;
    protected int nhosts;
    protected int totalnhosts;

    protected long maintime;

    public NetStatus() {
    }

    /** 用于记录remote node的 */
    public NetStatus(
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

    public NetStatus(NetStatus netStatus) {
        this.difficulty = netStatus.difficulty;
        this.maxdifficulty = netStatus.maxdifficulty;
        this.nblocks = netStatus.nblocks;
        this.totalnblocks = netStatus.totalnblocks;
        this.nmain = netStatus.nmain;
        this.totalnmain = netStatus.totalnmain;
        this.nhosts = netStatus.nhosts;
        this.totalnhosts = netStatus.totalnhosts;
    }

    public void init(BigInteger diff, long totalnmain, long totalnblocks) {
        this.difficulty = this.maxdifficulty = diff;
        this.nblocks = this.totalnblocks = totalnblocks;
        this.nmain = this.totalnmain = totalnmain;
    }

    public void updateNetStatus(NetStatus remoteNetStatus) {
        this.totalnhosts = Math.max(this.totalnhosts, remoteNetStatus.totalnhosts);
        this.totalnblocks = Math.max(this.totalnblocks, remoteNetStatus.totalnblocks);
        this.totalnmain = Math.max(this.totalnmain, remoteNetStatus.totalnmain);
        if (remoteNetStatus.maxdifficulty.compareTo(this.maxdifficulty) > 0) {
            this.maxdifficulty = remoteNetStatus.maxdifficulty;
        }
    }

    public BigInteger getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(BigInteger difficulty) {
        this.difficulty = difficulty;
    }

    public BigInteger getMaxdifficulty() {
        if (maxdifficulty.compareTo(difficulty) < 0) {
            maxdifficulty = BigInteger.valueOf(difficulty.longValue());
        }
        return maxdifficulty;
    }

    public void setMaxdifficulty(BigInteger maxdifficulty) {
        this.maxdifficulty = maxdifficulty;
    }

    public long getNblocks() {
        return nblocks;
    }

    public void setNblocks(long nblocks) {
        this.nblocks = nblocks;
    }

    public long getTotalnblocks() {
        if (totalnblocks < nblocks) {
            totalnblocks = nblocks;
        }
        return totalnblocks;
    }

    public void setTotalnblocks(long totalnblocks) {
        this.totalnblocks = totalnblocks;
    }

    public long getNmain() {
        return nmain;
    }

    public void setNmain(long nmain) {
        this.nmain = nmain;
    }

    public long getTotalnmain() {
        if (totalnmain < nmain) {
            totalnmain = nmain;
        }
        return totalnmain;
    }

    public void setTotalnmain(long totalnmain) {
        this.totalnmain = totalnmain;
    }

    public int getNhosts() {
        return nhosts;
    }

    public void setNhosts(int nhosts) {
        this.nhosts = nhosts;
    }

    public int getTotalnhosts() {
        return totalnhosts;
    }

    public void setTotalnhosts(int totalnhosts) {
        this.totalnhosts = totalnhosts;
    }

    public long getMaintime() {
        return maintime;
    }

    public void setMaintime(long maintime) {
        this.maintime = maintime;
    }

    @Override
    public String toString() {
        return "NetStatus:[ total block size:"
                + this.totalnblocks
                + ",total mainblock size:"
                + this.totalnmain
                + ",max diff:"
                + this.maxdifficulty.toString(16)
                + " ]";
    }

    /** 仅在新block加入时执行 可以不考虑并行的情况 */
    public void incMain() {
        this.nmain++;
    }

    public void decMain() {
        this.nmain--;
    }

    public void incBlock() {
        this.nblocks++;
    }

    public void decBlock() {
        this.nblocks--;
    }
}
