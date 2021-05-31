package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;


public class JsonRpcResultResponseTest {
    private ObjectMapper serializer = new ObjectMapper();

    @Test
    public void serializeResponseWithResult() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":48,\"result\":true}";
        assertEquals(
                serializer.writeValueAsString(
                        new JsonRpcResultResponse(48, new JsonRpcBooleanResult(true))
                ),
                message
        );
    }
}
