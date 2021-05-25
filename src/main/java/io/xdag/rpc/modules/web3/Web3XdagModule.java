package io.xdag.rpc.modules.web3;

import io.xdag.rpc.Web3;
import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.modules.xdag.XdagModule;


public interface Web3XdagModule {

    default String[] xdag_accounts() {
        return getXdagModule().accounts();
    }

    default String xdag_sign(String addr, String data) {
        return getXdagModule().sign(addr, data);
    }

    default String xdag_chainId() {
        return getXdagModule().chainId();
    }


    XdagModule getXdagModule();

    String xdag_protocolVersion();

    Object xdag_syncing();

    String xdag_coinbase();

    String xdag_blockNumber();

    String xdag_getBalance(String address) throws Exception;

    String xdag_getTotalBalance() throws Exception;

    default BlockResultDTO xdag_getTransactionByHash(String hash, Boolean full)throws Exception{
        return xdag_getBlockByHash(hash,full);
    }

    BlockResultDTO xdag_getBlockByNumber(String bnOrId, Boolean full) throws Exception;

    default String xdag_sendRawTransaction(String rawData) {
        return getXdagModule().sendRawTransaction(rawData);
    }

    default String xdag_sendTransaction(Web3.CallArguments args) {
        return getXdagModule().sendTransaction(args);
    }

    BlockResultDTO xdag_getBlockByHash(String blockHash, Boolean full) throws Exception;

}
