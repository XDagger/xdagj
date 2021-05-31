package io.xdag.rpc.modules.web3;

import io.xdag.rpc.dto.ETHBlockResultDTO;
import io.xdag.rpc.dto.ETHTransactionReceiptDTO;
import io.xdag.rpc.modules.eth.EthModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;

public class Web3EthModuleImpl implements Web3EthModule{
    private static final Logger logger = LoggerFactory.getLogger(Web3XdagModuleImpl.class);

    class SyncingResult {
        public String currentBlock;
        public String highestBlock;
    }

    private final EthModule ethModule;

    public Web3EthModuleImpl(EthModule ethModule) {
        this.ethModule = ethModule;
    }

    @Override
    public EthModule getEthModule() {
        return ethModule;
    }

    @Override
    public String eth_protocolVersion() {
        return Integer.toString(1);
    }

    @Override
    public String eth_coinbase() {
        return "0x88b221a282a64df608a820bae740425e5f439d1c";
    }

    @Override
    public String eth_blockNumber() {
        return toQuantityJsonHex(12);
    }

    @Override
    public String eth_getBalance(String address, String block) throws Exception {
        return toQuantityJsonHex(1000);
    }

    @Override
    public String eth_getBalance(String address) throws Exception {
        return toQuantityJsonHex(2000);
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        return "0x0";
    }

    @Override
    public ETHBlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        return null;
    }

    @Override
    public ETHTransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        return null;
    }
}
