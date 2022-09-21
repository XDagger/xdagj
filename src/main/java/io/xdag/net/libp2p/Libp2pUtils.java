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

package io.xdag.net.libp2p;

import static io.libp2p.crypto.keys.Secp256k1Kt.unmarshalSecp256k1PublicKey;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.net.libp2p.discovery.DiscoveryPeer;
import io.xdag.net.libp2p.peer.NodeId;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * @author wawa
 */
public class Libp2pUtils {

    public static Optional<DiscoveryPeer> convertToDiscoveryPeer(final NodeRecord nodeRecord) {
        return nodeRecord
                .getTcpAddress()
                .map(address -> socketAddressToDiscoveryPeer(nodeRecord, address));
    }

    public static DiscoveryPeer socketAddressToDiscoveryPeer(
            final NodeRecord nodeRecord, final InetSocketAddress address) {
        return new DiscoveryPeer(((Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1)), address);
    }

    public static String discoveryPeerToDailId(DiscoveryPeer peer) {
        String dailId = fromInetSocketAddress(peer.getNodeAddress(), getNodeId(peer)).toString();
        return dailId.replaceAll("p2p", "ipfs");
    }

    public static Multiaddr fromInetSocketAddress(
            final InetSocketAddress address, final NodeId nodeId) {
        return addPeerId(fromInetSocketAddress(address, "tcp"), nodeId);
    }

    public static LibP2PNodeId getNodeId(final DiscoveryPeer peer) {
        final PubKey pubKey = unmarshalSecp256k1PublicKey(peer.getPublicKey().toArrayUnsafe());
        return new LibP2PNodeId(PeerId.fromPubKey(pubKey));
    }

    public static Multiaddr fromInetSocketAddress(final InetSocketAddress address, final String protocol) {
        final String addrString =
                String.format(
                        "/%s/%s/%s/%d",
                        protocol(address.getAddress()),
                        address.getAddress().getHostAddress(),
                        protocol,
                        address.getPort());
        return Multiaddr.fromString(addrString);
    }

    public static String protocol(final InetAddress address) {
        return address instanceof Inet6Address ? "ip6" : "ip4";
    }

    public static Multiaddr addPeerId(final Multiaddr addr, final NodeId nodeId) {
//        return new Multiaddr(addr, Multiaddr.fromString("/p2p/" + nodeId.toBase58()));
        return addr.withP2P(PeerId.fromBase58(nodeId.toBase58()));
    }


}
