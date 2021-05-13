package io.xdag.rpc.jsonrpc;

import java.util.Objects;

public class JsonRpcResultResponse extends JsonRpcIdentifiableMessage {
    private final JsonRpcResult result;

    public JsonRpcResultResponse(int id, JsonRpcResult result) {
        super(JsonRpcVersion.V2_0, id);
        this.result = Objects.requireNonNull(result);
    }

    @SuppressWarnings("unused")
    public JsonRpcResult getResult() {
        return result;
    }
}
