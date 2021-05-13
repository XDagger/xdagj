package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

public abstract class JsonRpcRequest<T extends Enum<T>> extends JsonRpcIdentifiableMessage {
    private final T method;

    public JsonRpcRequest(
            JsonRpcVersion version,
            T method,
            int id) {
        super(version, id);
        this.method = Objects.requireNonNull(method);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public T getMethod() {
        return method;
    }
}
