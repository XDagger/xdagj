package io.xdag.rpc.modules.xdag;

import io.xdag.rpc.dto.BlockResultDTO;

public interface XdagModuleChain {
    String getCoinBase();
    BlockResultDTO getBlockByHash(String hash, boolean full);
    BlockResultDTO getBlockByNumber(String bnOrId, boolean full);
}
