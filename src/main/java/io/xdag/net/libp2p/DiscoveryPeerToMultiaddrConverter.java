package io.xdag.net.libp2p;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.net.libp2p.peer.LibP2PNodeId;
import io.xdag.net.discovery.DiscoveryPeer;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.libp2p.crypto.keys.Secp256k1Kt.unmarshalSecp256k1PublicKey;

public class DiscoveryPeerToMultiaddrConverter {

    public static Multiaddr convertToMultiAddr(final DiscoveryPeer peer) {
        final InetSocketAddress address = peer.getNodeAddress();
        final LibP2PNodeId nodeId = getNodeId(peer);
        final String addrString =
                String.format(
                        "/%s/%s/tcp/%d/p2p/%s",
                        protocol(address.getAddress()),
                        address.getAddress().getHostAddress(),
                        address.getPort(),
                        nodeId);
        return Multiaddr.fromString(addrString);
    }

    public static LibP2PNodeId getNodeId(final DiscoveryPeer peer) {
        final PubKey pubKey = unmarshalSecp256k1PublicKey(peer.getPublicKey().toArrayUnsafe());
        return new LibP2PNodeId(PeerId.fromPubKey(pubKey));
    }

    private static String protocol(final InetAddress address) {
        return address instanceof Inet6Address ? "ip6" : "ip4";
    }
}
