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

import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import lombok.Getter;

@Getter
public class TransactionResult {

    /**
     * Transaction execution result code.
     */
    public enum Code {

        /**
         * Success. The values have to be 0x01 for compatibility.
         */
        SUCCESS(0x01),

        /**
         * The transaction hash is invalid (should NOT be included on chain).
         */
        INVALID(0x20),

        /**
         * The transaction format is invalid.
         */
        INVALID_FORMAT(0x21),

        /**
         * The transaction timestamp is incorrect.
         */
        INVALID_TIMESTAMP(0x22),

        /**
         * The transaction type is invalid.
         */
        INVALID_TYPE(0x23),

        /**
         * The transaction nonce does not match the account nonce.
         */
        INVALID_NONCE(0x24),

        /**
         * The transaction fee (or gas * gasPrice) doesn't meet the minimum.
         */
        INVALID_FEE(0x25),

        /**
         * The transaction data is invalid, typically too large.
         */
        INVALID_DATA(0x26),

        /**
         * Insufficient available balance.
         */
        INSUFFICIENT_AVAILABLE(0x27);

        private static final Code[] map = new Code[256];

        static {
            for (Code code : Code.values()) {
                map[code.v] = code;
            }
        }

        private final byte v;

        Code(int c) {
            this.v = (byte) c;
        }

        public static Code of(int c) {
            return map[c];
        }

        public byte toByte() {
            return v;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }

        public boolean isRejected() {
            return !isSuccess();
        }

        public boolean isAcceptable() {
            return isSuccess();
        }
    }

    /**
     * Transaction execution result code.
     */
    protected Code code;

    /**
     * Create a transaction result.
     */
    public TransactionResult() {
        this(Code.SUCCESS);
    }

    public TransactionResult(Code code) {
        this.code = code;
    }

    public void setCode(Code code) {
        this.code = code;
    }

    public static TransactionResult fromBytes(byte[] bytes) {
        TransactionResult result = new TransactionResult();

        SimpleDecoder dec = new SimpleDecoder(bytes);
        Code code = Code.of(dec.readByte());
        result.setCode(code);

        return result;
    }

    public byte[] toBytes() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeByte(code.toByte());

        return enc.toBytes();
    }

    public byte[] toBytesForMerkle() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeByte(code.toByte());

        return enc.toBytes();
    }

    @Override
    public String toString() {
        return "TransactionResult{code=" + code + '}';
    }
}
