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
