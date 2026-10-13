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
import lombok.Getter;
import lombok.Setter;

/**
 * Class representing preliminary block information before full block creation
 */
@Getter
@Setter
public class PreBlockInfo {

    // Block type (main/wallet/transaction/snapshot)
    public long type;
    // Block flags for various attributes
    public int flags;
    // Block height in the chain
    private long height;
    // Block mining difficulty
    private BigInteger difficulty;
    // Reference to previous blocks
    private byte[] ref;
    // Link to block with maximum difficulty
    private byte[] maxDiffLink;
    // Transaction fee amount
    private long fee;
    // Block remark/memo field
    private byte[] remark;
    // Block full hash
    private byte[] hash;
    // Block truncated hash
    private byte[] hashlow;
    // Block amount/value
    private XAmount amount;
    // Block timestamp
    private long timestamp;

    // Snapshot related fields
    private boolean isSnapshot = false;
    private SnapshotInfo snapshotInfo = null;

}
