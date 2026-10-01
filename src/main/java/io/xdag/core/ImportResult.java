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

import org.apache.tuweni.bytes.MutableBytes32;

/**
 * Enum representing different results of block import operations
 * ERROR - Import failed with error
 * EXIST - Block already exists
 * NO_PARENT - Parent block not found
 * INVALID_BLOCK - Block validation failed
 * IN_MEM - Block is already in memory
 * IMPORTED_EXTRA - Block imported as extra
 * IMPORTED_NOT_BEST - Block imported but not in main chain
 * IMPORTED_BEST - Block imported into main chain
 * <p>
 * The details of an import (missing parent, error text, whether the sender misbehaved) belong to the import that
 * was just performed <em>by the calling thread</em>. They used to be plain fields of the shared enum constants,
 * so concurrent imports (p2p, rpc, pow) overwrote each other's details; they are thread-confined now.
 */
public enum ImportResult {
    ERROR,
    EXIST, 
    NO_PARENT,
    INVALID_BLOCK,
    IN_MEM,

    IMPORTED_EXTRA,
    IMPORTED_NOT_BEST,
    IMPORTED_BEST;

    // Truncated hash of the block (NO_PARENT: the missing parent)
    private final ThreadLocal<MutableBytes32> hashLow = new ThreadLocal<>();

    // Error message if import failed
    private final ThreadLocal<String> errorInfo = new ThreadLocal<>();

    // Whether the block can never be valid, whatever the local state and clock are
    private final ThreadLocal<Boolean> misbehavior = new ThreadLocal<>();

    /**
     * Get the truncated hash of the block
     * @return The truncated hash as MutableBytes32
     */
    public MutableBytes32 getHashlow() {
        return hashLow.get();
    }

    /**
     * Set the truncated hash of the block
     * @param hashLow The truncated hash to set
     */
    public void setHashlow(MutableBytes32 hashLow) {
        this.hashLow.set(hashLow);
    }

    public String getErrorInfo() {
        return errorInfo.get();
    }

    public void setErrorInfo(String errorInfo) {
        this.errorInfo.set(errorInfo);
    }

    /**
     * True if the block that was just imported by this thread is invalid for every node at any time (malformed,
     * bad signature, inconsistent amounts, ...), so whoever relayed it did not validate it. False for rejections
     * that depend on the local state or clock (unknown address under the legacy rules, timestamp in the future,
     * full transaction pool), which an honest peer can run into.
     */
    public boolean isMisbehavior() {
        return Boolean.TRUE.equals(misbehavior.get());
    }

    public void setMisbehavior(boolean misbehavior) {
        this.misbehavior.set(misbehavior);
    }

}
