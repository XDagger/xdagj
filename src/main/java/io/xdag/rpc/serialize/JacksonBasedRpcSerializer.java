package io.xdag.rpc.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.rpc.jsonrpc.JsonRpcMessage;
import io.xdag.rpc.modules.XdagJsonRpcRequest;

import java.io.IOException;
import java.io.InputStream;

public class JacksonBasedRpcSerializer implements JsonRpcSerializer {
    //From https://fasterxml.github.io/jackson-databind/javadoc/2.5/com/fasterxml/jackson/databind/ObjectMapper.html
    // ObjectMapper is thread-safe as long as the config methods are not called after the serialiation begins.
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String serializeMessage(JsonRpcMessage message) throws JsonProcessingException {
        return mapper.writeValueAsString(message);
    }

    @Override
    public XdagJsonRpcRequest deserializeRequest(InputStream source) throws IOException {
        return mapper.readValue(source, XdagJsonRpcRequest.class);
    }
}
