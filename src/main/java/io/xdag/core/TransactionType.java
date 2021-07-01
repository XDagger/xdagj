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

public enum TransactionType {

    /**
     * (0x00) Coinbase transaction
     */
    COINBASE(0x00),

    /**
     * (0x01) Balance transfer.
     */
    TRANSFER(0x01),

    /**
     * (0x05) Create a contract.
     */
    CREATE(0x05),

    /**
     * (0x06) Call a contract.
     */
    CALL(0x06);

    private static final TransactionType[] map = new TransactionType[256];
    static {
        for (TransactionType tt : TransactionType.values()) {
            map[tt.code] = tt;
        }
    }

    public static TransactionType of(byte code) {
        return map[0xff & code];
    }

    private int code;

    TransactionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public byte toByte() {
        return (byte) code;
    }

}
