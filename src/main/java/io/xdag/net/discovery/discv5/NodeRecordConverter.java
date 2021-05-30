package io.xdag.net.discovery.discv5;

import io.xdag.net.discovery.DiscoveryPeer;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * @author wawa
 */
public class NodeRecordConverter {
    static Optional<DiscoveryPeer> convertToDiscoveryPeer(final NodeRecord nodeRecord) {
        return nodeRecord
                .getTcpAddress()
                .map(address -> socketAddressToDiscoveryPeer(nodeRecord, address));
    }

    private static DiscoveryPeer socketAddressToDiscoveryPeer(
            final NodeRecord nodeRecord, final InetSocketAddress address) {
        return new DiscoveryPeer(((Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1)), address);
    }
}
