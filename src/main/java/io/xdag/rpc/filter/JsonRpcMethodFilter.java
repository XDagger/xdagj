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
        for (ModuleDescription module: this.modules) {
            if (module.methodIsEnable(methodName)) {
                return;
            }
        }

        throw new IOException("Method not supported: " + methodName);
    }
}
