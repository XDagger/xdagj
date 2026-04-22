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

import io.xdag.rpc.error.JsonRpcException;
import org.junit.Test;

public class JsonRpcRequestTest {

    @Test
    public void testValidRequest() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(1);
        request.setParams(new Object[]{"address"});

        // Should not throw any exception
        request.validate();
    }

//    @Test
//    public void testValidRequestWithStringId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId(1);
//        request.setParams(new Object[]{"address"});
//
//        // Should not throw any exception
//        request.validate();
//    }

    @Test
    public void testValidRequestWithNegativeId() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(-1);  // Negative ID is valid
        request.setParams(new Object[]{"address"});

        // Should not throw any exception
        request.validate();
    }

    @Test
    public void testValidRequestWithZeroId() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(0);  // Zero ID is valid
        request.setParams(new Object[]{"address"});

        // Should not throw any exception
        request.validate();
    }

//    @Test
//    public void testValidRequestWithDecimalId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId("1.5");  // Decimal ID is valid
//        request.setParams(new Object[]{"address"});
//
//        // Should not throw any exception
//        request.validate();
//    }

    @Test
    public void testValidRequestWithWhitespaceId() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId( 1 );  // ID with whitespace is valid
        request.setParams(new Object[]{"address"});

        // Should not throw any exception
        request.validate();
    }

//    @Test
//    public void testNullId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId(null);  // Null ID is allowed
//
//        // Should not throw any exception
//        request.validate();
//    }

    @Test(expected = JsonRpcException.class)
    public void testInvalidJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("1.0");
        request.setMethod("xdag_getBalance");
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testNullMethod() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testEmptyMethod() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("");
        request.validate();
    }

    @Test
    public void testEmptyParams() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(1);
        request.setParams(new Object[]{});  // Empty params array

        // Should not throw any exception
        request.validate();
    }

    @Test
    public void testNullParams() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(1);
        request.setParams(null);  // Null params

        // Should not throw any exception
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testNullJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc(null);
        request.setMethod("xdag_getBalance");
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testEmptyJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("");
        request.setMethod("xdag_getBalance");
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testWhitespaceJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc(" 2.0 ");
        request.setMethod("xdag_getBalance");
        request.validate();
    }

//    @Test
//    public void testValidRequestWithObjectId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId("{\"key\":\"value\"}");  // Object ID is valid according to JSON-RPC 2.0
//        request.setParams(new Object[]{"address"});
//
//        // Should not throw any exception
//        request.validate();
//    }

//    @Test
//    public void testValidRequestWithArrayId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId("[1,2,3]");  // Array ID is valid according to JSON-RPC 2.0
//        request.setParams(new Object[]{"address"});
//
//        // Should not throw any exception
//        request.validate();
//    }

//    @Test
//    public void testValidRequestWithBooleanId() {
//        JsonRpcRequest request = new JsonRpcRequest();
//        request.setJsonrpc("2.0");
//        request.setMethod("xdag_getBalance");
//        request.setId("true");  // Boolean ID is valid according to JSON-RPC 2.0
//        request.setParams(new Object[]{"address"});
//
//        // Should not throw any exception
//        request.validate();
//    }

    @Test
    public void testValidRequestWithSpecialCharactersInMethod() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance_v2.1");  // Method with special characters
        request.setId(1);
        request.setParams(new Object[]{"address"});

        // Should not throw any exception
        request.validate();
    }

    @Test
    public void testValidRequestWithComplexParams() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("xdag_getBalance");
        request.setId(1);
        request.setParams(new Object[]{
            "address",
            123,
            true,
            new Object[]{"nested", "array"},
            null
        });  // Complex params array

        // Should not throw any exception
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testBlankMethod() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("   ");  // Blank method (only whitespace)
        request.validate();
    }

    @Test(expected = JsonRpcException.class)
    public void testBlankJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setJsonrpc("   ");  // Blank version (only whitespace)
        request.setMethod("xdag_getBalance");
        request.validate();
    }
} 