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

import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;

@Slf4j
public class TxAddress {
    @Getter
    @Setter
    protected UInt64 txNonce;

    protected MutableBytes32 txNonceData;

    protected MutableBytes32 addressNonce;

    protected boolean parsed = false;

    public TxAddress(UInt64 transactionNonce) {
        this.addressNonce = MutableBytes32.create();
        this.txNonceData = MutableBytes32.create();
        this.txNonce = transactionNonce;
        this.addressNonce.set(0, Bytes.wrap(BytesUtils.bigIntegerToBytes(transactionNonce, 8)));
        this.txNonceData.set(0, this.addressNonce.slice(0, 8));
        parsed = true;
    }

    public TxAddress(XdagField field) {
        this.txNonceData = MutableBytes32.wrap(field.getData().reverse().mutableCopy());
        parse();
    }

    public void parse() {
        if (!parsed) {
            this.addressNonce = MutableBytes32.create();
            this.addressNonce.set(0,this.txNonceData.slice(0, 8));
            this.txNonce = UInt64.fromBytes(this.addressNonce.slice(0, 8));
            this.parsed = true;
        }
    }

    public Bytes getData() {
        if (this.txNonceData == null) {
            this.txNonceData = MutableBytes32.create();
            this.txNonceData.set(0,this.addressNonce.slice(0, 8));
        }
        return this.txNonceData;
    }

    public UInt64 getTransactionNonce() {
        parse();
        return this.txNonce;
    }

    @Override
    public String toString() {
        return "txNonce [" + addressNonce.toString() + "]";
    }
}
