package io.xdag.nat;

import io.xdag.utils.SafeFuture;
import io.xdag.utils.Service;
import lombok.extern.slf4j.Slf4j;


import java.util.Optional;

@Slf4j
public class NatService extends Service {


    private final Optional<NatManager> maybeNatManager;
    private final boolean isDiscoveryEnabled;
    private final int p2pPort;

    NatService(
            final int p2pPort,
            final boolean isDiscoveryEnabled,
            final Optional<NatManager> maybeNatManager) {
        this.p2pPort = p2pPort;
        this.isDiscoveryEnabled = isDiscoveryEnabled;
        this.maybeNatManager = maybeNatManager;
    }

    public NatService(
            final NatConfiguration natConfiguration,
            final int p2pPort,
            final boolean isDiscoveryEnabled) {
        this(
                p2pPort,
                isDiscoveryEnabled,
                natConfiguration.getNatMethod().equals(NatMethod.UPNP)
                        ? Optional.of(new NatManager())
                        : Optional.empty());
    }

    @Override
    protected SafeFuture<Void> doStart() {
        if (maybeNatManager.isEmpty()) {
            return SafeFuture.COMPLETE;
        }
        final NatManager natManager = maybeNatManager.get();
        return natManager
                .start()
                .thenRun(
                        () -> {
                            natManager.requestPortForward(p2pPort, NetworkProtocol.TCP, NatServiceType.XDAG_P2P);
                            if (isDiscoveryEnabled) {
                                natManager.requestPortForward(
                                        p2pPort, NetworkProtocol.UDP, NatServiceType.XDAG_DISCOVERY);
                            }
                        });
    }

    @Override
    protected SafeFuture<?> doStop() {
        return maybeNatManager.map(NatManager::stop).orElse(SafeFuture.completedFuture(null));
    }
}

