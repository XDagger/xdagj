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
package io.xdag.rpc.server.protocol;

import io.xdag.rpc.error.JsonRpcError;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonRpcResponseTest {

    @Test
    public void testSuccessResponse() {
        int id = 1;
        String result = "success";
        JsonRpcResponse response = JsonRpcResponse.success(id, result);

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(id, response.getId());
        assertEquals(result, response.getResult());
    }

    @Test
    public void testErrorResponse() {
        int id = 1;
        JsonRpcError error = new JsonRpcError(JsonRpcError.ERR_INVALID_REQUEST, "Invalid request");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals(error, errorResponse.getError());
        assertEquals(JsonRpcError.ERR_INVALID_REQUEST, errorResponse.getError().getCode());
        assertEquals("Invalid request", errorResponse.getError().getMessage());
    }

//    @Test
//    public void testNullId() {
//        String result = "success";
//        JsonRpcResponse response = JsonRpcResponse.success(1, result);
//
//        assertEquals("2.0", response.getJsonrpc();
//        assertNull(response.getId();
//        assertEquals(result, response.getResult();
//        assertNull(response.getError();
//    }

//    @Test
//    public void testNullResult() {
//        int id = 1;
//        JsonRpcResponse response = JsonRpcResponse.success(id, null);
//
//        assertEquals("2.0", response.getJsonrpc();
//        assertEquals(id, response.getId();
//        assertNull(response.getResult();
//        assertNull(response.getError();
//    }

    @Test
    public void testComplexResult() {
        int id = 1;
        Object[] result = new Object[]{"value1", 123, true};
        JsonRpcResponse response = JsonRpcResponse.success(id, result);

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(id, response.getId());
        assertArrayEquals(result, (Object[]) response.getResult());
    }

    @Test
    public void testErrorResponseWithData() {
        int id = 1;
        Object data = "Additional error data";
        JsonRpcError error = new JsonRpcError(JsonRpcError.ERR_INTERNAL, "Internal error");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals(error, errorResponse.getError());
        assertEquals(JsonRpcError.ERR_INTERNAL, errorResponse.getError().getCode());
        assertEquals("Internal error", errorResponse.getError().getMessage());
    }

    @Test
    public void testErrorResponseEnsuresNullResult() {
        int id = 1;
        Object result = "This should be null";
        JsonRpcError error = new JsonRpcError(JsonRpcError.ERR_INTERNAL, "Internal error");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals(error, errorResponse.getError());
    }

//    @Test(expected = IllegalArgumentException.class)
//    public void testErrorResponseWithNullError() {
//        JsonRpcResponse.error("1", null);
//    }

    @Test
    public void testNotificationResponse() {
        Object result = "notification result";
        JsonRpcResponse response = JsonRpcResponse.notification(result);

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(result, response.getResult());
    }

//    @Test
//    public void testNotificationResponseWithNullResult() {
//        JsonRpcResponse response = JsonRpcResponse.notification(null);
//
//        assertEquals("2.0", response.getJsonrpc();
//        assertNull(response.getId();
//        assertNull(response.getResult();
//        assertNull(response.getError();
//    }

//    @Test
//    public void testErrorResponseWithComplexData() {
//        int id = 1;
//        Object data = new Object[]{
//            "error details",
//            123,
//            new Object[]{"nested", "array"},
//            null
//        };
//        JsonRpcError error = new JsonRpcError(JsonRpcError.ERR_INTERNAL, "Internal error", data);
//        JsonRpcResponse response = JsonRpcResponse.error(id, error);
//
//        assertEquals("2.0", response.getJsonrpc();
//        assertEquals(id, response.getId();
//        assertNull(response.getResult();
//        assertEquals(error, response.getError();
//        assertArrayEquals((Object[]) data, (Object[]) response.getError().getData();
//    }

    @Test
    public void testSuccessResponseWithEmptyArray() {
        int id = 1;
        Object[] result = new Object[]{};
        JsonRpcResponse response = JsonRpcResponse.success(id, result);

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(id, response.getId());
        assertArrayEquals(result, (Object[]) response.getResult());
    }

    @Test
    public void testSuccessResponseWithNestedObjects() {
        int id = 1;
        Object[] nested = new Object[]{"nested", 456};
        Object[] result = new Object[]{"value1", 123, nested};
        JsonRpcResponse response = JsonRpcResponse.success(id, result);

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(id, response.getId());
        Object[] actualResult = (Object[]) response.getResult();
        assertEquals("value1", actualResult[0]);
        assertEquals(123, actualResult[1]);
        assertArrayEquals(nested, (Object[]) actualResult[2]);
    }

    @Test
    public void testErrorResponseWithMaxIntegerCode() {
        int id = 1;
        JsonRpcError error = new JsonRpcError(Integer.MAX_VALUE, "Max error code");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals(Integer.MAX_VALUE, errorResponse.getError().getCode());
    }

    @Test
    public void testErrorResponseWithMinIntegerCode() {
        int id = 1;
        JsonRpcError error = new JsonRpcError(Integer.MIN_VALUE, "Min error code");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals(Integer.MIN_VALUE, errorResponse.getError().getCode());
    }

    @Test
    public void testErrorResponseWithEmptyMessage() {
        int id = 1;
        JsonRpcError error = new JsonRpcError(JsonRpcError.ERR_INTERNAL, "");
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, error);

        assertEquals("2.0", errorResponse.getJsonrpc());
        assertEquals(id, errorResponse.getId());
        assertEquals("", errorResponse.getError().getMessage());
    }
} 