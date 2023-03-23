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

import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;

import io.xdag.utils.XdagTime;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Objects;
import lombok.Data;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.xerial.snappy.Snappy;

@Data
public class SnapshotBalanceData {

    protected long amount;
    protected long time;
    // we dont need storage_pos
    protected long storage_pos;
    protected byte[] hash;

    protected int flags;

    public SnapshotBalanceData() {

    }

    public SnapshotBalanceData(long amount, long time, byte[] hash, int flags) {
        this.amount = amount;
        this.time = time;
        this.hash = Arrays.reverse(hash);
        this.flags = flags;
    }

    public static SnapshotBalanceData parse(Bytes key, Bytes value) {
        // 未压缩
        if (key.size() == 32) {
            long flags = value.getLong(0, ByteOrder.LITTLE_ENDIAN);
            long amount = value.getLong(8, ByteOrder.LITTLE_ENDIAN);
            long time = value.getLong(16, ByteOrder.LITTLE_ENDIAN);
            Bytes32 hash = Bytes32.wrap(key.reverse());
            return new SnapshotBalanceData(amount, time, hash.toArray(), (int) flags);
        } else if (key.size() == 4) {
            // 1. 解压缩
            try {
                Bytes uncompressed = Bytes.wrap(Snappy.uncompress(value.toArray()));
                int flags = uncompressed.getInt(0, ByteOrder.LITTLE_ENDIAN);
                long amount = uncompressed.getLong(8, ByteOrder.LITTLE_ENDIAN);
                long time = uncompressed.getLong(16, ByteOrder.LITTLE_ENDIAN);
                Bytes32 hash = Bytes32.wrap(uncompressed.slice(24));
                return new SnapshotBalanceData(amount, time, hash.toArray(), flags);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "BalanceData{" +
                "amount=" + amount2xdag(amount) +
                ", time=" + FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                .format(XdagTime.xdagTimestampToMs(time)) +
                ", storage_pos=" + storage_pos +
                ", hash=" + (hash != null ? hash2Address(Bytes32.wrap(hash)) : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SnapshotBalanceData that = (SnapshotBalanceData) o;
        return amount == that.amount &&
                time == that.time &&
                storage_pos == that.storage_pos &&
                flags == that.flags &&
                java.util.Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, time, storage_pos, flags);
    }
}
