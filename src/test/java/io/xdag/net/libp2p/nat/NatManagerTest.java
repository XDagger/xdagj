/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.net.libp2p.nat;

import io.xdag.utils.SafeFuture;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.jupnp.model.meta.RemoteService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class NatManagerTest {
    private UpnpClient upnpClient;
    private NatManager natManager;

    @Before
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