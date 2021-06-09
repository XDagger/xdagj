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
package io.xdag.net.nat;

import com.google.common.annotations.VisibleForTesting;
import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.jupnp.support.model.PortMapping;

import java.util.Optional;

@Slf4j
public class UpnpClient {

  static final String SERVICE_TYPE_WAN_IP_CONNECTION = "WANIPConnection";
  private final SafeFuture<String> externalIpQueryFuture = new SafeFuture<>();
  private final SafeFuture<RemoteService> wanIpFuture = new SafeFuture<>();
  private Optional<String> localIpAddress = Optional.empty();
  private final UpnpService upnpService;
  private final RegistryListener registryListener;

  public UpnpClient() {
    // Workaround for an issue in the jupnp library: the ExecutorService used misconfigures
    // its ThreadPoolExecutor, causing it to only launch a single thread. This prevents any work
    // from getting done (effectively a deadlock). The issue is fixed here:
    //   https://github.com/jupnp/jupnp/pull/117
    // However, this fix has not made it into any releases yet.
    // this(new UpnpServiceImpl(new DefaultUpnpServiceConfiguration()));
    // TODO: once a new release is available, remove this @Override
    this(new UpnpServiceImpl(new XdagNatServiceConfiguration()));
  }

  public UpnpClient(final UpnpService upnpService) {
    this.upnpService = upnpService;
    // registry listener to observe new devices and look for specific services
    registryListener =
        new XdagRegistryListener(){
          @Override
          public void remoteDeviceAdded(final Registry registry, final RemoteDevice device) {
            log.debug("UPnP Device discovered: " + device.getDetails().getFriendlyName());
            inspectDeviceRecursive(device);
          }
        };
  }

  public SafeFuture<?> startup() {
    upnpService.startup();
    upnpService.getRegistry().addListener(registryListener);
    initiateExternalIpQuery();
    return wanIpFuture;
  }

  public void shutdown() {
    wanIpFuture.cancel(true);
    upnpService.getRegistry().removeListener(registryListener);
    upnpService.shutdown();
  }

  @SuppressWarnings("unchecked")
  public SafeFuture<Void> releasePortForward(final NatPortMapping portMapping) {
    log.debug(
        "Releasing port forward for {} {} -> {}",
        portMapping.getProtocol(),
        portMapping.getInternalPort(),
        portMapping.getExternalPort());

    RemoteService service = getWanIpFuture().join();
    XdagPortMappingDelete callback =
        new XdagPortMappingDelete(service, toJupnpPortMapping(portMapping));

//    ignoreFuture(upnpService.getControlPoint().execute(callback));

    return callback.getFuture();
  }

  public SafeFuture<NatPortMapping> requestPortForward(
      final int port, NetworkProtocol protocol, NatServiceType serviceType) {
    return requestPortForward(
        new PortMapping(
            true,
            new UnsignedIntegerFourBytes(0),
            null,
            new UnsignedIntegerTwoBytes(port),
            new UnsignedIntegerTwoBytes(port),
            null,
            toJupnpProtocol(protocol),
            serviceType.getValue()));
  }

  @VisibleForTesting
  public SafeFuture<RemoteService> getWanIpFuture() {
    return wanIpFuture;
  }

  private SafeFuture<String> getExternalIpFuture() {
    return externalIpQueryFuture;
  }

  private Optional<String> getLocalIpAddress() {
    return localIpAddress;
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<NatPortMapping> requestPortForward(final PortMapping portMapping) {
    return getExternalIpFuture()
        .thenCompose(
            address -> {
              // note that this future is a dependency of externalIpQueryFuture, so it must be
              // completed by now
              RemoteService service = getWanIpFuture().join();

              // at this point, we should have the local address we discovered the IGD on,
              // so we can prime the NewInternalClient field if it was omitted
              if (null == portMapping.getInternalClient()) {
                portMapping.setInternalClient(getLocalIpAddress().orElse(""));
              }

              // our query, which will be handled asynchronously by the jupnp library
              XdagPortMappingAdd callback = new XdagPortMappingAdd(service, portMapping);

              log.debug(
                  "Requesting port forward for {} {} -> {}",
                  portMapping.getProtocol(),
                  portMapping.getInternalPort(),
                  portMapping.getExternalPort());

//              ignoreFuture(upnpService.getControlPoint().execute(callback));
              return callback.getFuture();
            });
  }

  @SuppressWarnings("unchecked")
  private void initiateExternalIpQuery() {
    wanIpFuture
        .thenAccept(
            service -> {
              XdagGetExternalIP callback = new XdagGetExternalIP(service);
//              ignoreFuture(upnpService.getControlPoint().execute(callback));
              callback
                  .getFuture()
                  .thenAccept(
                      externalIpAddress -> {
                        log.debug("Finished getting IP Address");
                        localIpAddress = callback.getDiscoveredOnLocalAddress();
                        externalIpQueryFuture.complete(externalIpAddress);
                      })
                  .finish(
                      error -> {
                        log.debug("Failed to get external ip address", error);
                        externalIpQueryFuture.completeExceptionally(error);
                      });
            })
        .finish(error -> log.debug("Failed to retrieve external ip address", error));
  }

  private void inspectDeviceRecursive(final RemoteDevice device) {
    for (RemoteService service : device.getServices()) {
      String serviceType = service.getServiceType().getType();
      if (serviceType.equalsIgnoreCase(SERVICE_TYPE_WAN_IP_CONNECTION)) {
        wanIpFuture.complete(service);
      }
    }
    for (RemoteDevice subDevice : device.getEmbeddedDevices()) {
      inspectDeviceRecursive(subDevice);
    }
  }

  private PortMapping.Protocol toJupnpProtocol(final NetworkProtocol protocol) {
      return switch (protocol) {
          case UDP -> PortMapping.Protocol.UDP;
          case TCP -> PortMapping.Protocol.TCP;
      };
  }

  private PortMapping toJupnpPortMapping(final NatPortMapping natPortMapping) {
    return new PortMapping(
        true,
        new UnsignedIntegerFourBytes(0),
        null,
        new UnsignedIntegerTwoBytes(natPortMapping.getExternalPort()),
        new UnsignedIntegerTwoBytes(natPortMapping.getInternalPort()),
        null,
        toJupnpProtocol(natPortMapping.getProtocol()),
        natPortMapping.getNatServiceType().getValue());
  }
}
