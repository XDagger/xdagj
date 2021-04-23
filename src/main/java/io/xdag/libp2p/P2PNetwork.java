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
package io.xdag.libp2p;

import io.xdag.libp2p.peer.NodeId;
import io.xdag.libp2p.peer.PeerAddress;
import io.xdag.utils.SafeFuture;
import org.apache.tuweni.bytes.Bytes;

import java.util.Optional;
import java.util.stream.Stream;

public interface P2PNetwork<P> {

    void dail(String peer);

    enum State {
        IDLE,
        RUNNING,
        STOPPED
    }



    PeerAddress createPeerAddress(String peerAddress);

    boolean isConnected(PeerAddress peerAddress);

    Bytes getPrivateKey();

    Optional getPeer(NodeId id);

    Stream streamPeers();

    NodeId parseNodeId(final String nodeId);

    int getPeerCount();

    String getNodeAddress();

    NodeId getNodeId();



    /**
     * Starts the P2P network layer.
     *
     */
    SafeFuture<?> start();

    /** Stops the P2P network layer. */
    SafeFuture<?> stop();
}

