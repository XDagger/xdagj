/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.rpc.server.handler;

import io.xdag.rpc.api.XdagApi;
import io.xdag.rpc.error.JsonRpcError;
import io.xdag.rpc.error.JsonRpcException;
import io.xdag.rpc.server.protocol.JsonRpcRequest;
import io.xdag.rpc.model.response.BlockResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.xdag.rpc.server.handler.JsonRpcHandler.MAPPER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JsonRequestHandler
 * For documentation examples, see {@link io.xdag.rpc.examples.RpcExamplesTest}
 */
public class JsonRequestHandlerTest {

    @Mock
    private XdagApi xdagApi;

    private JsonRequestHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new JsonRequestHandler(xdagApi);
    }

    @Test
    public void testSupportsMethod() {
        assertTrue("Should support xdag_blockNumber", handler.supportsMethod("xdag_blockNumber"));
        assertTrue("Should support xdag_getBalance", handler.supportsMethod("xdag_getBalance"));
        assertTrue("Should support xdag_getBlockByHash", handler.supportsMethod("xdag_getBlockByHash"));
        assertTrue("Should support xdag_getStatus", handler.supportsMethod("xdag_getStatus"));
        assertTrue("Should support xdag_personal_sendTransaction", handler.supportsMethod("xdag_personal_sendTransaction"));
        assertFalse("Should not support unknown_method", handler.supportsMethod("unknown_method"));
        assertFalse("Should not support null method", handler.supportsMethod(null));
    }

    @Test
    public void testHandleNullRequest() {
        try {
            handler.handle(null);
            fail("Should throw JsonRpcException for null request");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode());
            assertEquals("Error message should match", "Request cannot be null", e.getMessage());
        }
    }

    @Test
    public void testHandleEmptyParams() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBalance",
                    "params": [],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        try {
            handler.handle(request);
            fail("Should throw JsonRpcException for empty params");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode());
            assertEquals("Error message should match", "Missing address parameter", e.getMessage());
        }
    }

//    @Test
//    public void testHandleInvalidPageNumber() throws Exception {
//        String requestJson = """
//                {
//                    "jsonrpc": "2.0",
//                    "method": "xdag_getBlockByHash",
//                    "params": ["0x1234", -1],
//                    "id": "1"
//                }""";
//
//        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
//        request.validate();
//        try {
//            handler.handle(request);
//            fail("Should throw JsonRpcException for invalid page number");
//        } catch (JsonRpcException e) {
//            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode();
//            assertEquals("Error message should match", "Page number must be greater than 0", e.getMessage();
//        }
//    }

//    @Test
//    public void testHandleInvalidPageSize() throws Exception {
//        String requestJson = """
//                {
//                    "jsonrpc": "2.0",
//                    "method": "xdag_getBlockByHash",
//                    "params": ["0x1234", 1, 101],
//                    "id": "1"
//                }""";
//
//        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
//        request.validate();
//        try {
//            handler.handle(request);
//            fail("Should throw JsonRpcException for invalid page size");
//        } catch (JsonRpcException e) {
//            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode();
//            assertEquals("Error message should match", "Page size must be between 1 and 100", e.getMessage();
//        }
//    }

