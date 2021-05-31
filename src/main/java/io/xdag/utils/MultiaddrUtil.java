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

import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.net.libp2p.peer.NodeId;

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

    public static Multiaddr addPeerId(final Multiaddr addr, final NodeId nodeId) {
        return new Multiaddr(addr, Multiaddr.fromString("/p2p/" + nodeId.toBase58()));
    }



    private static String protocol(final InetAddress address) {
        return address instanceof Inet6Address ? "ip6" : "ip4";
    }
}
