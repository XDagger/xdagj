package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;

public abstract class JsonRpcIdentifiableMessage extends JsonRpcMessage{
    private final int id;

    public JsonRpcIdentifiableMessage(JsonRpcVersion version, int id) {
        super(version);
        this.id = requireNonNegative(id);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public int getId() {
        return id;
    }

    private static int requireNonNegative(int id) {
        if (id < 0) {
            throw new IllegalArgumentException(
                    String.format("JSON-RPC message id should be a positive number, but was %s.", id)
            );
        }

        return id;
    }
}
