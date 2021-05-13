package io.xdag.rpc.jsonrpc;

public abstract class JsonRpcResult implements JsonRpcResultOrError{
    @Override
    public JsonRpcIdentifiableMessage responseFor(int messageId) {
        return new JsonRpcResultResponse(messageId, this);
    }
}
