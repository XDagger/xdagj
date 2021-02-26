package io.xdag.new_libp2p.utils;


import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.xdag.new_libp2p.peer.LibP2PNodeId;
import io.xdag.new_libp2p.peer.NodeId;
import io.xdag.new_libp2p.peer.PeerAddress;

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
