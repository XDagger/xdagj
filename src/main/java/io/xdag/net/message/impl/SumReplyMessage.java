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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import java.math.BigInteger;
import java.nio.ByteOrder;

public class SumReplyMessage extends AbstractMessage {

    MutableBytes sums;

    public SumReplyMessage(long endtime, long random, XdagStats xdagStats, MutableBytes sums) {
        super(SUMS_REPLY, 1, endtime, random, xdagStats);
        this.sums = sums;
//        System.arraycopy(BytesUtils.longToBytes(random, true), 0, encoded, 32, 8);
        encoded.set(32, Bytes.wrap(BytesUtils.longToBytes(random, true)));
//        System.arraycopy(sums, 0, encoded, 256, 256);
        encoded.set(256, Bytes.wrap(sums));
        updateCrc();
    }

    public SumReplyMessage(MutableBytes encoded) {
        super(encoded);
    }

    @Override
    public Bytes getEncoded() {
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

    public Bytes getSum() {
        parse();
        return sums;
    }

    @Override
    public void parse() {
        if (parsed) {
            return;
        }

        this.starttime = encoded.getLong(16, ByteOrder.LITTLE_ENDIAN);
        this.endtime = encoded.getLong(24, ByteOrder.LITTLE_ENDIAN);
        this.random = encoded.getLong(32,ByteOrder.LITTLE_ENDIAN);
        BigInteger maxdifficulty = encoded.slice(80, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
        long totalnblocks = encoded.getLong(104, ByteOrder.LITTLE_ENDIAN);
        long totalnmains = encoded.getLong(120, ByteOrder.LITTLE_ENDIAN);
        int totalnhosts = encoded.getInt(132,ByteOrder.LITTLE_ENDIAN);
        long maintime = encoded.getLong(136, ByteOrder.LITTLE_ENDIAN);
        xdagStats = new XdagStats(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

        // test netdb
        int length = 6;
        // 80 是sizeof(xdag_stats)
//        byte[] netdb = new byte[length * 32 - 80];
        MutableBytes netdb = MutableBytes.create(length * 32 - 80);
//        System.arraycopy(encoded.toArray(), 144, netdb, 0, length * 32 - 80);
//        netdb.set(0, encoded);
        netdb.set(0, encoded.slice(144,length*32-80));
        netDB = new NetDB(netdb.toArray());

//        sums = new byte[256];
        sums = MutableBytes.create(256);
//        System.arraycopy(encoded.toArray(), 256, sums, 0, 256);
        sums.set(0, encoded.slice(256, 256));
        parsed = true;
    }
}
