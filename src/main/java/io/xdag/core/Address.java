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
import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;

public class Address {

    /**
     * 放入字段的数据 正常顺序
     */
    protected MutableBytes32 data;
    /**
     * 输入or输出or不带amount的输出
     */
    @Getter
    @Setter
    protected XdagField.FieldType type;
    /**
     * 转账金额（输入or输出）
     */
    protected UInt64 amount = UInt64.ZERO;
    /**
     * 地址hash低192bit
     */
    protected MutableBytes32 hashLow;

    protected boolean parsed = false;

    public Address(XdagField field) {
        this.type = field.getType();
        this.data = MutableBytes32.wrap(field.getData().reverse().mutableCopy());
        parse();
    }

    /**
     * 只用于ref 跟 maxdifflink
     */
    public Address(Bytes32 hashLow) {
        this.hashLow = hashLow.mutableCopy();
        this.amount = UInt64.ZERO;
        parsed = true;
    }

    /**
     * 只用于ref 跟 maxdifflink
     */
    public Address(Block block) {
        this.hashLow = block.getHashLow().mutableCopy();
        this.amount = UInt64.ZERO;
        parsed = true;
    }

    public Address(Bytes32 blockHashlow, XdagField.FieldType type) {
        this.type = type;
        this.data = blockHashlow.mutableCopy();
        parse();
    }

    public Address(Bytes32 blockHashLow, XdagField.FieldType type, UInt64 amount) {
        this.type = type;
        this.hashLow = blockHashLow.mutableCopy();
        this.amount = amount;
        parsed = true;
    }

    public Bytes getData() {
        if (this.data == null) {
            this.data = MutableBytes32.create();
            this.data.set(8, this.hashLow.slice(8, 24));
            this.data.set(0, amount.toBytes());
        }
        return this.data;
    }

    public void parse() {
        if (!parsed) {
            this.hashLow = MutableBytes32.create();
            this.hashLow.set(8, this.data.slice(8, 24));
            this.amount = UInt64.fromBytes(this.data.slice(0, 8));
            this.parsed = true;
        }
    }

    public UInt64 getAmount() {
        parse();
        return this.amount;
    }

    public MutableBytes32 getHashLow() {
        parse();
        return this.hashLow;
    }

    @Override
    public String toString() {
        return "Block Hash[" + hashLow.toHexString() + "]";
    }
}
