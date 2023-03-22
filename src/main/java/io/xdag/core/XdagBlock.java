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

import static io.xdag.core.XdagField.FieldType.fromByte;

import java.nio.ByteOrder;

import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;

public class XdagBlock {

    public static final int XDAG_BLOCK_FIELDS = 16;
    public static final int XDAG_BLOCK_SIZE = 512;

    /**
     * data 以添加签名
     */
    private MutableBytes data;
    private long sum;
    private XdagField[] fields;

    public XdagBlock() {
        fields = new XdagField[XDAG_BLOCK_FIELDS];
    }

    public XdagBlock(XdagField[] fields) {
        this.fields = fields;
    }

    public XdagBlock(byte[] data) {
        this(MutableBytes.wrap(data));
    }

    public XdagBlock(MutableBytes data) {
        this.data = data;
        if (data != null && data.size() == 512) {
            fields = new XdagField[XDAG_BLOCK_FIELDS];
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                MutableBytes32 fieldBytes = MutableBytes32.create();
                fieldBytes.set(0, data.slice(i * 32, 32));
                fields[i] = new XdagField(fieldBytes);
                fields[i].setType(fromByte(getMsgCode(i)));
            }
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                sum += fields[i].getSum();
                fields[i].setType(fromByte(getMsgCode(i)));
            }
        }
    }

    public byte getMsgCode(int n) {
        long type = this.data.getLong(8, ByteOrder.LITTLE_ENDIAN);
        return (byte) (type >> (n << 2) & 0xf);
    }

    public XdagField[] getFields() {
        if (this.fields == null) {
            throw new Error("no fields");
        } else {
            return this.fields;
        }
    }

    public XdagField getField(int number) {
        XdagField[] fields = getFields();
        return fields[number];
    }

    public MutableBytes getData() {
        if (this.data == null) {
            this.data = MutableBytes.create(512);
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                sum += fields[i].getSum();
                int index = i * 32;
                this.data.set(index, fields[i].getData().reverse());
            }
        }
        return data;
    }

    /**
     * 获取区块sums*
     */
    public long getSum() {
        return sum;
    }
}
