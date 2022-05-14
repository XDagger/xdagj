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

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.xdag2amount;

import com.google.common.collect.Maps;
import io.xdag.Kernel;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ImportResult;
import io.xdag.rpc.Web3.CallArguments;
import io.xdag.rpc.dto.ProcessResult;
import io.xdag.utils.BasicUtils;
import io.xdag.wallet.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;

public class XdagModuleTransactionEnabled extends XdagModuleTransactionBase {

    private final Kernel kernel;

    public XdagModuleTransactionEnabled(Kernel kernel) {
        super(kernel);
        this.kernel = kernel;
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String result = super.sendRawTransaction(rawData);
        return result;
    }

    @Override
    public ProcessResult personalSendTransaction(CallArguments args, String passphrase) {

        String from = args.from;
        String to = args.to;
        String value = args.value;
        String remark = args.remark;

        ProcessResult result = ProcessResult.builder().res(true).build();

        Bytes32 hash = checkParam(from, to, value, remark,result);
        if (!result.getRes()) {
            return result;
        }
        checkPassword(passphrase,result);
        if (!result.getRes()) {
            return result;
        }

        // do xfer
        double amount = BasicUtils.getDouble(value);
        doXfer(amount,hash,remark,result);

        return result;
    }


    public void doXfer(double sendValue, Bytes32 toAddress,String remark, ProcessResult processResult) {
        long amount = xdag2amount(sendValue);
        MutableBytes32 to = MutableBytes32.create();
//        System.arraycopy(address, 8, to, 8, 24);
        to.set(8, toAddress.slice(8, 24));

        // 待转账余额
        AtomicLong remain = new AtomicLong(amount);
        // 转账输入
        Map<Address, KeyPair> ourBlocks = Maps.newHashMap();

        // our block select
        kernel.getBlockStore().fetchOurBlocks(pair -> {
            int index = pair.getKey();
            Block block = pair.getValue();
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

        // 余额不足
        if (remain.get() > 0) {
            processResult.setRes(false);
            processResult.setResInfo("No enough input");
        }
        List<String> resInfo = new ArrayList<>();
        // create transaction
        List<BlockWrapper> txs = kernel.getWallet().createTransactionBlock(ourBlocks, to, remark);
        for (BlockWrapper blockWrapper : txs) {
            kernel.getSyncMgr().syncPushBlock(blockWrapper,blockWrapper.getBlock().getHashLow());
            resInfo.add(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow()));
        }

        processResult.setRes(true);
        processResult.setResInfo(resInfo.toString());
    }

    private Bytes32 checkParam(String from, String to, String value, String remark,ProcessResult processResult) {
        Bytes32 hash = null;
        try {
            double amount = BasicUtils.getDouble(value);
            if (amount < 0) {
                processResult.setRes(false);
                processResult.setResInfo("The transfer amount must be greater than 0");
            }

            // check whether to is exist in blockchain
            if (to.length() == 32) {
                hash = Bytes32.wrap(address2Hash(to));
            } else {
                hash = Bytes32.wrap(BasicUtils.getHash(to));
            }
            if (hash == null) {
                processResult.setRes(false);
                processResult.setResInfo("To address is illegal");
            } else {
                if (kernel.getBlockchain().getBlockByHash(Bytes32.wrap(hash), false) == null) {
                    processResult.setRes(false);
                    processResult.setResInfo("To address is illegal");
                }
            }

        } catch (NumberFormatException e) {
            processResult.setRes(false);
            processResult.setResInfo(e.getMessage());
        }
        return hash;
    }

    private void checkPassword(String passphrase,ProcessResult result) {
        Wallet wallet = new Wallet(kernel.getConfig());
        try {
            boolean res = wallet.unlock(passphrase);
            result.setRes(res);
        } catch (Exception e) {
            result.setRes(false);
            result.setResInfo(e.getMessage());
        }
    }
}
