package io.xdag.net.libp2p.peer;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.net.discovery.DiscoveryPeer;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.libp2p.crypto.keys.Secp256k1Kt.unmarshalSecp256k1PublicKey;
import static io.xdag.utils.MultiaddrUtil.addPeerId;

/**
 * @author wawa
 */
public class DiscoveryPeerConverter {

    public static String discoveryPeerToDailId(DiscoveryPeer peer){
        String dailId =  fromInetSocketAddress(peer.getNodeAddress(), DiscoveryPeerConverter.getNodeId(peer)).toString();
        return dailId.replaceAll("p2p","ipfs");
    }

    public static Multiaddr fromInetSocketAddress(
            final InetSocketAddress address, final NodeId nodeId) {
        return addPeerId(fromInetSocketAddress(address, "tcp"), nodeId);
    }

    public static LibP2PNodeId getNodeId(final DiscoveryPeer peer) {
        final PubKey pubKey = unmarshalSecp256k1PublicKey(peer.getPublicKey().toArrayUnsafe());
        return new LibP2PNodeId(PeerId.fromPubKey(pubKey));
    }
    static Multiaddr fromInetSocketAddress(final InetSocketAddress address, final String protocol) {
        final String addrString =
                String.format(
                        "/%s/%s/%s/%d",
                        protocol(address.getAddress()),
                        address.getAddress().getHostAddress(),
                        protocol,
                        address.getPort());
        return Multiaddr.fromString(addrString);
    }
    private static String protocol(final InetAddress address) {
        return address instanceof Inet6Address ? "ip6" : "ip4";
    }
}
