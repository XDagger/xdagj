package io.xdag.nat;

import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.PortMappingAdd;
import org.jupnp.support.model.PortMapping;

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
    @SuppressWarnings("rawtypes")
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
    @SuppressWarnings("rawtypes")
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
