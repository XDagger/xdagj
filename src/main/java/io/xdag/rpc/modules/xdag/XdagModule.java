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

package io.xdag.rpc.modules.xdag;

import io.xdag.rpc.Web3;
import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.utils.TypeConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagModule implements XdagModuleTransaction, XdagModuleWallet, XdagModuleChain {

    private final XdagModuleWallet xdagModuleWallet;
    private final XdagModuleTransaction xdagModuleTransaction;
    private final XdagModuleChain xdagModuleChain;
    private final byte chainId;

    public XdagModule(byte chainId, XdagModuleWallet xdagModuleWallet, XdagModuleTransaction xdagModuleTransaction,
            XdagModuleChain xdagModuleChain) {
        this.chainId = chainId;
        this.xdagModuleWallet = xdagModuleWallet;
        this.xdagModuleTransaction = xdagModuleTransaction;
        this.xdagModuleChain = xdagModuleChain;
    }


    public String chainId() {
        return TypeConverter.toJsonHex(new byte[]{chainId});
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        return xdagModuleTransaction.sendTransaction(args);
    }

    @Override
    public Object personalSendTransaction(Web3.CallArguments args, String passphrase) {
        return xdagModuleTransaction.personalSendTransaction(args, passphrase);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return xdagModuleTransaction.sendRawTransaction(rawData);
    }

    @Override
    public String[] accounts() {
        return xdagModuleWallet.accounts();
    }

    @Override
    public String sign(String addr, String data) {
        return xdagModuleWallet.sign(addr, data);
    }

    @Override
    public String getCoinBase() {
        return null;
    }

    @Override
    public BlockResultDTO getBlockByHash(String hash, int page,Object... parameters ) {
        return xdagModuleChain.getBlockByHash(hash, page, parameters);
    }

    @Override
    public BlockResultDTO getBlockByNumber(String bnOrId, int page, Object... parameters) {
        return xdagModuleChain.getBlockByNumber(bnOrId, page, parameters);
    }

    @Override
    public String getRewardByNumber(String bnOrId) {
        return xdagModuleChain.getRewardByNumber(bnOrId);
    }

    @Override
    public String getBalanceByNumber(String number) {
        return xdagModuleChain.getBalanceByNumber(number);
    }

    @Override
    public Object getBlocksByNumber(String number) {
        return xdagModuleChain.getBlocksByNumber(number);
    }

    @Override
    public String getMaxXferBalance() {
        return xdagModuleChain.getMaxXferBalance();
    }
}
