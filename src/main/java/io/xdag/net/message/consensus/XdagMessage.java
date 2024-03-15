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
package io.xdag.net.message.consensus;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes32;

import io.xdag.utils.SimpleEncoder;
import io.xdag.core.XdagStats;
import io.xdag.net.NetDB;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.Numeric;
import io.xdag.utils.SimpleDecoder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class XdagMessage extends Message  {

    protected long starttime;

    protected long endtime;

    protected long random;

    protected Bytes32 hash;

    protected XdagStats xdagStats;

    protected NetDB remoteNetdb;

    protected NetDB localNetdb;

    public XdagMessage(MessageCode code, Class<?> responseMessageClass, byte[] body) {
        super(code, responseMessageClass);
        this.body = body;
        decode();
    }

    public XdagMessage(MessageCode code, Class<?> responseMessageClass, long starttime, long endtime, long random, XdagStats xdagStats, NetDB localNetdb) {
        super(code, responseMessageClass);

        this.starttime = starttime;
        this.endtime = endtime;
        this.random = random;
        this.xdagStats = xdagStats;
        this.localNetdb = localNetdb;

        this.hash = Bytes32.ZERO;
        SimpleEncoder enc = encode();
        this.body = enc.toBytes();
    }

    public XdagMessage(MessageCode code, Class<?> responseMessageClass, long starttime, long endtime, Bytes32 hash, XdagStats xdagStats,
            NetDB localNetdb) {
        super(code, responseMessageClass);

        this.starttime = starttime;
        this.endtime = endtime;
        this.hash = hash;
        this.xdagStats = xdagStats;
        this.localNetdb = localNetdb;

        SimpleEncoder enc = encode();
        this.body = enc.toBytes();
    }

    protected SimpleEncoder encode() {
        SimpleEncoder enc = new SimpleEncoder();

        enc.writeLong(starttime);
        enc.writeLong(endtime);
        enc.writeLong(random);
        enc.writeBytes(hash.toArray());

        enc.writeBytes(BytesUtils.bigIntegerToBytes(xdagStats.maxdifficulty, 16, false));

        enc.writeLong(xdagStats.totalnblocks);
        enc.writeLong(Math.max(xdagStats.totalnmain, xdagStats.nmain));
        enc.writeInt(xdagStats.totalnhosts);
        enc.writeLong(xdagStats.maintime);

        enc.writeBytes(localNetdb.getEncoded());
        return enc;
    }

    protected SimpleDecoder decode() {
        SimpleDecoder dec = new SimpleDecoder(this.body);

        this.starttime = dec.readLong();
        this.endtime = dec.readLong();
        this.random = dec.readLong();
        this.hash = Bytes32.wrap(dec.readBytes());

        BigInteger maxdifficulty = Numeric.toBigInt(dec.readBytes());
        long totalnblocks = dec.readLong();
        long totalnmains = dec.readLong();
        int totalnhosts = dec.readInt();
        long maintime = dec.readLong();

        xdagStats = new XdagStats(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

        byte[] netdb = dec.readBytes();
        localNetdb = new NetDB(netdb);
        return dec;
    }

}
