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

import lombok.Getter;
import lombok.Setter;

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

    // Truncated hash of the block
    private MutableBytes32 hashLow;

    // Error message if import failed
    @Setter
    @Getter
    private String errorInfo;

    /**
     * Get the truncated hash of the block
     * @return The truncated hash as MutableBytes32
     */
    public MutableBytes32 getHashlow() {
        return hashLow;
    }

    /**
     * Set the truncated hash of the block
     * @param hashLow The truncated hash to set
     */
    public void setHashlow(MutableBytes32 hashLow) {
        this.hashLow = hashLow;
    }

}
