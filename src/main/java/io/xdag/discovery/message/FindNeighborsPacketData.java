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
package io.xdag.discovery.message;


import io.xdag.discovery.data.PacketData;
import io.xdag.utils.discoveryutils.RLPInput;
import io.xdag.utils.discoveryutils.bytes.BytesValue;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;

import static com.google.common.base.Preconditions.checkArgument;

public class FindNeighborsPacketData implements PacketData {
    private static final int TARGET_SIZE = 37;

    /* Node ID. */
    private final BytesValue target;

    /* In millis after epoch. */
    private final long expiration;

    private FindNeighborsPacketData(final BytesValue target, final long expiration) {
        checkArgument(target != null && target.size() == TARGET_SIZE, "target must be a valid node id");
        checkArgument(expiration >= 0, "expiration must be positive");

        this.target = target;
        this.expiration = expiration;
    }

    public static FindNeighborsPacketData create(final BytesValue target) {
        return new FindNeighborsPacketData(
                target, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        out.writeBytesValue(target);
        out.writeLongScalar(expiration);
        out.endList();
    }

    public static FindNeighborsPacketData readFrom(final RLPInput in) {
        in.enterList();
        final BytesValue target = in.readBytesValue();
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new FindNeighborsPacketData(target, expiration);
    }

    public long getExpiration() {
        return expiration;
    }

    public BytesValue getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "FindNeighborsPacketData{" + "expiration=" + expiration + ", target=" + target + '}';
    }
}