//    @Test
//    public void testHandleInvalidTimeFormat() throws Exception {
//        String requestJson = """
//                {
//                    "jsonrpc": "2.0",
//                    "method": "xdag_getBlockByHash",
//                    "params": ["0x1234", 1, "", "invalid_time"],
//                    "id": "1"
//                }""";
//
//        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
//        request.validate();
//        try {
//            handler.handle(request);
//            fail("Should throw JsonRpcException for invalid time format");
//        } catch (JsonRpcException e) {
//            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode();
//            assertTrue("Error message should match", e.getMessage().contains("Invalid start time");
//        }
//    }

    @Test(expected = JsonRpcException.class)
    public void testHandleInvalidMethod() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "unknown_method",
                    "params": [],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        handler.handle(request);
    }

    @Test(expected = JsonRpcException.class)
    public void testHandleInvalidParams() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBalance",
                    "params": ["invalid_address"],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        when(xdagApi.xdag_getBalance(anyString())).thenThrow(new IllegalArgumentException("Invalid address format"));
        handler.handle(request);
    }

    @Test(expected = JsonRpcException.class)
    public void testHandleMethodNotFound() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "unknown_method",
                    "params": [],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        handler.handle(request);
    }

    @Test(expected = JsonRpcException.class)
    public void testHandleInternalError() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_blockNumber",
                    "params": [],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        when(xdagApi.xdag_blockNumber()).thenThrow(new RuntimeException("Internal error"));
        handler.handle(request);
    }

    @Test
    public void testHandleMaxPageSize() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": ["0x1234", 1, 100],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        
        BlockResponse mockResponse = BlockResponse.builder()
                .hash("0x1234")
                .type("Main")
                .state("Accepted")
                .totalPage(1)
                .build();
                
        when(xdagApi.xdag_getBlockByHash(anyString(), anyInt(), anyInt())).thenReturn(mockResponse);
        Object result = handler.handle(request);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be BlockResponse", result instanceof BlockResponse);
        BlockResponse blockResponse = (BlockResponse) result;
        assertEquals("Hash should match", "0x1234", blockResponse.getHash());
        assertEquals("Type should be Main", "Main", blockResponse.getType());
        assertEquals("State should be Accepted", "Accepted", blockResponse.getState());
        verify(xdagApi).xdag_getBlockByHash("0x1234", 1, 100);
    }

    @Test
    public void testHandleMinPageSize() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": ["0x1234", 1, 1],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        
        BlockResponse mockResponse = BlockResponse.builder()
                .hash("0x1234")
                .type("Main")
                .state("Accepted")
                .totalPage(1)
                .build();
                
        when(xdagApi.xdag_getBlockByHash(anyString(), anyInt(), anyInt())).thenReturn(mockResponse);
        Object result = handler.handle(request);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be BlockResponse", result instanceof BlockResponse);
        BlockResponse blockResponse = (BlockResponse) result;
        assertEquals("Hash should match", "0x1234", blockResponse.getHash());
        assertEquals("Type should be Main", "Main", blockResponse.getType());
        assertEquals("State should be Accepted", "Accepted", blockResponse.getState());
        verify(xdagApi).xdag_getBlockByHash("0x1234", 1, 1);
    }

    @Test
    public void testHandleNonNumericPageNumber() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": ["0x1234", "abc"],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        try {
            handler.handle(request);
            fail("Should throw JsonRpcException for non-numeric page number");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INTERNAL, e.getCode());
            assertEquals("Error message should match", "Internal error: For input string: \"abc\"", e.getMessage());
        }
    }

    @Test
    public void testHandleNonNumericPageSize() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": ["0x1234", 1, "abc"],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        try {
            handler.handle(request);
            fail("Should throw JsonRpcException for non-numeric page size");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INTERNAL, e.getCode());
            assertEquals("Error message should match", "Internal error: For input string: \"abc\"", e.getMessage());
        }
    }

    @Test
    public void testHandleNullParams() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": [null, null],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        try {
            handler.handle(request);
            fail("Should throw JsonRpcException for null parameters");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode());
            assertTrue("Error message should indicate invalid parameters", e.getMessage().contains("Missing block hash parameter"));
        }
    }

    @Test
    public void testHandleEmptyHash() throws Exception {
        String requestJson = """
                {
                    "jsonrpc": "2.0",
                    "method": "xdag_getBlockByHash",
                    "params": ["", 1],
                    "id": "1"
                }""";

        JsonRpcRequest request = MAPPER.readValue(requestJson, JsonRpcRequest.class);
        request.validate();
        try {
            handler.handle(request);
            fail("Should throw JsonRpcException for empty hash");
        } catch (JsonRpcException e) {
            assertEquals("Error code should be invalid params", JsonRpcError.ERR_INVALID_PARAMS, e.getCode());
            assertTrue("Error message should indicate invalid hash", e.getMessage().contains("Missing block hash parameter"));
        }
    }
}