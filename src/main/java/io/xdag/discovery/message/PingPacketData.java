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
import io.xdag.utils.discoveryutils.PeerDiscoveryPacketDecodingException;
import io.xdag.utils.discoveryutils.RLPInput;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;
import io.xdag.discovery.peers.Endpoint;

import java.math.BigInteger;

import static io.xdag.utils.discoveryutils.Preconditions.checkGuard;
import static com.google.common.base.Preconditions.checkArgument;

public class PingPacketData implements PacketData {

    /* Fixed value that represents we're using v4 of the P2P discovery protocol. */
    private static final int VERSION = 4;

    /* Source. */
    private final Endpoint from;

    /* Destination. */
    private final Endpoint to;

    /* In millis after epoch. */
    private final long expiration;

    private PingPacketData(final Endpoint from, final Endpoint to, final long expiration) {
        checkArgument(from != null, "source endpoint cannot be null");
        checkArgument(to != null, "destination endpoint cannot be null");
        checkArgument(expiration >= 0, "expiration cannot be negative");

        this.from = from;
        this.to = to;
        this.expiration = expiration;
    }

    public static PingPacketData create(final Endpoint from, final Endpoint to) {
        return new PingPacketData(
                from, to, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    public static PingPacketData readFrom(final RLPInput in) {
        in.enterList();
        final BigInteger version = in.readBigIntegerScalar();
        checkGuard(
                version.intValue() == VERSION,
                PeerDiscoveryPacketDecodingException::new,
                "Version mismatch in ping packet. Expected: %s, got: %s.",
                VERSION,
                version);

        final Endpoint from = Endpoint.decodeStandalone(in);
        final Endpoint to = Endpoint.decodeStandalone(in);
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new PingPacketData(from, to, expiration);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        out.writeIntScalar(VERSION);
        from.encodeStandalone(out);
        to.encodeStandalone(out);
        out.writeLongScalar(expiration);
        out.endList();
    }

    public Endpoint getFrom() {
        return from;
    }

    public Endpoint getTo() {
        return to;
    }

    public long getExpiration() {
        return expiration;
    }

    @Override
    public String toString() {
        return "PingPacketData{" + "from=" + from + ", to=" + to + ", expiration=" + expiration + '}';
    }
}
