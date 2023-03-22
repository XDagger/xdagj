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

import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.xdag.rpc.dto.ETHBlockResultDTO;
import io.xdag.rpc.dto.ETHTransactionReceiptDTO;
import io.xdag.rpc.modules.eth.EthModule;

public class Web3EthModuleImpl implements Web3EthModule {

    private static final Logger logger = LoggerFactory.getLogger(Web3XdagModuleImpl.class);
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

    static class SyncingResult {
        public String currentBlock;
        public String highestBlock;
    }
}
