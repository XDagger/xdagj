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
package io.xdag.rpc.modules.web3;

import io.xdag.rpc.dto.ETHBlockResultDTO;
import io.xdag.rpc.dto.ETHTransactionReceiptDTO;
import io.xdag.rpc.modules.eth.EthModule;


public interface Web3EthModule {

    default String[] eth_accounts() {
        return getEthModule().accounts();
    }

    default String eth_sign(String addr, String data) {
        return getEthModule().sign(addr, data);
    }

//    default String eth_call(Web3.CallArguments args, String bnOrId) {
//        return getEthModule().call(args, bnOrId);
//    }

//    default String eth_estimateGas(Web3.CallArguments args) {
//        return getEthModule().estimateGas(args);
//    }



//    default Map<String, Object> eth_bridgeState() throws Exception {
//        return getEthModule().bridgeState();
//    }

    default String eth_chainId() {
        return getEthModule().chainId();
    }

    EthModule getEthModule();

    String eth_protocolVersion();

//    Object eth_syncing();

    String eth_coinbase();

//    boolean eth_mining();

//    BigInteger eth_hashrate();

    default String eth_gasPrice(){
        return "0x1";
    }

    String eth_blockNumber();

    String eth_getBalance(String address, String block) throws Exception;

    String eth_getBalance(String address) throws Exception;

    String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception;

    default String eth_getTransactionCount(String address, String blockId) {
        return "0x1";
    }

//    String eth_getBlockTransactionCountByHash(String blockHash)throws Exception;

//    String eth_getBlockTransactionCountByNumber(String bnOrId)throws Exception;

//    String eth_getUncleCountByBlockHash(String blockHash)throws Exception;

//    String eth_getUncleCountByBlockNumber(String bnOrId)throws Exception;

    default String eth_getCode(String address, String blockId) {
        return getEthModule().getCode(address, blockId);
    }

    default String eth_sendRawTransaction(String rawData) {
        return getEthModule().sendRawTransaction(rawData);
    }

//    default String eth_sendTransaction(Web3.CallArguments args) {
//        return getEthModule().sendTransaction(args);
//    }

//    BlockResultDTO eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception;

    ETHBlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception;

//    BlockResultDTO eth_getTransactionByHash(String transactionHash) throws Exception;

//    BlockResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception;

//    BlockResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception;

    ETHTransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception;

//    BlockResultDTO eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception;

//    BlockResultDTO eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception;

//    String[] eth_getCompilers();

//    Map<String, CompilationResultDTO> eth_compileLLL(String contract);

//    Map<String, CompilationResultDTO> eth_compileSerpent(String contract);

//    Map<String, CompilationResultDTO> eth_compileSolidity(String contract);

//    String eth_newFilter(Web3.FilterRequest fr) throws Exception;

//    String eth_newBlockFilter();

//    String eth_newPendingTransactionFilter();

//    boolean eth_uninstallFilter(String id);

//    Object[] eth_getFilterChanges(String id);

//    Object[] eth_getFilterLogs(String id);

//    Object[] eth_getLogs(Web3.FilterRequest fr) throws Exception;

//    BigInteger eth_netHashrate();

//    boolean eth_submitWork(String nonce, String header, String mince);

//    boolean eth_submitHashrate(String hashrate, String id);
}


