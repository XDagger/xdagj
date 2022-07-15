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

import io.xdag.Kernel;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagBlock;
import io.xdag.rpc.Web3;
import io.xdag.rpc.Web3.CallArguments;
import io.xdag.rpc.dto.ProcessResult;
import io.xdag.utils.BasicUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XdagModuleTransactionBase implements XdagModuleTransaction {

    protected static final Logger logger = LoggerFactory.getLogger(XdagModuleTransactionBase.class);

    private final Kernel kernel;

    public XdagModuleTransactionBase(Kernel kernel) {

        this.kernel = kernel;
    }

    @Override
    public synchronized String sendTransaction(Web3.CallArguments args) {
        // 1. process args
//        byte[] from = Hex.decode(args.from);
//        byte[] to = Hex.decode(args.to);
//        BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
        // 2. create a transaction
        // 3. try to add blockchain
        return null;
    }

    @Override
    public String sendRawTransaction(String rawData) {

        // 1. build transaction
        // 2. try to add blockchain
        System.out.println(rawData);
        Block block = new Block(new XdagBlock(Hex.decode(rawData)));
        kernel.getSyncMgr().importBlock(
                new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL()));
        return BasicUtils.hash2Address(block.getHash());
    }

    @Override
    public Object personalSendTransaction(CallArguments args, String passphrase) {
        return null;
    }
}
