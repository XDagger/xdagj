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

import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;

public class XdagRegistryListener implements RegistryListener {
    /**
     * Called as soon as possible after a device has been discovered.
     * <p>
     * This method will be called after SSDP notification datagrams of a new alive
     * UPnP device have been received and processed. The announced device XML descriptor
     * will be retrieved and parsed. The given {@link RemoteDevice} metadata
     * is validated and partial {@link Service} metadata is available. The
     * services are unhydrated, they have no actions or state variable metadata because the
     * service descriptors of the device model have not been retrieved at this point.
     * </p>
     * <p>
     * You typically do not use this method on a regular machine, this is an optimization
     * for slower UPnP hosts (such as Android handsets).
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   A validated and hydrated device metadata graph, with anemic service metadata.
     */
    @Override
    public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {

    }

    /**
     * Called when service metadata couldn't be initialized.
     * <p>
     * If you override the {@link #remoteDeviceDiscoveryStarted(Registry, RemoteDevice)}
     * method, you might want to override this method as well.
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   A validated and hydrated device metadata graph, with anemic service metadata.
     * @param ex       The reason why service metadata could not be initialized, or <code>null</code> if service
     */
    @Override
    public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {

    }

    /**
     * Called when complete metadata of a newly discovered device is available.
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   A validated and hydrated device metadata graph, with complete service metadata.
     */
    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {

    }

    /**
     * Called when a discovered device's expiration timestamp is updated.
     * <p>
     * This is a signal that a device is still alive and you typically don't have to react to this
     * event. You will be notified when a device disappears through timeout.
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   A validated and hydrated device metadata graph, with complete service metadata.
     */
    @Override
    public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {

    }

    /**
     * Called when a previously discovered device disappears.
     * <p>
     * This method will also be called when a discovered device did not update its expiration timeout
     * and has been been removed automatically by the local registry. This method will not be called
     * when the UPnP stack is shutting down.
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   A validated and hydrated device metadata graph, with complete service metadata.
     */
    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {

    }

    /**
     * Called after you add your own device to the {@link Registry}.
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   The local device added to the {@link Registry}.
     */
    @Override
    public void localDeviceAdded(Registry registry, LocalDevice device) {

    }

    /**
     * Called after you remove your own device from the {@link Registry}.
     * <p>
     * This method will not be called when the UPnP stack is shutting down.
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     * @param device   The local device removed from the {@link Registry}.
     */
    @Override
    public void localDeviceRemoved(Registry registry, LocalDevice device) {

    }

    /**
     * Called after registry maintenance stops but before the registry is cleared.
     * <p>
     * This method should typically not block, it executes in the thread that shuts down the UPnP stack.
     * </p>
     *
     * @param registry The jUPnP registry of all devices and services know to the local UPnP stack.
     */
    @Override
    public void beforeShutdown(Registry registry) {

    }

    /**
     * Called after the registry has been cleared on shutdown.
     * <p>
     * This method should typically not block, it executes in the thread that shuts down the UPnP stack.
     * </p>
     */
    @Override
    public void afterShutdown() {

    }
}
