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

package io.xdag.rpc;

import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.dto.ProcessResult;
import io.xdag.rpc.dto.StatusDTO;
import io.xdag.rpc.modules.web3.Web3XdagModule;
import io.xdag.rpc.modules.xdag.XdagModule;
import java.util.Map;

public class Web3Impl implements Web3 {

    Web3XdagModule web3XdagModule;

    public Web3Impl(Web3XdagModule web3XdagModule) {
        this.web3XdagModule = web3XdagModule;
    }

    @Override
    public String web3_clientVersion() {
        return null;
    }

    @Override
    public String web3_sha3(String data) throws Exception {
        return null;
    }

    @Override
    public String net_version() {
        return null;
    }

    @Override
    public String net_peerCount() {
        return null;
    }

    @Override
    public boolean net_listening() {
        return false;
    }

    @Override
    public String[] net_peerList() {
        return new String[0];
    }

    @Override
    public Map<String, String> rpc_modules() {
        return null;
    }

    @Override
    public void db_putString() {

    }

    @Override
    public void db_getString() {

    }

    @Override
    public void db_putHex() {

    }

    @Override
    public void db_getHex() {

    }

    @Override
    public String personal_newAccount(String passphrase) {
        return null;
    }

    @Override
    public String[] personal_listAccounts() {
        return new String[0];
    }

    @Override
    public String personal_importRawKey(String key, String passphrase) {
        return null;
    }

    @Override
    public Object personal_sendTransaction(CallArguments transactionArgs, String passphrase) throws Exception {
        return web3XdagModule.xdag_personal_sendTransaction(transactionArgs, passphrase);
    }

    @Override
    public boolean personal_unlockAccount(String key, String passphrase, String duration) {
        return false;
    }

    @Override
    public boolean personal_lockAccount(String key) {
        return false;
    }

    @Override
    public String personal_dumpRawKey(String address) throws Exception {
        return null;
    }

    @Override
    public XdagModule getXdagModule() {
        return web3XdagModule.getXdagModule();
    }

    @Override
    public String xdag_protocolVersion() {
        return web3XdagModule.xdag_protocolVersion();
    }

    @Override
    public Object xdag_syncing() {
        return web3XdagModule.xdag_syncing();
    }

    @Override
    public String xdag_coinbase() {
        return web3XdagModule.xdag_coinbase();
    }

    @Override
    public String xdag_blockNumber() {
        return web3XdagModule.xdag_blockNumber();
    }

    @Override
    public String xdag_getBalance(String address) throws Exception {
        return web3XdagModule.xdag_getBalance(address);
    }

    @Override
    public String xdag_getTotalBalance() throws Exception {
        return web3XdagModule.xdag_getTotalBalance();
    }

    @Override
    public BlockResultDTO xdag_getBlockByNumber(String bnOrId) {
        return web3XdagModule.xdag_getBlockByNumber(bnOrId);
    }

    @Override
    public BlockResultDTO xdag_getBlockByHash(String blockHash) {
        return web3XdagModule.xdag_getBlockByHash(blockHash);
    }

    @Override
    public StatusDTO xdag_getStatus() throws Exception {
        return web3XdagModule.xdag_getStatus();
    }
}
