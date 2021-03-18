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
    @SuppressWarnings("rawtypes")
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
     * @param defaultMsg A user-friendly error message generated from the invocation exception and response.
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
