package io.xdag.rpc.jsonrpc;

import java.util.Objects;

public class JsonRpcError implements JsonRpcResultOrError {
    private final int code;
    private final String message;

    public JsonRpcError(int code, String message) {
        this.code = code;
        this.message = Objects.requireNonNull(message);
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public JsonRpcIdentifiableMessage responseFor(int messageId) {
        return new JsonRpcErrorResponse(messageId, this);
    }
}
