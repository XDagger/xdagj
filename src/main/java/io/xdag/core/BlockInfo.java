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
import java.util.Arrays;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Block information class that contains block metadata
 */
@Getter
@Setter
public class BlockInfo {

    // Block type
    public long type;
    // Block flags indicating various states
    public int flags;
    // Block height in the chain
    private long height;
    // Block difficulty value
    private BigInteger difficulty;
    // Reference to another block
    private byte[] ref;
    // Link to block with maximum difficulty
    private byte[] maxDiffLink;
    // Transaction fee
    private XAmount fee = XAmount.ZERO;
    // Block remark/memo field
    private byte[] remark;
    // Block full hash
    private byte[] hash;
    // Block truncated hash
    private byte[] hashlow;
    // Block amount/value
    private XAmount amount = XAmount.ZERO;
    // Block timestamp
    private long timestamp;

    // Snapshot related fields
    private boolean isSnapshot = false;
    private SnapshotInfo snapshotInfo = null;

    @Override
    public String toString() {
        return "BlockInfo{" +
                "height=" + height +
                ", hash=" + Arrays.toString(hash) +
                ", hashlow=" + Arrays.toString(hashlow) +
                ", amount=" + amount.toString() +
                ", type=" + type +
                ", difficulty=" + difficulty +
                ", ref=" + Arrays.toString(ref) +
                ", maxDiffLink=" + Arrays.toString(maxDiffLink) +
                ", flags=" + Integer.toHexString(flags) +
                ", fee=" + fee.toString() +
                ", timestamp=" + timestamp +
                ", remark=" + Arrays.toString(remark) +
                ", isSnapshot=" + isSnapshot +
                ", snapshotInfo=" + snapshotInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockInfo blockInfo = (BlockInfo) o;
        return type == blockInfo.type &&
                flags == blockInfo.flags &&
                height == blockInfo.height &&
                timestamp == blockInfo.timestamp &&
                Arrays.equals(hash, blockInfo.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, flags, height, timestamp);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}
