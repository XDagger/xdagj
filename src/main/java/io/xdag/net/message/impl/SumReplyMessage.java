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
package io.xdag.net.message.impl;

import static io.xdag.net.message.XdagMessageCodes.SUMS_REPLY;

import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetDB;
import io.xdag.core.XdagStats;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import java.math.BigInteger;

public class SumReplyMessage extends AbstractMessage {

    byte[] sums;

    public SumReplyMessage(long endtime, long random, XdagStats xdagStats, byte[] sums) {
        super(SUMS_REPLY, 1, endtime, random, xdagStats);
        this.sums = sums;
        System.arraycopy(BytesUtils.longToBytes(random, true), 0, encoded, 32, 8);
        System.arraycopy(sums, 0, encoded, 256, 256);
        updateCrc();
    }

    public SumReplyMessage(byte[] encoded) {
        super(encoded);
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.SUMS_REPLY;
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        return "["
                + this.getCommand().name()
                + " starttime="
                + starttime
                + " endtime="
                + this.endtime
                + " netstatus="
                + xdagStats;
    }

    public byte[] getSum() {
        parse();
        return sums;
    }

    @Override
    public void parse() {
        if (parsed) {
            return;
        }
        starttime = BytesUtils.bytesToLong(encoded, 16, true);
        endtime = BytesUtils.bytesToLong(encoded, 24, true);
        random = BytesUtils.bytesToLong(encoded, 32, true);
        BigInteger maxdifficulty = BytesUtils.bytesToBigInteger(encoded, 80, true);
        long totalnblocks = BytesUtils.bytesToLong(encoded, 104, true);
        long totalnmains = BytesUtils.bytesToLong(encoded, 120, true);
        int totalnhosts = BytesUtils.bytesToInt(encoded, 132, true);
        long maintime = BytesUtils.bytesToLong(encoded, 136, true);
        xdagStats = new XdagStats(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

        // test netdb
        int length = 6;
        // 80 是sizeof(xdag_stats)
        byte[] netdb = new byte[length * 32 - 80];
        System.arraycopy(encoded, 144, netdb, 0, length * 32 - 80);
        netDB = new NetDB(netdb);

        sums = new byte[256];
        System.arraycopy(encoded, 256, sums, 0, 256);
        parsed = true;
    }
}
