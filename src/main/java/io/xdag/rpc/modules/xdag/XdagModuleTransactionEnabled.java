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

import static io.xdag.utils.BasicUtils.address2Hash;

import io.xdag.Kernel;
import io.xdag.core.Blockchain;
import io.xdag.rpc.Web3.CallArguments;
import io.xdag.utils.BasicUtils;
import io.xdag.wallet.Wallet;
import lombok.Builder;
import org.apache.tuweni.bytes.Bytes32;

public class XdagModuleTransactionEnabled extends XdagModuleTransactionBase {

    private final Kernel kernel;

    public XdagModuleTransactionEnabled(Blockchain blockchain, Kernel kernel) {
        super(blockchain);
        this.kernel = kernel;
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String result = super.sendRawTransaction(rawData);
        return result;
    }

    @Override
    public String personalSendTransaction(CallArguments args, String passphrase) {

        String from = args.from;
        String to = args.to;
        String value = args.value;
        String remark = args.remark;

        ProcessResult result = checkParam(from, to, value, remark);

        // TODO return the error message

        if (!checkPassword(passphrase)) {
            // TODO return error about the password error
        }

//        return super.personalSendTransaction(args, passphrase);
        return null;
    }

    private ProcessResult checkParam(String from, String to, String value, String remark) {
        ProcessResult.ProcessResultBuilder processResultBuilder = ProcessResult.builder();

        try {
            double amount = BasicUtils.getDouble(value);
            if (amount < 0) {
                processResultBuilder.res(false).resInfo("The transfer amount must be greater than 0");
            }

            // check whether to is exist in blockchain
            Bytes32 hash;
            if (to.length() == 32) {
                hash = Bytes32.wrap(address2Hash(to));
            } else {
                hash = Bytes32.wrap(BasicUtils.getHash(to));
            }
            if (hash == null) {
                processResultBuilder.res(false).resInfo("To address is illegal");
            }
            if (kernel.getBlockchain().getBlockByHash(Bytes32.wrap(hash), false) == null) {
                processResultBuilder.res(false).resInfo("To address is illegal");
            }

        } catch (NumberFormatException e) {
            processResultBuilder.res(false).resInfo(e.getMessage());
        }
        return processResultBuilder.build();
    }

    private boolean checkPassword(String passphrase) {
        Wallet wallet = new Wallet(kernel.getConfig());
        return wallet.unlock(passphrase);
    }

    @Builder
    private class ProcessResult {

        private boolean res;
        private String resInfo;
    }
}
