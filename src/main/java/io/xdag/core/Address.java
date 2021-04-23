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
import io.xdag.utils.Numeric;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

public class Address {
    /** 放入字段的数据 正常顺序 */
    protected byte[] data;
    /** 输入or输出or不带amount的输出 */
    @Getter
    @Setter
    protected XdagField.FieldType type;
    /** 转账金额（输入or输出） */
    protected BigInteger amount;
    /** 地址hash低192bit */
    protected byte[] hashLow = new byte[32];

    protected boolean parsed = false;

    public Address(XdagField field) {
        this.type = field.getType();
        this.data = Arrays.reverse(field.getData());
        parse();
    }

    /** 只用于ref 跟 maxdifflink */
    public Address(byte[] hashLow) {
        this.hashLow = hashLow;
        this.amount = BigInteger.valueOf(0);
        parsed = true;
    }

    /** 只用于ref 跟 maxdifflink */
    public Address(Block block) {
        this.hashLow = block.getHashLow();
        this.amount = BigInteger.valueOf(0);
        parsed = true;
    }

    public Address(byte[] blockHashlow, XdagField.FieldType type) {
        this.type = type;
        this.data = blockHashlow;
        parse();
    }

    public Address(byte[] blockHashLow, XdagField.FieldType type, long amount) {
        this.type = type;
        this.hashLow = blockHashLow;
        this.amount = BigInteger.valueOf(amount);
        parsed = true;
    }

    public byte[] getData() {
        if (data == null) {
            data = new byte[32];
            System.arraycopy(hashLow, 8, data, 8, 24);
            System.arraycopy(BytesUtils.bigIntegerToBytes(amount, 8), 0, data, 0, 8);
        }
        return data;
    }

    public void parse() {
        if (!parsed) {
            System.arraycopy(data, 8, hashLow, 8, 24);
            byte[] amountbyte = new byte[8];
            System.arraycopy(data, 0, amountbyte, 0, 8);
            amount = Numeric.toBigInt(amountbyte);
            parsed = true;
        }
    }

    public BigInteger getAmount() {
        parse();
        return this.amount;
    }

    public byte[] getHashLow() {
        parse();
        return this.hashLow;
    }

    @Override
    public String toString() {
        return "Block Hash[" + Hex.toHexString(hashLow) + "]";
    }
}
