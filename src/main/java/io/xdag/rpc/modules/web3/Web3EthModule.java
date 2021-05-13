//package io.xdag.rpc.modules.web3;
//
//import io.xdag.rpc.Web3;
//
//import java.math.BigInteger;
//import java.util.Map;
//
//public interface Web3EthModule {
//    default String[] eth_accounts() {
//        return getEthModule().accounts();
//    }
//
//    default String eth_sign(String addr, String data) {
//        return getEthModule().sign(addr, data);
//    }
//
////    default String eth_call(Web3.CallArguments args, String bnOrId) {
////        return getEthModule().call(args, bnOrId);
////    }
//
////    default String eth_estimateGas(Web3.CallArguments args) {
////        return getEthModule().estimateGas(args);
////    }
//
//
//
////    default Map<String, Object> eth_bridgeState() throws Exception {
////        return getEthModule().bridgeState();
////    }
//
////    default String eth_chainId() {
////        return getEthModule().chainId();
////    }
//
//    EthModule getEthModule();
//
//    String eth_protocolVersion();
//
//    Object eth_syncing();
//
//    String eth_coinbase();
//
//    boolean eth_mining();
//
//    BigInteger eth_hashrate();
//
//    String eth_gasPrice();
//
//    String eth_blockNumber();
//
//    String eth_getBalance(String address, String block) throws Exception;
//
//    String eth_getBalance(String address) throws Exception;
//
//    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;
//
//    String eth_getTransactionCount(String address, String blockId) throws Exception ;
//
//    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;
//
//    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;
//
//    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;
//
//    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;
//
//    default String eth_getCode(String address, String blockId) {
//        return getEthModule().getCode(address, blockId);
//    }
//
//    default String eth_sendRawTransaction(String rawData) {
//        return getEthModule().sendRawTransaction(rawData);
//    }
//
//    default String eth_sendTransaction(Web3.CallArguments args) {
//        return getEthModule().sendTransaction(args);
//    }
//
//    BlockResultDTO eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception;
//
//    BlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception;
//
//    TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception;
//
//    TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception;
//
//    TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception;
//
//    TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception;
//
//    BlockResultDTO eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception;
//
//    BlockResultDTO eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception;
//
//    String[] eth_getCompilers();
//
//    Map<String, CompilationResultDTO> eth_compileLLL(String contract);
//
//    Map<String, CompilationResultDTO> eth_compileSerpent(String contract);
//
//    Map<String, CompilationResultDTO> eth_compileSolidity(String contract);
//
//    String eth_newFilter(Web3.FilterRequest fr) throws Exception;
//
//    String eth_newBlockFilter();
//
//    String eth_newPendingTransactionFilter();
//
//    boolean eth_uninstallFilter(String id);
//
//    Object[] eth_getFilterChanges(String id);
//
//    Object[] eth_getFilterLogs(String id);
//
//    Object[] eth_getLogs(Web3.FilterRequest fr) throws Exception;
//
//    BigInteger eth_netHashrate();
//
//    boolean eth_submitWork(String nonce, String header, String mince);
//
//    boolean eth_submitHashrate(String hashrate, String id);
//}
