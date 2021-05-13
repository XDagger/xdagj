package io.xdag.rpc.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.xdag.rpc.jsonrpc.JsonRpcMessage;
import io.xdag.rpc.modules.XdagJsonRpcRequest;

import java.io.IOException;
import java.io.InputStream;

public interface JsonRpcSerializer {
    /**
     * @return a JsonRpcMessage serialized into a JSON string
     * @throws JsonProcessingException when serialization fails
     */
    String serializeMessage(JsonRpcMessage message) throws JsonProcessingException;

    /**
     * @return an RskJsonRpcRequest deserialized from a JSON string in the source stream
     * @throws IOException when deserialization fails
     */
    XdagJsonRpcRequest deserializeRequest(InputStream source) throws IOException;
}

