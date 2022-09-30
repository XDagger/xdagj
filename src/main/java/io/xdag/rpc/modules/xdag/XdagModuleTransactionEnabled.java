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

import com.google.common.collect.Maps;
import io.xdag.Kernel;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ImportResult;
import io.xdag.rpc.Web3.CallArguments;
import io.xdag.rpc.dto.ProcessResult;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.XdagTime;
import io.xdag.utils.exception.XdagOverFlowException;
import io.xdag.wallet.Wallet;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.KeyPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.xdag.config.Constants.CONFIRMATIONS_COUNT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.rpc.ErrorCode.*;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.xdag2amount;

public class XdagModuleTransactionEnabled extends XdagModuleTransactionBase {

    private final Kernel kernel;

    public XdagModuleTransactionEnabled(Kernel kernel) {
        super(kernel);
        this.kernel = kernel;
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return super.sendRawTransaction(rawData);
    }

    @Override
    public Object personalSendTransaction(CallArguments args, String passphrase) {

        String from = args.from;
        String to = args.to;
        String value = args.value;
        String remark = args.remark;

        ProcessResult result = ProcessResult.builder().code(SUCCESS.code()).build();

        checkParam(value, remark,result);
        if (result.getCode() != SUCCESS.code()) {
            return result.getErrMsg();
        }

        Bytes32 toHash = checkTo(to,result);
        if (result.getCode() != SUCCESS.code()) {
            return result.getErrMsg();
        }

        Bytes32 fromHash = checkFrom(from,result);
        if (result.getCode() != SUCCESS.code()) {
            return result.getErrMsg();
        }

        checkPassword(passphrase,result);
        if (result.getCode() != SUCCESS.code()) {
            return result.getErrMsg();
        }

        // do xfer
        double amount = BasicUtils.getDouble(value);
        doXfer(amount,fromHash,toHash,remark,result);

        if (result.getCode() != SUCCESS.code()) {
            return result.getErrMsg();
        } else {
            return result.getResInfo();
        }
    }


    public void doXfer(double sendValue,Bytes32 fromAddress, Bytes32 toAddress,String remark, ProcessResult processResult) {
        long amount;
        try {
            amount = xdag2amount(sendValue);
        } catch (XdagOverFlowException e){
            processResult.setCode(ERR_PARAM_INVALID.code());
            processResult.setErrMsg(ERR_PARAM_INVALID.msg());
            return;
        }
        MutableBytes32 to = MutableBytes32.create();
//        System.arraycopy(address, 8, to, 8, 24);
        to.set(8, toAddress.slice(8, 24));

        // 待转账余额
        AtomicLong remain = new AtomicLong(amount);
        // 转账输入
        Map<Address, KeyPair> ourBlocks = Maps.newHashMap();

        // 如果没有from则从节点账户里搜索
        if (fromAddress == null) {
            // our block select
            kernel.getBlockStore().fetchOurBlocks(pair -> {
                int index = pair.getKey();
                Block block = pair.getValue();
                if (XdagTime.getCurrentEpoch() < XdagTime.getEpoch(block.getTimestamp()) + 2 * CONFIRMATIONS_COUNT) {
                    return false;
                }
                if (remain.get() <= block.getInfo().getAmount()) {
                    ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, remain.get()),
                            kernel.getWallet().getAccounts().get(index));
                    remain.set(0);
                    return true;
                } else {
                    if (block.getInfo().getAmount() > 0) {
                        remain.set(remain.get() - block.getInfo().getAmount());
                        ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, block.getInfo().getAmount()),
                                kernel.getWallet().getAccounts().get(index));
                        return false;
                    }
                    return false;
                }
            });
        } else {
            MutableBytes32 from = MutableBytes32.create();
            from.set(8, toAddress.slice(8, 24));
            Block fromBlock = kernel.getBlockStore().getBlockInfoByHash(from);
            // 如果余额足够
            if (fromBlock.getInfo().getAmount() >= remain.get()) {
                int keyIndex = kernel.getBlockStore().getKeyIndexByHash(from);
                ourBlocks.put(new Address(from, XDAG_FIELD_IN, remain.get()),
                        kernel.getWallet().getAccounts().get(keyIndex));
                remain.set(0);
            }
        }

        // 余额不足
        if (remain.get() > 0) {
            processResult.setCode(ERR_BALANCE_NOT_ENOUGH.code());
            processResult.setErrMsg(ERR_BALANCE_NOT_ENOUGH.msg());
            return;
        }
        List<String> resInfo = new ArrayList<>();
        // create transaction
        List<BlockWrapper> txs = kernel.getWallet().createTransactionBlock(ourBlocks, to, remark);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                resInfo.add(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow()));
            }
        }

        processResult.setCode(SUCCESS.code());
        processResult.setResInfo(resInfo);
    }

    private Bytes32 checkFrom(String fromAddress, ProcessResult processResult) {
        if (StringUtils.isBlank(fromAddress)) {
            return null;
        } else {
            return checkAddress(fromAddress,processResult);
        }
    }

    private Bytes32 checkTo(String toAddress, ProcessResult processResult) {
        if (StringUtils.isBlank(toAddress)) {
            processResult.setCode(ERR_TO_ADDRESS_INVALID.code());
            processResult.setErrMsg(ERR_TO_ADDRESS_INVALID.msg());
            return null;
        } else {
            return checkAddress(toAddress,processResult);
        }
    }

    private Bytes32 checkAddress(String address,ProcessResult processResult) {

        Bytes32 hash = null;

        // check whether to is exist in blockchain
        if (address.length() == 32) {
            hash = Bytes32.wrap(address2Hash(address));
        } else {
            hash = Bytes32.wrap(BasicUtils.getHash(address));
        }
        if (hash == null) {
            processResult.setCode(ERR_TO_ADDRESS_INVALID.code());
            processResult.setErrMsg(ERR_TO_ADDRESS_INVALID.msg());
        } else {
            if (kernel.getBlockchain().getBlockByHash(Bytes32.wrap(hash), false) == null) {
                processResult.setCode(ERR_TO_ADDRESS_INVALID.code());
                processResult.setErrMsg(ERR_TO_ADDRESS_INVALID.msg());
            }
        }

        return hash;
    }

    private void checkParam(String value, String remark,ProcessResult processResult) {
        try {
            double amount = BasicUtils.getDouble(value);
            if (amount < 0) {
                processResult.setCode(ERR_VALUE_INVALID.code());
                processResult.setErrMsg(ERR_VALUE_INVALID.msg());
            }

        } catch (NumberFormatException e) {
            processResult.setCode(e.hashCode());
            processResult.setErrMsg(e.getMessage());
        }
    }

    private void checkPassword(String passphrase,ProcessResult result) {
        Wallet wallet = new Wallet(kernel.getConfig());
        try {
            boolean res = wallet.unlock(passphrase);
            if (!res) {
                result.setCode(ERR_WALLET_UNLOCK.code());
                result.setErrMsg(ERR_WALLET_UNLOCK.msg());
            }
        } catch (Exception e) {
            result.setCode(e.hashCode());
            result.setErrMsg(e.getMessage());
        }
    }
}
