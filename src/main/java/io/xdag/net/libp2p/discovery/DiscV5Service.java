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

package io.xdag.net.libp2p.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;

import io.xdag.net.libp2p.Libp2pUtils;
import io.xdag.utils.SafeFuture;
import io.xdag.utils.Service;

/**
 * @author wawa
 */
public class DiscV5Service extends Service {

    private final DiscoverySystem discoverySystem;

    public DiscV5Service(final DiscoverySystem discoverySystem) {
        this.discoverySystem = discoverySystem;
    }

    public static DiscV5Service create(
            final Bytes privateKey, final String address, final int port, final List<String> bootnodes) {
        final DiscoverySystem discoveryManager =
                new DiscoverySystemBuilder()
                        .privateKey(privateKey)
                        .bootnodes(bootnodes.toArray(new String[0]))
                        .localNodeRecord(
                                new NodeRecordBuilder().privateKey(privateKey).address(address, port).build())
                        .build();

        return new DiscV5Service(discoveryManager);
    }

    @Override
    protected SafeFuture<?> doStart() {
        return SafeFuture.of(discoverySystem.start());
    }

    @Override
    protected SafeFuture<?> doStop() {
        discoverySystem.stop();
        return SafeFuture.completedFuture(null);
    }

    public Stream<DiscoveryPeer> streamKnownPeers() {
        return activeNodes().map(Libp2pUtils::convertToDiscoveryPeer).flatMap(Optional::stream);
    }

    public SafeFuture<Collection<NodeRecord>> searchForPeers() {
        return SafeFuture.of(discoverySystem.searchForNewPeers());
    }

    public Optional<String> getEnr() {
        return Optional.of(discoverySystem.getLocalNodeRecord().asEnr());
    }

    private Stream<NodeRecord> activeNodes() {
        return discoverySystem.streamLiveNodes();
    }
}
