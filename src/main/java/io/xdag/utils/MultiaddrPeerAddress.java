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
package io.xdag.utils;


import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.xdag.libp2p.peer.LibP2PNodeId;
import io.xdag.libp2p.peer.NodeId;
import io.xdag.libp2p.peer.PeerAddress;

import java.util.Objects;


public class MultiaddrPeerAddress extends PeerAddress {

    private final Multiaddr multiaddr;

    MultiaddrPeerAddress(final NodeId nodeId, final Multiaddr multiaddr) {
        super(nodeId);
        this.multiaddr = multiaddr;
    }

    @Override
    public String toExternalForm() {
        return multiaddr.toString();
    }

    public static MultiaddrPeerAddress fromAddress(final String address) {
        final Multiaddr multiaddr = Multiaddr.fromString(address);
        return fromMultiaddr(multiaddr);
    }



    private static MultiaddrPeerAddress fromMultiaddr(final Multiaddr multiaddr) {
        final String p2pComponent = multiaddr.getStringComponent(Protocol.P2P);
        if (p2pComponent == null) {
            throw new IllegalArgumentException("No peer ID present in multiaddr: " + multiaddr);
        }
        final LibP2PNodeId nodeId = new LibP2PNodeId(PeerId.fromBase58(p2pComponent));
        return new MultiaddrPeerAddress(nodeId, multiaddr);
    }

    public Multiaddr getMultiaddr() {
        return multiaddr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final MultiaddrPeerAddress that = (MultiaddrPeerAddress) o;
        return Objects.equals(multiaddr, that.multiaddr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), multiaddr);
    }
}
