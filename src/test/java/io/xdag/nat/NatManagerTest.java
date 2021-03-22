package io.xdag.nat;

import io.xdag.utils.SafeFuture;
import junit.framework.TestCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.model.meta.RemoteService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NatManagerTest extends TestCase {
    private UpnpClient upnpClient;
    private NatManager natManager;

    @BeforeEach
    public void setup() {
        upnpClient = mock(UpnpClient.class);
        natManager = new NatManager(upnpClient);
        when(upnpClient.startup()).thenReturn(SafeFuture.completedFuture(null));
    }

    @Test
    public void startShouldInvokeUPnPClientStartup() {
        Assertions.assertThat(natManager.start()).isCompleted();
        verify(upnpClient).startup();

        verifyNoMoreInteractions(upnpClient);
    }

    @Test
    public void stopShouldInvokeUPnPClientShutdown() {
        final RemoteService remoteService = mock(RemoteService.class);
        Assertions.assertThat(natManager.start()).isCompleted();
        verify(upnpClient).startup();

        when(upnpClient.getWanIpFuture()).thenReturn(SafeFuture.completedFuture(remoteService));
        Assertions.assertThat(natManager.stop()).isCompleted();

        verify(upnpClient).getWanIpFuture();
        verify(upnpClient).shutdown();

        verifyNoMoreInteractions(upnpClient);
    }

    @Test
    public void stopDoesNothingWhenAlreadyStopped() {
        Assertions.assertThat(natManager.stop()).isCompleted();
        verifyNoMoreInteractions(upnpClient);
    }

    @Test
    public void startDoesNothingWhenAlreadyStarted() {
        Assertions.assertThat(natManager.start()).isCompleted();

        verify(upnpClient).startup();

        Assertions.assertThat(natManager.start()).hasFailed();

        verifyNoMoreInteractions(upnpClient);
    }

    @Test
    public void requestPortForwardThrowsWhenCalledBeforeStart() {
        assertThatThrownBy(
                () -> {
                    natManager.requestPortForward(80, NetworkProtocol.TCP, NatServiceType.XDAG_DISCOVERY);
                })
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void requestPortForwardThrowsWhenPortIsZero() {
        Assertions.assertThat(natManager.start()).isCompleted();

        assertThatThrownBy(
                () ->
                        natManager.requestPortForward(
                                0, NetworkProtocol.TCP, NatServiceType.XDAG_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldReleasePortsOnShutdown() {
        final NatPortMapping tcpMapping = mock(NatPortMapping.class);
        final SafeFuture<NatPortMapping> futureTcpMapping = SafeFuture.completedFuture(tcpMapping);

        Assertions.assertThat(natManager.start()).isCompleted();
        verify(upnpClient).startup();

        // after the manager starts, the parent service will map any required ports
        when(upnpClient.requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.XDAG_P2P))
                .thenReturn(futureTcpMapping);
        natManager.requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.XDAG_P2P);
        verify(upnpClient).requestPortForward(1234, NetworkProtocol.TCP, NatServiceType.XDAG_P2P);
        verifyNoMoreInteractions(upnpClient);

        // when stop is called, the port that got mapped needs to be released
        when(upnpClient.getWanIpFuture()).thenReturn(SafeFuture.completedFuture(null));
        when(upnpClient.releasePortForward(tcpMapping)).thenReturn(SafeFuture.COMPLETE);
        Assertions.assertThat(natManager.stop()).isCompleted();
        verify(upnpClient).getWanIpFuture();
        verify(upnpClient).releasePortForward(tcpMapping);
        verify(upnpClient).shutdown();
        verifyNoMoreInteractions(upnpClient);
    }
}