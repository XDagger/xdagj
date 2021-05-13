package io.xdag.rpc.modules.xdag;

import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.modules.xdag.XdagModuleChain;

public class XdagModuleChainBase implements XdagModuleChain {



    @Override
    public String getCoinBase() {
        return null;
    }

    @Override
    public BlockResultDTO getBlockByHash(String hash, boolean full) {
        return null;
    }

    @Override
    public BlockResultDTO getBlockByNumber(String bnOrId, boolean full) {
        return null;
    }
}
