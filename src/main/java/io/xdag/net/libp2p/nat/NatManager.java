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

import com.google.common.annotations.VisibleForTesting;
import io.xdag.utils.SafeFuture;
import io.xdag.utils.Service;
import lombok.extern.slf4j.Slf4j;
import org.jupnp.model.meta.RemoteService;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Slf4j
public class NatManager extends Service {

    static final String SERVICE_TYPE_WAN_IP_CONNECTION = "WANIPConnection";

    private final UpnpClient upnpClient;
    private final Queue<NatPortMapping> forwardedPorts = new ConcurrentLinkedQueue<>();

    public NatManager() {
        this(new UpnpClient());
    }

    @VisibleForTesting
    NatManager(final UpnpClient upnpClient) {
        this.upnpClient = upnpClient;
    }

    @Override
    public SafeFuture<Void> doStart() {
        log.info("Starting UPnP Service");

        upnpClient.startup().finish(error -> log.warn("Failed to startup UPnP service", error));
        return SafeFuture.COMPLETE;
    }

    @Override
    protected SafeFuture<Void> doStop() {
        return releaseAllPortForwards()
                .orTimeout(3, TimeUnit.SECONDS)
                .exceptionally(
                        error -> {
                            log.debug("Failed to release port forwards", error);
                            return null;
                        })
                .alwaysRun(upnpClient::shutdown);
    }

    @VisibleForTesting
    CompletableFuture<RemoteService> getWANIPConnectionService() {
        checkState(isRunning(), "Cannot call getWANIPConnectionService() when in stopped state");
        return upnpClient.getWanIpFuture();
    }

    private SafeFuture<Void> releaseAllPortForwards() {
        // if we haven't observed the WANIPConnection service yet, we should have no port forwards to
        // release
        if (!upnpClient.getWanIpFuture().isDone()) {
            log.debug("Ports had not completed setting up, will not be able to disconnect.");
            return SafeFuture.COMPLETE;
        }

        if (forwardedPorts.isEmpty()) {
            log.debug("Port list is empty.");
            return SafeFuture.COMPLETE;
        } else {
            log.debug("Have {} ports to close", forwardedPorts.size());
        }
        final List<SafeFuture<Void>> futures = new ArrayList<>();

        while (!forwardedPorts.isEmpty()) {
            futures.add(upnpClient.releasePortForward(forwardedPorts.remove()));
        }

        // return a future that completes successfully only when each of our port delete requests
        // complete
        return SafeFuture.allOf(futures.toArray(new SafeFuture<?>[0]));
    }

    public void requestPortForward(
            final int port, final NetworkProtocol protocol, final NatServiceType serviceType) {
        checkState(isRunning(), "Cannot call requestPortForward() when in stopped state");
        checkArgument(port != 0, "Cannot map to internal port zero.");
        upnpClient
                .requestPortForward(port, protocol, serviceType)
                .thenCompose(
                        natPortMapping -> {
                            forwardedPorts.add(natPortMapping);
                            log.info("upnp success");
                            return SafeFuture.COMPLETE;
                        })
                .finish(error -> log.debug("Failed to forward port ", error));
    }
}

