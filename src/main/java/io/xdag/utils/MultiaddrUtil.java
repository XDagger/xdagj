package io.xdag.utils;/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.libp2p.peer.NodeId;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;


public class MultiaddrUtil {


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

    public static Multiaddr fromInetSocketAddress(
            final InetSocketAddress address, final NodeId nodeId) {
        return addPeerId(fromInetSocketAddress(address, "tcp"), nodeId);
    }

    private static Multiaddr addPeerId(final Multiaddr addr, final NodeId nodeId) {
        return new Multiaddr(addr, Multiaddr.fromString("/p2p/" + nodeId.toBase58()));
    }



    private static String protocol(final InetAddress address) {
        return address instanceof Inet6Address ? "ip6" : "ip4";
    }
}
