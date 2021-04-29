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
import java.util.zip.CRC32;

import io.xdag.core.XdagStats;

import io.xdag.utils.BytesUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
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
    protected byte[] hash;

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

    public AbstractMessage(XdagMessageCodes type, long starttime, long endtime, byte[] hash, XdagStats xdagStats) {
        parsed = true;
        this.starttime = starttime;
        this.endtime = endtime;
        this.hash = hash;
        this.xdagStats = xdagStats;
        this.codes = type;
        encode();
    }

    public AbstractMessage(byte[] data) {
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
        int length = getCommand() == SUMS_REPLY ? 6 : 14;
        // 80 是sizeof(xdag_stats)
        byte[] netdb = new byte[length * 32 - 80];
        System.arraycopy(encoded, 144, netdb, 0, length * 32 - 80);
        netDB = new NetDB(netdb);

        parsed = true;
    }

    public void encode() {
        parsed = true;
        encoded = new byte[512];
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
        byte[] first = BytesUtils.merge(
                BytesUtils.longToBytes(transportheader, true),
                BytesUtils.longToBytes(type, true),
                BytesUtils.longToBytes(starttime, true),
                BytesUtils.longToBytes(endtime, true));
        System.arraycopy(first, 0, encoded, 0, 32);
        System.arraycopy(BytesUtils.longToBytes(random, true), 0, encoded, 32, 8);

        // field2 diff and maxdiff
        System.arraycopy(BytesUtils.bigIntegerToBytes(diff, 16, true), 0, encoded, 64, 16);
        System.arraycopy(BytesUtils.bigIntegerToBytes(maxDiff, 16, true), 0, encoded, 80, 16);

        // field3 nblock totalblock main totalmain
        System.arraycopy(BytesUtils.longToBytes(nblocks, true), 0, encoded, 96, 8);
        System.arraycopy(BytesUtils.longToBytes(totalBlockNumber, true), 0, encoded, 104, 8);
        System.arraycopy(BytesUtils.longToBytes(nmain, true), 0, encoded, 112, 8);
        System.arraycopy(BytesUtils.longToBytes(totalMainNumber, true), 0, encoded, 120, 8);

        System.arraycopy(tmpbyte, 0, encoded, 128, tmpbyte.length);
    }

    public void updateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(encoded, 0, 512);
        System.arraycopy(BytesUtils.longToBytes(crc32.getValue(), true), 0, encoded, 4, 4);
    }

}
