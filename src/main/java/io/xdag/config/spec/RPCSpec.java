package io.xdag.config.spec;

import io.xdag.rpc.modules.ModuleDescription;

import java.util.List;

public interface RPCSpec {
    List<ModuleDescription> getRpcModules();
    boolean isRPCEnabled();
    String getRPCHost();
    int getRPCPortByHttp();
    int getRPCPortByWebSocket();
}
