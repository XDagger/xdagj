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

import static io.xdag.config.Constants.DNET_PKT_XDAG;
import static io.xdag.core.XdagBlock.XDAG_BLOCK_SIZE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_NONCE;
import static io.xdag.net.message.XdagMessageCodes.SUMS_REPLY;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

import io.xdag.core.XdagStats;

import io.xdag.utils.BytesUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.util.encoders.Hex;

@EqualsAndHashCode(callSuper = false)
public abstract class AbstractMessage extends Message {
    @Getter
    @Setter
    protected long starttime;

    @Getter
    @Setter
    protected long endtime;

    @Getter
    @Setter
    protected long random;

    @Getter
    @Setter
    protected Bytes32 hash;

    /** 获取对方节点的netstatus */
    @Setter
    protected XdagStats xdagStats;
    /** 获取对方节点的netdb */
    protected NetDB netDB;
    protected XdagMessageCodes codes;

    public AbstractMessage(
            XdagMessageCodes type, long starttime, long endtime, long random, XdagStats xdagStats) {
        parsed = true;
        this.starttime = starttime;
        this.endtime = endtime;
        this.random = random;
        this.xdagStats = xdagStats;
        this.codes = type;
        encode();
    }

    public AbstractMessage(XdagMessageCodes type, long starttime, long endtime, Bytes32 hash, XdagStats xdagStats) {
        parsed = true;
        this.starttime = starttime;
        this.endtime = endtime;
        this.hash = hash;
        this.xdagStats = xdagStats;
        this.codes = type;
        encode();
    }

    public AbstractMessage(MutableBytes data) {
        super(data);
        parse();
    }

    public XdagStats getXdagStats() {
        return xdagStats;
    }

    public NetDB getNetDB() {
        return netDB;
    }

    public void parse() {
        if (parsed) {
            return;
        }
        starttime = encoded.getLong(16, ByteOrder.LITTLE_ENDIAN);
        endtime = encoded.getLong(24, ByteOrder.LITTLE_ENDIAN);
        random = encoded.getLong(32, ByteOrder.LITTLE_ENDIAN);
        BigInteger maxdifficulty = encoded.slice(80, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
        long totalnblocks = encoded.getLong(104,ByteOrder.LITTLE_ENDIAN);
        long totalnmains = encoded.getLong(120,ByteOrder.LITTLE_ENDIAN);
        int totalnhosts = encoded.getInt(132,ByteOrder.LITTLE_ENDIAN);
        long maintime = encoded.getLong(136,ByteOrder.LITTLE_ENDIAN);
        xdagStats = new XdagStats(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

        // test netdb
        int length = getCommand() == SUMS_REPLY ? 6 : 14;
        // 80 是sizeof(xdag_stats)
        byte[] netdb = new byte[length * 32 - 80];
        System.arraycopy(encoded.toArray(), 144, netdb, 0, length * 32 - 80);
        netDB = new NetDB(netdb);

        parsed = true;
    }

    public void encode() {
        parsed = true;
        encoded = MutableBytes.create(512);
        int ttl = 1;
        long transportheader = (ttl << 8) | DNET_PKT_XDAG | (XDAG_BLOCK_SIZE << 16);
        long type = (codes.asByte() << 4) | XDAG_FIELD_NONCE.asByte();

        BigInteger diff = xdagStats.difficulty;
        BigInteger maxDiff = xdagStats.maxdifficulty;
        long nmain = xdagStats.nmain;
        long totalMainNumber = Math.max(xdagStats.totalnmain,nmain);
        long nblocks = xdagStats.nblocks;
        long totalBlockNumber = xdagStats.totalnblocks;

        // TODO：后续根据ip替换
        String tmp = "04000000040000003ef4780100000000" + "7f000001611e7f000001b8227f0000015f767f000001d49d";
        // net 相关
        byte[] tmpbyte = Hex.decode(tmp);

        // add netdb
        // byte[] iplist = netDB.encode(netDB.getActiveIP());

        // field 0 and field1
        MutableBytes32 first = MutableBytes32.create();
        first.set(0, Bytes.wrap(BytesUtils.longToBytes(transportheader, true)));
        first.set(8, Bytes.wrap(BytesUtils.longToBytes(type, true)));
        first.set(16, Bytes.wrap(BytesUtils.longToBytes(starttime, true)));
        first.set(24, Bytes.wrap(BytesUtils.longToBytes(endtime, true)));

        encoded.set(0, first);
        encoded.set(32, Bytes.wrap(BytesUtils.longToBytes(random, true)));

        // field2 diff and maxdiff
        encoded.set(64, Bytes.wrap(BytesUtils.bigIntegerToBytes(diff, 16, true)));
        encoded.set(80, Bytes.wrap(BytesUtils.bigIntegerToBytes(maxDiff, 16, true)));

        // field3 nblock totalblock main totalmain
        encoded.set(96, Bytes.wrap(BytesUtils.longToBytes(nblocks,  true)));
        encoded.set(104, Bytes.wrap(BytesUtils.longToBytes(totalBlockNumber,  true)));
        encoded.set(112, Bytes.wrap(BytesUtils.longToBytes(nmain,  true)));
        encoded.set(120, Bytes.wrap(BytesUtils.longToBytes(totalMainNumber,  true)));
        encoded.set(128, Bytes.wrap(tmpbyte));
    }

    public void updateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(encoded.toArray(), 0, 512);
        System.arraycopy(BytesUtils.longToBytes(crc32.getValue(), true), 0, encoded, 4, 4);
    }

}
