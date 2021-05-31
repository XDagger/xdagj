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
package io.xdag.rpc.exception;

public class XdagJsonRpcRequestException extends RuntimeException {

    private final Integer code;

    protected XdagJsonRpcRequestException(Integer code, String message, Exception e) {
        super(message, e);
        this.code = code;
    }

    public XdagJsonRpcRequestException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static XdagJsonRpcRequestException transactionRevertedExecutionError() {
        return executionError("transaction reverted");
    }

    public static XdagJsonRpcRequestException unknownError(String message) {
        return new XdagJsonRpcRequestException(-32009, message);
    }

    private static XdagJsonRpcRequestException executionError(String message) {
        return new XdagJsonRpcRequestException(-32015, String.format("VM execution error: %s", message));
    }

    public static XdagJsonRpcRequestException transactionError(String message) {
        return new XdagJsonRpcRequestException(-32010, message);
    }

    public static XdagJsonRpcRequestException invalidParamError(String message) {
        return new XdagJsonRpcRequestException(-32602, message);
    }

    public static XdagJsonRpcRequestException invalidParamError(String message, Exception e) {
        return new XdagJsonRpcRequestException(-32602, message, e);
    }

    public static XdagJsonRpcRequestException unimplemented(String message) {
        return new XdagJsonRpcRequestException(-32201, message);
    }

    public static XdagJsonRpcRequestException blockNotFound(String message) {
        return new XdagJsonRpcRequestException(-32600, message);
    }

    public static XdagJsonRpcRequestException stateNotFound(String message) {
        return new XdagJsonRpcRequestException(-32600, message);
    }
}
