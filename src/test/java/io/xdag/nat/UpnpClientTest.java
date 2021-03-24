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