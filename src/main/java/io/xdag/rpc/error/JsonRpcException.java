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
package io.xdag.rpc.error;

import lombok.Getter;

@Getter
public class JsonRpcException extends RuntimeException {
    private final int code;
    private final String message;


    public JsonRpcException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;

    }

    public JsonRpcException(JsonRpcError error) {
        this(error.getCode(), error.getMessage());
    }

    public static JsonRpcException invalidRequest(String message) {
        return new JsonRpcException(JsonRpcError.ERR_INVALID_REQUEST, message);
    }

    public static JsonRpcException methodNotFound(String method) {
        return new JsonRpcException(JsonRpcError.ERR_METHOD_NOT_FOUND, "Method not found: " + method);
    }

    public static JsonRpcException invalidParams(String message) {
        return new JsonRpcException(JsonRpcError.ERR_INVALID_PARAMS, message);
    }

    public static JsonRpcException internalError(String message) {
        return new JsonRpcException(JsonRpcError.ERR_INTERNAL, message);
    }

    public static JsonRpcException serverError(String message) {
        return new JsonRpcException(JsonRpcError.ERR_SERVER, message);
    }

    public static JsonRpcException invalidAddress(String message) {
        return new JsonRpcException(JsonRpcError.ERR_XDAG_ADDRESS, message);
    }

    public static JsonRpcException blockNotFound(String message) {
        return new JsonRpcException(JsonRpcError.ERR_XDAG_BLOCK, message);
    }

    public static JsonRpcException insufficientFunds(String message) {
        return new JsonRpcException(JsonRpcError.ERR_XDAG_FUNDS, message);
    }

    public static JsonRpcException transactionError(String message) {
        return new JsonRpcException(JsonRpcError.ERR_XDAG_TX, message);
    }

    public static JsonRpcException poolError(String message) {
        return new JsonRpcException(JsonRpcError.ERR_XDAG_POOL, message);
    }
}
