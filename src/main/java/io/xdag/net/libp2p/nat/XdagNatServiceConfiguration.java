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

import lombok.extern.slf4j.Slf4j;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.binding.xml.DeviceDescriptorBinder;
import org.jupnp.binding.xml.ServiceDescriptorBinder;
import org.jupnp.binding.xml.UDA10DeviceDescriptorBinderImpl;
import org.jupnp.binding.xml.UDA10ServiceDescriptorBinderImpl;
import org.jupnp.model.Namespace;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.ServiceType;
import org.jupnp.transport.impl.*;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.*;

import java.util.concurrent.*;

@Slf4j
public class XdagNatServiceConfiguration implements UpnpServiceConfiguration {
    private final ThreadPoolExecutor executorService;
    private final Namespace namespace;

    private final DeviceDescriptorBinder deviceDescriptorBinderUDA10;
    private final ServiceDescriptorBinder serviceDescriptorBinderUDA10;

    private final int streamListenPort;
    private final int multicastResponsePort;

    XdagNatServiceConfiguration() {
        this(
                NetworkAddressFactoryImpl.DEFAULT_TCP_HTTP_LISTEN_PORT,
                NetworkAddressFactoryImpl.DEFAULT_MULTICAST_RESPONSE_LISTEN_PORT);
    }

    public XdagNatServiceConfiguration(final int streamListenPort, final int multicastResponsePort) {
        executorService =
                new ThreadPoolExecutor(
                        16,
                        200,
                        10,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(2000),
                        new DefaultUpnpServiceConfiguration.JUPnPThreadFactory(),
                        new ThreadPoolExecutor.DiscardPolicy() {
                            // The pool is bounded and rejections will happen during shutdown
                            @Override
                            public void rejectedExecution(
                                    final Runnable runnable, final ThreadPoolExecutor threadPoolExecutor) {
                                // Log and discard
                                log.warn("Thread pool rejected execution of " + runnable.getClass());
                                super.rejectedExecution(runnable, threadPoolExecutor);
                            }
                        });
        executorService.allowCoreThreadTimeOut(true);

        deviceDescriptorBinderUDA10 = new UDA10DeviceDescriptorBinderImpl();
        serviceDescriptorBinderUDA10 = new UDA10ServiceDescriptorBinderImpl();
        namespace = new Namespace();

        this.streamListenPort = streamListenPort;
        this.multicastResponsePort = multicastResponsePort;
    }
    /**
     * @return A new instance of the {@link NetworkAddressFactory} interface.
     */

    @Override
    public NetworkAddressFactory createNetworkAddressFactory() {
        return new NetworkAddressFactoryImpl(streamListenPort, multicastResponsePort);
    }
    /**
     * @return The shared implementation of {@link DatagramProcessor}.
     */
    @Override
    public DatagramProcessor getDatagramProcessor() {
        return new DatagramProcessorImpl();
    }

    @Override
    public SOAPActionProcessor getSoapActionProcessor() {
        return new SOAPActionProcessorImpl();
    }

    @Override
    public GENAEventProcessor getGenaEventProcessor() {
        return new GENAEventProcessorImpl();
    }

    @SuppressWarnings("rawtypes") // superclass uses raw types
    @Override
    public StreamClient createStreamClient() {
        return new OkHttpStreamClient(new StreamClientConfigurationImpl(executorService));
    }

    @SuppressWarnings("rawtypes") // superclass uses raw types
    @Override
    public MulticastReceiver createMulticastReceiver(
            final NetworkAddressFactory networkAddressFactory) {
        return new MulticastReceiverImpl(
                new MulticastReceiverConfigurationImpl(
                        networkAddressFactory.getMulticastGroup(), networkAddressFactory.getMulticastPort()));
    }

    @SuppressWarnings("rawtypes") // superclass uses raw types
    @Override
    public DatagramIO createDatagramIO(final NetworkAddressFactory networkAddressFactory) {
        return new DatagramIOImpl(new DatagramIOConfigurationImpl());
    }

    @SuppressWarnings("rawtypes") // superclass uses raw types
    @Override
    public StreamServer createStreamServer(final NetworkAddressFactory networkAddressFactory) {
        return null;
    }

    @Override
    public Executor getMulticastReceiverExecutor() {
        return executorService;
    }

    @Override
    public Executor getDatagramIOExecutor() {
        return executorService;
    }

    @Override
    public ExecutorService getStreamServerExecutorService() {
        return executorService;
    }

    @Override
    public DeviceDescriptorBinder getDeviceDescriptorBinderUDA10() {
        return deviceDescriptorBinderUDA10;
    }

    @Override
    public ServiceDescriptorBinder getServiceDescriptorBinderUDA10() {
        return serviceDescriptorBinderUDA10;
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {
        return new ServiceType[0];
    }

    @Override
    public int getRegistryMaintenanceIntervalMillis() {
        return 1000;
    }

    @Override
    public int getAliveIntervalMillis() {
        return 0;
    }

    @Override
    public boolean isReceivedSubscriptionTimeoutIgnored() {
        return false;
    }

    @Override
    public Integer getRemoteDeviceMaxAgeSeconds() {
        return null;
    }

    @Override
    public UpnpHeaders getDescriptorRetrievalHeaders(final RemoteDeviceIdentity identity) {
        return null;
    }

    @Override
    public UpnpHeaders getEventSubscriptionHeaders(final RemoteService service) {
        return null;
    }

    @Override
    public Executor getAsyncProtocolExecutor() {
        return executorService;
    }

    @Override
    public ExecutorService getSyncProtocolExecutorService() {
        return executorService;
    }

    @Override
    public Namespace getNamespace() {
        return namespace;
    }

    @Override
    public Executor getRegistryMaintainerExecutor() {
        return executorService;
    }

    @Override
    public Executor getRegistryListenerExecutor() {
        return executorService;
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }
}
