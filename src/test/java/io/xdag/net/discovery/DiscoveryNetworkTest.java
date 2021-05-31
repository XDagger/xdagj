package io.xdag.net.discovery;

import io.xdag.net.discovery.discv5.DiscV5ServiceImpl;
import io.xdag.utils.SafeFuture;
import org.junit.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class DiscoveryNetworkTest {
    private final DiscV5ServiceImpl discoveryService = mock(DiscV5ServiceImpl.class);

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
