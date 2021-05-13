package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class JsonRpcMessage {
    private final JsonRpcVersion version;

    public JsonRpcMessage(JsonRpcVersion version) {
        this.version = verifyVersion(version);
    }

    @JsonProperty("jsonrpc")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public JsonRpcVersion getVersion() {
        return version;
    }

    private static JsonRpcVersion verifyVersion(JsonRpcVersion version) {
        if (version != JsonRpcVersion.V2_0) {
            throw new IllegalArgumentException(
                    String.format("JSON-RPC version should always be %s, but was %s.", JsonRpcVersion.V2_0, version)
            );
        }

        return version;
    }
}
