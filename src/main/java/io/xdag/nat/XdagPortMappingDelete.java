package io.xdag.nat;

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
    @SuppressWarnings("rawtypes")
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
