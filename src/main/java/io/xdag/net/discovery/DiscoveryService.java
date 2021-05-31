package io.xdag.net.discovery;

import io.xdag.utils.SafeFuture;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author wawa
 */
public interface DiscoveryService {

    SafeFuture<?> start();

    SafeFuture<?> stop();

    Stream<DiscoveryPeer> streamKnownPeers();

    SafeFuture<Void> searchForPeers();

    Optional<String> getEnr();

}

