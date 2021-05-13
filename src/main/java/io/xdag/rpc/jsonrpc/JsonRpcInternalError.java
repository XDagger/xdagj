package io.xdag.rpc.jsonrpc;

public class JsonRpcInternalError extends JsonRpcError {
    public JsonRpcInternalError() {
        super(-32603, "Internal error.");
    }
}
