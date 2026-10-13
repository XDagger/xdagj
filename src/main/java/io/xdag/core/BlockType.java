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

import lombok.Getter;

/**
 * Enum representing different types of blocks in the XDAG blockchain
 * MAIN_BLOCK - Main chain block that forms the blockchain
 * WALLET - Wallet block that stores account information
 * TRANSACTION - Transaction block that records transfers
 * SNAPSHOT - Snapshot block used for chain state backup
 */
@Getter
public enum BlockType {
    MAIN_BLOCK(0, "Main"),
    WALLET(1, "Wallet"), 
    TRANSACTION(2, "Transaction"),
    SNAPSHOT(3, "Snapshot");

    // Numeric code representing the block type
    private final int code;
    // Human readable description of the block type
    private final String desc;

    /**
     * Constructor for BlockType enum
     * @param code Numeric code for the block type
     * @param desc Description of the block type
     */
    BlockType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
