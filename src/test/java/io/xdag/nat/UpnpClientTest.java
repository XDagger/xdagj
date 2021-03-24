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
package io.xdag.nat;

import io.xdag.utils.SafeFuture;
import org.junit.Before;
import org.junit.Test;
import org.jupnp.UpnpService;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class UpnpClientTest {
    private UpnpService natService = mock(UpnpService.class);
    private Registry registry = mock(Registry.class);
    private UpnpClient upnpClient;

    @Before
    public void setup() {
        when(natService.getRegistry()).thenReturn(registry);
        this.upnpClient = new UpnpClient(natService);
    }
    @Test
    public void registryListenerShouldDetectService() throws Exception {
        final SafeFuture<?> startupFuture = upnpClient.startup();

        ArgumentCaptor<RegistryListener> captor = ArgumentCaptor.forClass(RegistryListener.class);
        verify(registry).addListener(captor.capture());
        RegistryListener listener = captor.getValue();

        assertThat(listener).isNotNull();

        // create a remote device that matches the WANIPConnection service that NatManager
        // is looking for and directly call the registry listener
        RemoteService wanIpConnectionService =
                new RemoteService(
                        new UDAServiceType("WANIPConnection"),
                        new UDAServiceId("WANIPConnectionService"),
                        URI.create("/x_wanipconnection.xml"),
                        URI.create("/control?WANIPConnection"),
                        URI.create("/event?WANIPConnection"),
                        null,
                        null);

        RemoteDevice device =
                new RemoteDevice(
                        new RemoteDeviceIdentity(
                                UDN.valueOf(NatManager.SERVICE_TYPE_WAN_IP_CONNECTION),
                                3600,
                                new URL("http://127.63.31.15/"),
                                null,
                                InetAddress.getByName("127.63.31.15")),
                        new UDADeviceType("WANConnectionDevice"),
                        new DeviceDetails("WAN Connection Device"),
                        wanIpConnectionService);

        listener.remoteDeviceAdded(registry, device);

        assertThat(startupFuture).isCompleted();
        assertThat(upnpClient.getWanIpFuture().join()).isEqualTo(wanIpConnectionService);
    }
    @Test
    public void TestClientnomalRun(){
        NatService natService = new NatService(10000,true, Optional.of(new NatManager()));
        SafeFuture<Void> start = natService.doStart();
        assertThat(start).isCompleted();
    }

}