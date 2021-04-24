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
import io.xdag.utils.discoveryutils.bytes.RLPOutput;
import lombok.extern.slf4j.Slf4j;
import io.xdag.discovery.peers.DefaultPeer;
import io.xdag.discovery.peers.DiscoveryPeer;
import io.xdag.discovery.peers.Peer;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class NeighborsPacketData implements PacketData {

    private final List<DiscoveryPeer> peers;

    /* In millis after epoch. */
    private final long expiration;

    private NeighborsPacketData(final List<DiscoveryPeer> peers, final long expiration) {
        checkArgument(peers != null, "peer list cannot be null");
        checkArgument(expiration >= 0, "expiration must be positive");

        this.peers = peers;
        this.expiration = expiration;
    }

    public static NeighborsPacketData create(final List<DiscoveryPeer> peers) {
        log.info("create NeighborsPacketData success");
        return new NeighborsPacketData(
                peers, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    public static NeighborsPacketData readFrom(final RLPInput in) {
        in.enterList();
        final List<DiscoveryPeer> peers =
                in.readList(rlp -> new DiscoveryPeer(DefaultPeer.readFrom(rlp)));
        log.info("peers nums = "+peers.size());
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new NeighborsPacketData(peers, expiration);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        out.writeList(peers, Peer::writeTo);
        out.writeLongScalar(expiration);
        out.endList();
    }

    public List<DiscoveryPeer> getNodes() {
        return peers;
    }

    public long getExpiration() {
        return expiration;
    }

    @Override
    public String toString() {
        return String.format("NeighborsPacketData{peers=%s, expiration=%d}", peers, expiration);
    }
}
