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

import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.PortMappingDelete;
import org.jupnp.support.model.PortMapping;

@Slf4j
public class XdagPortMappingDelete extends PortMappingDelete {
    private final SafeFuture<Void> future = new SafeFuture<>();
    public XdagPortMappingDelete(Service<?, ?> service, PortMapping portMapping) {
        super(service, portMapping);
    }

    /**
     * Called when the action invocation succeeded.
     *
     * @param invocation The successful invocation, call its <code>getOutput()</code> method for results.
     */
    public void success(final ActionInvocation invocation) {
        log.info(
                "Port forward {} {} -> {} removed successfully.",
                portMapping.getProtocol(),
                portMapping.getInternalPort(),
                portMapping.getExternalPort());

        future.complete(null);
    }

    /**
     * Because the underlying jupnp library omits generics info in this method signature, we must too
     * when we override it.
     */
    @Override
    public void failure(
            final ActionInvocation invocation, final UpnpResponse operation, final String msg) {
        log.warn(
                "Port forward removal request for {} {} -> {} failed (ignoring): {}",
                portMapping.getProtocol(),
                portMapping.getInternalPort(),
                portMapping.getExternalPort(),
                msg);

        // ignore exceptions; we did our best
        future.complete(null);
    }

    public SafeFuture<Void> getFuture() {
        return future;
    }
}
