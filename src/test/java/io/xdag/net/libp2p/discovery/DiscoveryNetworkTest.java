package io.xdag.net.libp2p.discovery;

import io.xdag.net.libp2p.discovery.DiscV5Service;
import io.xdag.utils.SafeFuture;
import org.junit.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class DiscoveryNetworkTest {
    private final DiscV5Service discoveryService = mock(DiscV5Service.class);

    @Test
    public void DiscoveryStart() {
        final SafeFuture<Void> discoveryStart = new SafeFuture<>();
        doReturn(discoveryStart).when(discoveryService).start();
    }
    @Test
    @SuppressWarnings({"FutureReturnValueIgnored"})
    public void shouldStopNetworkAndDiscoveryWhenConnectionManagerStopFails() {
        doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    }

}
