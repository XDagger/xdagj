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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;

public class XdagField {
    @Getter
    @Setter
    private FieldType type;
    
    @Getter
    @Setter
    private byte[] data;

    @Setter
    private long sum;

    public XdagField(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.position(32 - data.length);
        buffer.put(data);
        this.data = buffer.array();
    }

    public XdagField() {
        this.data = new byte[32];
    }

    public long getSum() {
        if (sum == 0) {
            for (int i = 0; i < 4; i++) {
                sum += BytesUtils.bytesToLong(getData(), i * 8, true);
            }
            return sum;
        } else {
            return sum;
        }
    }

    public enum FieldType {
        // nonce字段
        XDAG_FIELD_NONCE(0x00),
        // 头部字段
        XDAG_FIELD_HEAD(0x01),
        // 输入
        XDAG_FIELD_IN(0x02),
        // 输入
        XDAG_FIELD_OUT(0x03),
        // 输入签名
        XDAG_FIELD_SIGN_IN(0x04),
        // 输出签名
        XDAG_FIELD_SIGN_OUT(0x05),
        XDAG_FIELD_PUBLIC_KEY_0(0x06),
        XDAG_FIELD_PUBLIC_KEY_1(0x07),
        XDAG_FIELD_HEAD_TEST(0x08),
        XDAG_FIELD_REMARK(0x09),
        XDAG_FIELD_RESERVE1(0x0A),
        XDAG_FIELD_RESERVE2(0x0B),
        XDAG_FIELD_RESERVE3(0x0C),
        XDAG_FIELD_RESERVE4(0x0D),
        XDAG_FIELD_RESERVE5(0x0E),
        XDAG_FIELD_RESERVE6(0x0F);

        private static final Map<Integer, FieldType> intToTypeMap = new HashMap<>();

        static {
            for (FieldType type : FieldType.values()) {
                intToTypeMap.put(type.cmd, type);
            }
        }

        private final int cmd;

        private FieldType(int cmd) {
            this.cmd = cmd;
        }

        public static FieldType fromByte(byte i) {
            return intToTypeMap.get((int) i);
        }

        public static boolean inRange(byte code) {
            return code >= XDAG_FIELD_NONCE.asByte() && code <= XDAG_FIELD_RESERVE6.asByte();
        }

        public byte asByte() {
            return (byte) (cmd);
        }
    }
}
