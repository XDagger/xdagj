package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.annotation.JsonValue;

public class JsonRpcBooleanResult extends JsonRpcResult {
    private final boolean result;

    public JsonRpcBooleanResult(boolean result) {
        this.result = result;
    }

    @JsonValue
    public boolean getResult() {
        return result;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(result);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof JsonRpcBooleanResult)) {
            return false;
        }

        JsonRpcBooleanResult other = (JsonRpcBooleanResult) o;
        return this.result == other.result;
    }
}
