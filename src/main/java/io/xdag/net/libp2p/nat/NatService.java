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
import io.xdag.utils.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class NatService extends Service {


    private final Optional<NatManager> maybeNatManager;
    private final boolean isDiscoveryEnabled;
    private final int p2pPort;

    NatService(
            final int p2pPort,
            final boolean isDiscoveryEnabled,
            final Optional<NatManager> maybeNatManager) {
        this.p2pPort = p2pPort;
        this.isDiscoveryEnabled = isDiscoveryEnabled;
        this.maybeNatManager = maybeNatManager;
    }

    public NatService(
            final NatConfiguration natConfiguration,
            final int p2pPort,
            final boolean isDiscoveryEnabled) {
        this(
                p2pPort,
                isDiscoveryEnabled,
                natConfiguration.getNatMethod().equals(NatMethod.UPNP)
                        ? Optional.of(new NatManager())
                        : Optional.empty());
    }

    @Override
    protected SafeFuture<Void> doStart() {
        if (maybeNatManager.isEmpty()) {
            return SafeFuture.COMPLETE;
        }
        final NatManager natManager = maybeNatManager.get();
        return natManager
                .start()
                .thenRun(
                        () -> {
                            natManager.requestPortForward(p2pPort, NetworkProtocol.TCP, NatServiceType.XDAG_P2P);
                            if (isDiscoveryEnabled) {
                                natManager.requestPortForward(
                                        p2pPort, NetworkProtocol.UDP, NatServiceType.XDAG_DISCOVERY);
                            }
                        });
    }

    @Override
    protected SafeFuture<?> doStop() {
        return maybeNatManager.map(NatManager::stop).orElse(SafeFuture.completedFuture(null));
    }
}

