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

import io.xdag.core.XdagStats;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import lombok.EqualsAndHashCode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteOrder;

import static io.xdag.config.Constants.DNET_PKT_XDAG;
import static io.xdag.core.XdagBlock.XDAG_BLOCK_SIZE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_NONCE;

@EqualsAndHashCode(callSuper = false)
public class BlockRequestMessage extends AbstractMessage {

    public BlockRequestMessage(MutableBytes hash, XdagStats xdagStats) {
        super(XdagMessageCodes.BLOCK_REQUEST, 0, 0, Bytes32.wrap(hash), xdagStats);
    }

    public BlockRequestMessage(MutableBytes hash) {
        super(hash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public Bytes getEncoded() {
        // TODO Auto-generated method stub
        return encoded;
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
                + " hash="
                + hash.toHexString()
                + " netstatus="
                + xdagStats;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.BLOCK_REQUEST;
    }

    @Override
    public void encode() {
        parsed = true;
        encoded = MutableBytes.create(512);
        int ttl = 1;
        long transportheader = (ttl << 8) | DNET_PKT_XDAG | (XDAG_BLOCK_SIZE << 16);
        long type = (codes.asByte() << 4) | XDAG_FIELD_NONCE.asByte();

        BigInteger diff = xdagStats.getDifficulty();
        BigInteger maxDiff = xdagStats.getMaxdifficulty();
        long nmain = xdagStats.getNmain();
        long totalMainNumber = Math.max(xdagStats.getTotalnmain(),nmain);
        long nblocks = xdagStats.getNblocks();
        long totalBlockNumber = xdagStats.getTotalnblocks();

        // TODO：后续根据ip替换
        String tmp = "04000000040000003ef4780100000000" + "7f000001611e7f000001b8227f0000015f767f000001d49d";
        // net 相关
        byte[] tmpbyte = Hex.decode(tmp);

        // field 0 and field1
        MutableBytes32 first = MutableBytes32.create();
//                BytesUtils.merge(
//                BytesUtils.longToBytes(transportheader, true),
//                BytesUtils.longToBytes(type, true),
//                BytesUtils.longToBytes(starttime, true),
//                BytesUtils.longToBytes(endtime, true));
        first.set(0, Bytes.wrap(BytesUtils.longToBytes(transportheader, true)));
        first.set(8, Bytes.wrap(BytesUtils.longToBytes(type, true)));
        first.set(16, Bytes.wrap(BytesUtils.longToBytes(starttime, true)));
        first.set(24, Bytes.wrap(BytesUtils.longToBytes(endtime, true)));

//        System.arraycopy(first, 0, encoded, 0, 32);
        encoded.set(0, first);
//        this.hash = Arrays.reverse(hash);
//        System.arraycopy(hash, 0, encoded, 32, 32);
        encoded.set(32, hash.reverse());

        // field2 diff and maxdiff
//        System.arraycopy(BytesUtils.bigIntegerToBytes(diff, 16, true), 0, encoded, 64, 16);
        encoded.set(64, Bytes.wrap(BytesUtils.bigIntegerToBytes(diff, 16, true)));
//        System.arraycopy(BytesUtils.bigIntegerToBytes(maxDiff, 16, true), 0, encoded, 80, 16);
        encoded.set(80, Bytes.wrap(BytesUtils.bigIntegerToBytes(maxDiff, 16, true)));

        // field3 nblock totalblock main totalmain
//        System.arraycopy(BytesUtils.longToBytes(nblocks, true), 0, encoded, 96, 8);
        encoded.set(96, Bytes.wrap(BytesUtils.longToBytes(nblocks,  true)));
//        System.arraycopy(BytesUtils.longToBytes(totalBlockNumber, true), 0, encoded, 104, 8);
        encoded.set(104, Bytes.wrap(BytesUtils.longToBytes(totalBlockNumber,  true)));
//        System.arraycopy(BytesUtils.longToBytes(nmain, true), 0, encoded, 112, 8);
        encoded.set(112, Bytes.wrap(BytesUtils.longToBytes(nmain,  true)));
//        System.arraycopy(BytesUtils.longToBytes(totalMainNumber, true), 0, encoded, 120, 8);
        encoded.set(120, Bytes.wrap(BytesUtils.longToBytes(totalMainNumber,  true)));
//        System.arraycopy(tmpbyte, 0, encoded, 128, tmpbyte.length);
        encoded.set(128, Bytes.wrap(tmpbyte));
        updateCrc();
    }

    @Override
    public void parse() {
        if (parsed) {
            return;
        }
//        starttime = BytesUtils.bytesToLong(encoded, 16, true);
        this.starttime = encoded.getLong(16, ByteOrder.LITTLE_ENDIAN);
//        endtime = BytesUtils.bytesToLong(encoded, 24, true);
        this.endtime = encoded.getLong(24, ByteOrder.LITTLE_ENDIAN);
//        BigInteger maxdifficulty = BytesUtils.bytesToBigInteger(encoded, 80, true);
        BigInteger maxdifficulty = encoded.slice(80, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
//        long totalnblocks = BytesUtils.bytesToLong(encoded, 104, true);
        long totalnblocks = encoded.getLong(104,ByteOrder.LITTLE_ENDIAN);
//        long totalnmains = BytesUtils.bytesToLong(encoded, 120, true);
        long totalnmains = encoded.getLong(120, ByteOrder.LITTLE_ENDIAN);
//        int totalnhosts = BytesUtils.bytesToInt(encoded, 132, true);
        int totalnhosts = encoded.getInt(132, ByteOrder.LITTLE_ENDIAN);
//        long maintime = BytesUtils.bytesToLong(encoded, 136, true);
        long maintime = encoded.getLong(136, ByteOrder.LITTLE_ENDIAN);
        xdagStats = new XdagStats(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);
//        hash = new byte[32];
//        System.arraycopy(encoded, 32, hash, 0, 24);
        MutableBytes32 hash = MutableBytes32.create();
        hash.set(0, encoded.slice(32, 24));
        this.hash = hash.copy();
        parsed = true;
    }
}
