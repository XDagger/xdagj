package io.xdag.net.libp2p.discovery;

import io.xdag.net.libp2p.Libp2pUtils;
import io.xdag.utils.SafeFuture;
import io.xdag.utils.Service;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author wawa
 */
public class DiscV5Service extends Service {

    private final DiscoverySystem discoverySystem;

    public DiscV5Service(final DiscoverySystem discoverySystem) {
        this.discoverySystem = discoverySystem;
    }

    public static DiscV5Service create(
            final Bytes privateKey, final String address, final int port, final List<String> bootnodes) {
        final DiscoverySystem discoveryManager =
                new DiscoverySystemBuilder()
                        .privateKey(privateKey)
                        .bootnodes(bootnodes.toArray(new String[0]))
                        .localNodeRecord(
                                new NodeRecordBuilder().privateKey(privateKey).address(address, port).build())
                        .build();

        return new DiscV5Service(discoveryManager);
    }

    @Override
    protected SafeFuture<?> doStart() {
        return SafeFuture.of(discoverySystem.start());
    }

    @Override
    protected SafeFuture<?> doStop() {
        discoverySystem.stop();
        return SafeFuture.completedFuture(null);
    }

    public Stream<DiscoveryPeer> streamKnownPeers() {
        return activeNodes().map(Libp2pUtils::convertToDiscoveryPeer).flatMap(Optional::stream);
    }

    public SafeFuture<Void> searchForPeers() {
        return SafeFuture.of(discoverySystem.searchForNewPeers());
    }

    public Optional<String> getEnr() {
        return Optional.of(discoverySystem.getLocalNodeRecord().asEnr());
    }

    private Stream<NodeRecord> activeNodes() {
        return discoverySystem
                .streamKnownNodes()
                .filter(record -> record.getStatus() == NodeStatus.ACTIVE)
                .map(NodeRecordInfo::getNode);
    }
}
