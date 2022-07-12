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

import io.xdag.rpc.Web3;
import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.dto.ConfigDTO;
import io.xdag.rpc.dto.ProcessResult;
import io.xdag.rpc.dto.StatusDTO;
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

    default BlockResultDTO xdag_getTransactionByHash(String hash) throws Exception {
        return xdag_getBlockByHash(hash);
    }

    default BlockResultDTO xdag_getBlockByNumber(String bnOrId) {
        return getXdagModule().getBlockByNumber(bnOrId);
    }

    default String xdag_getRewardByNumber(String bnOrId) {
        return getXdagModule().getRewardByNumber(bnOrId);
    }

    default String xdag_getBalanceByNumber(String bnOrId) {
        return getXdagModule().getBalanceByNumber(bnOrId);
    }

    default Object xdag_getBlocksByNumber(String bnOrId) {
        return getXdagModule().getBlocksByNumber(bnOrId);
    }

    default String xdag_sendRawTransaction(String rawData) {
        return getXdagModule().sendRawTransaction(rawData);
    }

    default String xdag_sendTransaction(Web3.CallArguments args) {
        return getXdagModule().sendTransaction(args);
    }

    default Object xdag_personal_sendTransaction(Web3.CallArguments args, String passphrase) {
        return getXdagModule().personalSendTransaction(args, passphrase);
    }

    default BlockResultDTO xdag_getBlockByHash(String blockHash) {
        return getXdagModule().getBlockByHash(blockHash);
    }

    StatusDTO xdag_getStatus() throws Exception;

    Object xdag_netType() throws Exception;
    Object xdag_poolConfig() throws Exception;
    Object xdag_netConnectionList() throws Exception;

    Object xdag_updatePoolConfig(ConfigDTO args,String passphrase) throws Exception;

}
