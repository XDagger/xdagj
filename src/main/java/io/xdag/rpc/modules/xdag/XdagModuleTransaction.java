package io.xdag.rpc.modules.xdag;

import io.xdag.rpc.Web3;

public interface XdagModuleTransaction {
    String sendTransaction(Web3.CallArguments args);

    String sendRawTransaction(String rawData);
}
