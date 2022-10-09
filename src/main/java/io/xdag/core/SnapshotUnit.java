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

import static io.xdag.utils.BasicUtils.hash2Address;

import java.math.BigInteger;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

@Data
@Slf4j
public class SnapshotUnit {

    protected byte[] pubkey;
    protected SnapshotBalanceData balanceData;
    // data means block data
    protected byte[] data;
    protected boolean hasPubkey = false;
    protected int type;
    protected int keyIndex = -1;
    protected byte[] hash;

    public SnapshotUnit() {

    }

    public SnapshotUnit(byte[] pubkey, SnapshotBalanceData balanceData, byte[] data, byte[] hash) {
        if (pubkey == null && data != null) {
            this.type = 0; //BI_DATA_BALANCE;
        } else if (pubkey != null && balanceData != null) {
            this.type = 1; //Type.BI_PUBKEY_BALANCE;
        } else {
            this.type = 2; //Type.BI_PUBKEY;
        }
        this.pubkey = pubkey;
        this.balanceData = balanceData;
        this.data = data;
        this.hash = hash;
    }

    public SnapshotUnit(byte[] pubkey, SnapshotBalanceData balanceData, byte[] data, byte[] hash, int keyIndex) {
        if (pubkey == null && data != null) {
            this.type = 0; //BI_DATA_BALANCE;
        } else if (pubkey != null && balanceData != null) {
            this.type = 1; //Type.BI_PUBKEY_BALANCE;
        } else {
            this.type = 2; //Type.BI_PUBKEY;
        }
        this.pubkey = pubkey;
        this.balanceData = balanceData;
        this.data = data;
        this.hash = hash;
        this.keyIndex = keyIndex;
    }

    public static BlockInfo trasferToBlockInfo(SnapshotUnit snapshotUnit) {
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setDifficulty(BigInteger.ZERO);
        blockInfo.setFlags(snapshotUnit.getBalanceData().flags);
        if (snapshotUnit.type == 2) {
            blockInfo.setAmount(UInt64.ZERO);
            blockInfo.setTimestamp(0);
        } else {
            blockInfo.setAmount(UInt64.valueOf(snapshotUnit.getBalanceData().getAmount()));
            blockInfo.setTimestamp(snapshotUnit.getBalanceData().getTime());
        }
        blockInfo.setHash(snapshotUnit.getHash());
        byte[] hashLow = new byte[32];
        System.arraycopy(snapshotUnit.getHash(), 8, hashLow, 8, 24);
        blockInfo.setHashlow(hashLow);
        blockInfo.setSnapshot(true);
        if (snapshotUnit.type == 0) {
            blockInfo.setSnapshotInfo(new SnapshotInfo(false, snapshotUnit.getData()));
        } else {
            blockInfo.setSnapshotInfo(new SnapshotInfo(true, snapshotUnit.getPubkey()));
        }
        return blockInfo;
    }

    public boolean hasPubkey() {
        return hasPubkey;
    }

    public boolean hasData() {
        return data != null;
    }

    @Override
    public String toString() {
        return "SnapshotUnit{" +
                "pubkey=" + (pubkey == null ? null : Bytes.wrap(pubkey)) +
                ", balanceData=" + balanceData +
                ", hash=" + hash2Address(Bytes32.wrap(hash)) +
                '}';
    }
}
