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
import lombok.extern.slf4j.Slf4j;

import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.GetExternalIP;




import java.util.Optional;

@Slf4j
public class XdagGetExternalIP extends GetExternalIP {
    private Optional<String> discoveredOnLocalAddress = Optional.empty();
    private final SafeFuture<String> future = new SafeFuture<>();

    public XdagGetExternalIP(Service<?, ?> service) {
        super(service);
    }

    @Override
    public void success(final ActionInvocation invocation) {
        RemoteService service = (RemoteService) invocation.getAction().getService();
        RemoteDevice device = service.getDevice();
        RemoteDeviceIdentity identity = device.getIdentity();

        discoveredOnLocalAddress = Optional.of(identity.getDiscoveredOnLocalAddress().getHostAddress());

        super.success(invocation);
    }
    @Override
    protected void success(String result) {
        log.info("External IP address {} detected for internal address {}",
                result,
                discoveredOnLocalAddress.get());
        future.complete(result);
    }


    /**
     * Called when the action invocation failed.
     *
     * @param invocation The failed invocation, call its <code>getFailure()</code> method for more details.
     * @param operation  If the invocation was on a remote service, the response message, otherwise null.
     * @see #createDefaultFailureMessage
     */
    @Override
    public void failure(ActionInvocation invocation, UpnpResponse operation, String msg) {
        future.completeExceptionally(new Exception(msg));
    }
    public SafeFuture<String> getFuture() {
        return future;
    }

    public Optional<String> getDiscoveredOnLocalAddress() {
        return discoveredOnLocalAddress;
    }
}
