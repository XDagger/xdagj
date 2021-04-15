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
import io.xdag.discovery.peers.Endpoint;

public class PongPacketData implements PacketData {
    private final Endpoint to;

    /* Hash of the PING packet. */
    private final BytesValue pingHash;

    /* In millis after epoch. */
    private final long expiration;

    private PongPacketData(final Endpoint to, final BytesValue pingHash, final long expiration) {
        this.to = to;
        this.pingHash = pingHash;
        this.expiration = expiration;
    }

    public static PongPacketData create(final Endpoint to, final BytesValue pingHash) {
        return new PongPacketData(
                to, pingHash, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    public static PongPacketData readFrom(final RLPInput in) {
        in.enterList();
        final Endpoint to = Endpoint.decodeStandalone(in);
        final BytesValue hash = in.readBytesValue();
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new PongPacketData(to, hash, expiration);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        to.encodeStandalone(out);
        out.writeBytesValue(pingHash);
        out.writeLongScalar(expiration);
        out.endList();
    }

    @Override
    public String toString() {
        return "PongPacketData{"
                + "to="
                + to
                + ", pingHash="
                + pingHash
                + ", expiration="
                + expiration
                + '}';
    }

    public Endpoint getTo() {
        return to;
    }

    public BytesValue getPingHash() {
        return pingHash;
    }

    public long getExpiration() {
        return expiration;
    }

}
