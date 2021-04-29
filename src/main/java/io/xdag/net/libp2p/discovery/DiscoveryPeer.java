package io.xdag.net.libp2p.discovery;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * @author wawa
 */
public record DiscoveryPeer(Bytes publicKey, InetSocketAddress nodeAddress) {

    public Bytes getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DiscoveryPeer that = (DiscoveryPeer) o;
        return Objects.equals(publicKey, that.publicKey)
                && Objects.equals(nodeAddress, that.nodeAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, nodeAddress);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("publicKey", publicKey)
                .add("nodeAddress", nodeAddress)
                .toString();
    }
}
