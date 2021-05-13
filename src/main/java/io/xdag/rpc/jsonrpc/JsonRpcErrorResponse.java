package io.xdag.rpc.jsonrpc;

import java.util.Objects;

public class JsonRpcErrorResponse extends JsonRpcIdentifiableMessage {
    private final JsonRpcError error;

    public JsonRpcErrorResponse(int id, JsonRpcError error) {
        super(JsonRpcVersion.V2_0, id);
        this.error = Objects.requireNonNull(error);
    }

    @SuppressWarnings("unused")
    public JsonRpcError getError() {
        return error;
    }
}
