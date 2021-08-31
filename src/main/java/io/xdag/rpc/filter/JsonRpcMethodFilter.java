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

package io.xdag.rpc.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import com.googlecode.jsonrpc4j.RequestInterceptor;
import io.xdag.rpc.modules.ModuleDescription;
import java.io.IOException;
import java.util.List;

public class JsonRpcMethodFilter implements RequestInterceptor {

    private List<ModuleDescription> modules;

    /**
     * This checks the JSON RPC invoked method against the received list of modules
     *
     * @param modules list of configured modules
     */
    public JsonRpcMethodFilter(List<ModuleDescription> modules) {
        this.modules = modules;
    }

    @Override
    public void interceptRequest(JsonNode node) throws IOException {
        if (node.hasNonNull(JsonRpcBasicServer.METHOD)) {
            checkMethod(node.get(JsonRpcBasicServer.METHOD).asText());
        }
    }

    private void checkMethod(String methodName) throws IOException {
        for (ModuleDescription module : this.modules) {
            if (module.methodIsEnable(methodName)) {
                return;
            }
        }

        throw new IOException("Method not supported: " + methodName);
    }
}
