package io.xdag.discovery.peer;

import com.google.common.net.InetAddresses;
import io.xdag.discovery.Utils.NetworkUtility;
import io.xdag.discovery.Utils.PeerDiscoveryPacketDecodingException;
import io.xdag.discovery.Utils.RLPInput;
import io.xdag.discovery.Utils.bytes.RLPOutput;

import java.net.InetAddress;
import java.util.Objects;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static io.xdag.discovery.Utils.Preconditions.checkGuard;


public class Endpoint {
    private final String host;
    private final int udpPort;
    private final int tcpPort;

    public Endpoint(final String host, final int udpPort, final int tcpPort) {
        this.host = host;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        checkArgument(
                host != null && InetAddresses.isInetAddress(host), "host requires a valid IP address");
        checkArgument(
                NetworkUtility.isValidPort(udpPort), "UDP port requires a value between 1 and 65535");
    }

    public String getHost() {
        return host;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public int getTcpPort() {
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
                && (this.tcpPort==(other.tcpPort));
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
        out.writeUnsignedShort(tcpPort);

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
        int tcpPort = 0;
        if (fieldCount == 3) {
            if (in.nextIsNull()) {
                in.skipNext();
            } else {
                tcpPort = in.readUnsignedShort();
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
