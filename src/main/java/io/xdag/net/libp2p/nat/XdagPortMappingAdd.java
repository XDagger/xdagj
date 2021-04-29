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
import lombok.extern.slf4j.Slf4j;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.PortMappingAdd;
import org.jupnp.support.model.PortMapping;

/**
 * @author wawa
 */
@Slf4j
public class XdagPortMappingAdd extends PortMappingAdd {

    private final SafeFuture<NatPortMapping> future = new SafeFuture<>();

    public XdagPortMappingAdd(Service<?, ?> service, PortMapping portMapping) {
        super(service, portMapping);
    }

    /**
     * Called when the action invocation succeeded.
     *
     * @param invocation The successful invocation, call its <code>getOutput()</code> method for results.
     */
    @Override
    public void success(final ActionInvocation invocation) {
        log.info(
                "Port forward request for {} {} -> {} succeeded.",
                portMapping.getProtocol(),
                portMapping.getInternalPort(),
                portMapping.getExternalPort());

        final NatServiceType natServiceType = NatServiceType.fromString(portMapping.getDescription());
        final NatPortMapping natPortMapping =
                new NatPortMapping(
                        natServiceType,
                        NetworkProtocol.valueOf(portMapping.getProtocol().name()),
                        portMapping.getInternalClient(),
                        portMapping.getRemoteHost(),
                        portMapping.getExternalPort().getValue().intValue(),
                        portMapping.getInternalPort().getValue().intValue());

        future.complete(natPortMapping);
    }

    /**
     * Because the underlying jupnp library omits generics info in this method signature, we must too
     * when we override it.
     */
    @Override
    public void failure(
            final ActionInvocation invocation, final UpnpResponse operation, final String msg) {
        log.warn(
                "Port forward request for {} {} -> {} failed: {}",
                portMapping.getProtocol(),
                portMapping.getInternalPort(),
                portMapping.getExternalPort(),
                msg);
        future.completeExceptionally(new Exception(msg));
    }

    public SafeFuture<NatPortMapping> getFuture() {
        return future;
    }
}
