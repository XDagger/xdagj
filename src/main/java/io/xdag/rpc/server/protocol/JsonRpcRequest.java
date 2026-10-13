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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.xdag.rpc.error.JsonRpcException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Setter
@Getter
public class JsonRpcRequest {
    // Getters and Setters
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("params")
    private Object[] params;
    
    @JsonProperty("id")
    private int id;

    /**
     * Validate the JSON-RPC request
     * @throws JsonRpcException if the request is invalid
     */
    public void validate() throws JsonRpcException {
        // Check JSON-RPC version
        if (StringUtils.isBlank(jsonrpc) || !jsonrpc.equals("2.0")) {
            throw JsonRpcException.invalidRequest("Invalid JSON-RPC version, must be '2.0'");
        }

        // Check method
        if (StringUtils.isBlank(method)) {
            throw JsonRpcException.invalidRequest("Method cannot be null or empty");
        }

        // ID can be any valid JSON value or null according to JSON-RPC 2.0 spec
        // No validation needed for id
    }
}
