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

import static io.xdag.utils.BasicUtils.hash2Address;

import java.math.BigInteger;
import java.nio.ByteOrder;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;

@Data
public class StatsBlock {

    protected long height;
    protected long time;
    protected byte[] hash;
    protected BigInteger difficulty;

    public StatsBlock() {

    }

    public StatsBlock(long height, long time, byte[] hash, BigInteger difficulty) {
        this.height = height;
        this.time = time;
        this.hash = Arrays.reverse(hash);
        this.difficulty = difficulty;
    }

    public static StatsBlock parse(Bytes key, Bytes value,int offset) {
        Bytes uncompressed = value;
        long time = uncompressed.getLong(8, ByteOrder.LITTLE_ENDIAN);
        Bytes32 hash = Bytes32.wrap(uncompressed.slice(16, 32));
        BigInteger diff = uncompressed.slice(48, 16).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
        return new StatsBlock(offset, time, hash.toArray(), diff);
    }


    @Override
    public String toString() {
        return "StatsBlock{" +
                "height=" + height +
                ", time=" + time +
                ", hash=" + hash2Address(Bytes32.wrap(hash)) +
                ", difficulty=" + difficulty.toString(16) +
                '}';
    }
}
