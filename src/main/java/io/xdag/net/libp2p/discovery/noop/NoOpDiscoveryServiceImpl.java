package io.xdag.net.libp2p.discovery.noop;

import io.xdag.net.libp2p.discovery.DiscoveryPeer;
import io.xdag.net.libp2p.discovery.DiscoveryService;
import io.xdag.utils.SafeFuture;
import org.apache.tuweni.bytes.Bytes;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author wawa
 */
public class NoOpDiscoveryServiceImpl implements DiscoveryService {

    @Override
    public SafeFuture<?> start() {
        return SafeFuture.COMPLETE;
    }

    @Override
    public SafeFuture<?> stop() {
        return SafeFuture.COMPLETE;
    }

    @Override
    public Stream<DiscoveryPeer> streamKnownPeers() {
        return Stream.empty();
    }

    @Override
    public SafeFuture<Void> searchForPeers() {
        return SafeFuture.COMPLETE;
    }

    @Override
    public Optional<String> getEnr() {
        return Optional.empty();
    }

}
