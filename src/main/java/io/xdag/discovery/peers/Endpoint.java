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
package io.xdag.discovery.peers;

import io.xdag.utils.discoveryutils.NetworkUtility;
import io.xdag.utils.discoveryutils.PeerDiscoveryPacketDecodingException;
import io.xdag.utils.discoveryutils.RLPInput;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;
import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.util.Objects;
import java.util.OptionalInt;

import static io.xdag.utils.discoveryutils.Preconditions.checkGuard;
import static com.google.common.base.Preconditions.checkArgument;


public class Endpoint {
    private final String host;
    private final int udpPort;
    private final OptionalInt tcpPort;

    public Endpoint(final String host, final int udpPort, final OptionalInt tcpPort) {
        checkArgument(
                host != null && InetAddresses.isInetAddress(host), "host requires a valid IP address");
        checkArgument(
                NetworkUtility.isValidPort(udpPort), "UDP port requires a value between 1 and 65535");
        tcpPort.ifPresent(
                p ->
                        checkArgument(
                                NetworkUtility.isValidPort(p), "TCP port requires a value between 1 and 65535"));

        this.host = host;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
    }

    public String getHost() {
        return host;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public OptionalInt getTcpPort() {
        return tcpPort;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Endpoint)) {
            return false;
        }
        final Endpoint other = (Endpoint) obj;
        return host.equals(other.host)
                && this.udpPort == other.udpPort
                && (this.tcpPort.equals(other.tcpPort));
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, udpPort, tcpPort);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Endpoint{");
        sb.append("host='").append(host).append('\'');
        sb.append(", udpPort=").append(udpPort);
        tcpPort.ifPresent(p -> sb.append(", getTcpPort=").append(p));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Encodes this endpoint into a standalone object.
     *
     * @param out The RLP output stream.
     */
    public void encodeStandalone(final RLPOutput out) {
        out.startList();
        encodeInline(out);
        out.endList();
    }

    /**
     * Encodes this endpoint to an RLP representation that is inlined into a containing object
     * (generally a Peer).
     *
     * @param out The RLP output stream.
     */
    public void encodeInline(final RLPOutput out) {
        out.writeInetAddress(InetAddresses.forString(host));
        out.writeUnsignedShort(udpPort);
        if (tcpPort.isPresent()) {
            out.writeUnsignedShort(tcpPort.getAsInt());
        } else {
            out.writeNull();
        }
    }

    /**
     * Decodes the input stream as an Endpoint instance appearing inline within another object
     * (generally a Peer).
     *
     * @param fieldCount The number of fields RLP list.
     * @param in The RLP input stream from which to read.
     * @return The decoded endpoint.
     */
    public static Endpoint decodeInline(final RLPInput in, final int fieldCount) {
        checkGuard(
                fieldCount == 2 || fieldCount == 3,
                PeerDiscoveryPacketDecodingException::new,
                "Invalid number of components in RLP representation of an endpoint: expected 2 o 3 elements but got %s",
                fieldCount);

        final InetAddress addr = in.readInetAddress();
        final int udpPort = in.readUnsignedShort();

        // Some mainnet packets have been shown to either not have the TCP port field at all,
        // or to have an RLP NULL value for it.
        OptionalInt tcpPort = OptionalInt.empty();
        if (fieldCount == 3) {
            if (in.nextIsNull()) {
                in.skipNext();
            } else {
                tcpPort = OptionalInt.of(in.readUnsignedShort());
            }
        }
        return new Endpoint(addr.getHostAddress(), udpPort, tcpPort);
    }

    /**
     * Decodes the RLP stream as a standalone Endpoint instance, which is not part of a Peer.
     *
     * @param in The RLP input stream from which to read.
     * @return The decoded endpoint.
     */
    public static Endpoint decodeStandalone(final RLPInput in) {
        final int size = in.enterList();
        final Endpoint endpoint = decodeInline(in, size);
        in.leaveList();
        return endpoint;
    }
}
