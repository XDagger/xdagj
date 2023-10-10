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
package io.xdag.net.message.consensus;

import java.util.ArrayList;
import java.util.List;

import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.SimpleEncoder;
import lombok.Getter;

@Getter
public class MainBlockPartsMessage extends Message {

    private final long number;
    private final int parts;
    private final List<byte[]> data;

    public MainBlockPartsMessage(long number, int parts, List<byte[]> data) {
        super(MessageCode.MAIN_BLOCK_PARTS, null);

        this.number = number;
        this.parts = parts;
        this.data = data;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeLong(number);
        enc.writeInt(parts);
        enc.writeInt(data.size());
        for (byte[] b : data) {
            enc.writeBytes(b);
        }
        this.body = enc.toBytes();
    }

    public MainBlockPartsMessage(byte[] body) {
        super(MessageCode.MAIN_BLOCK_PARTS, null);

        SimpleDecoder dec = new SimpleDecoder(body);
        this.number = dec.readLong();
        this.parts = dec.readInt();
        this.data = new ArrayList<>();
        int n = dec.readInt();
        for (int i = 0; i < n; i++) {
            data.add(dec.readBytes());
        }

        this.body = body;
    }

    @Override
    public String toString() {
        return "BlockPartsMessage [number=" + number + ", parts=" + parts + "]";
    }
}
