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
package io.xdag.libp2p.manager;

import io.libp2p.core.*;
import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.libp2p.RPCHandler.RPCHandler;
import io.xdag.libp2p.peer.LibP2PNodeId;
import io.xdag.libp2p.peer.NodeId;
import io.xdag.libp2p.peer.Peer;
import io.xdag.utils.MultiaddrPeerAddress;
import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
public class PeerManager implements ConnectionHandler {


    private final ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NodeId, SafeFuture<Peer>> pendingConnections =
            new ConcurrentHashMap<>();
    RPCHandler rpcHandler;
    Host host;

    public PeerManager() {

    }

    @Override
    public void handleConnection(@NotNull final Connection connection) {
        final PeerId remoteId = connection.secureSession().getRemoteId();
        log.debug( "Got new connection from " + remoteId);
        connection.closeFuture().thenRun(() -> log.debug( "Peer disconnected: " + remoteId));

    }
    public SafeFuture<Peer> connect(final MultiaddrPeerAddress peer, final Network network) {
        return pendingConnections.computeIfAbsent(peer.getId(), __ -> doConnect(peer, network));
    }

    private SafeFuture<Peer> doConnect(final MultiaddrPeerAddress peer, final Network network) {
        log.debug("Connecting to {}", peer);
        log.info("network = "+network.toString());
        log.info("MultiaddrPeerAddress = "+ peer.toString());
        return SafeFuture.of(() -> network.connect(peer.getMultiaddr()))
                .thenApply(
                        connection -> {
                            final LibP2PNodeId nodeId =
                                    new LibP2PNodeId(connection.secureSession().getRemoteId());
                            final Peer connectedPeer = connectedPeerMap.get(nodeId);
                            if (connectedPeer == null) {
                                if (connection.closeFuture().isDone()) {
                                    // Connection has been immediately closed and the peer already removed
                                    // Since the connection is closed anyway, we can create a new peer to wrap it.
                                } else {
                                    // Theoretically this should never happen because removing from the map is done
                                    // by the close future completing, but make a loud noise just in case.
                                    throw new IllegalStateException(
                                            "No peer registered for established connection to " + nodeId);
                                }
                            }
                            return connectedPeer;
                        })
                .whenComplete((result, error) -> pendingConnections.remove(peer.getId()));
    }
    public void connect(String peer) {
        log.debug("Connecting to {}", peer);
        Multiaddr address = Multiaddr.fromString(peer);
        rpcHandler.dial(host,address);
    }
    public Optional<Peer> getPeer(NodeId id) {
        return Optional.ofNullable(connectedPeerMap.get(id));
    }
    public int getPeerCount() {
        return connectedPeerMap.size();
    }
    public Stream<Peer> streamPeers() {
        return connectedPeerMap.values().stream();
    }
}
