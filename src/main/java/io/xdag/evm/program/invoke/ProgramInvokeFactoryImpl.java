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
package io.xdag.evm.program.invoke;

import java.math.BigInteger;

import io.xdag.evm.client.Block;
import io.xdag.evm.client.BlockStore;
import io.xdag.evm.client.Repository;
import io.xdag.evm.client.Transaction;
import io.xdag.evm.DataWord;
import io.xdag.evm.program.Program;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class ProgramInvokeFactoryImpl implements ProgramInvokeFactory {

    @Override
    public ProgramInvoke createProgramInvoke(Transaction tx, Block block, Repository repository,
                                             BlockStore blockStore) {

        // creates an phantom invoke, from the sender to the sender, at depth -1

        Bytes address = tx.getFrom();
        Bytes origin = tx.getFrom();
        Bytes caller = tx.getFrom();
        long gas = tx.getGas();
        BigInteger gasPrice = tx.getGasPrice();
        BigInteger callValue = tx.getValue();
        Bytes callData = tx.getData();

        Bytes32 prevHash = block.getParentHash();
        Bytes coinbase = block.getCoinbase();
        long timestamp = block.getTimestamp();
        long number = block.getNumber();
        BigInteger difficulty = block.getDifficulty();
        long gasLimit = block.getGasLimit();

        Repository originalRepository = repository.clone();
        int callDepth = -1;

        return new ProgramInvokeImpl(DataWord.of(address), DataWord.of(origin), DataWord.of(caller),
                gas, DataWord.of(gasPrice), DataWord.of(callValue), callData,
                DataWord.of(prevHash), DataWord.of(coinbase), DataWord.of(timestamp), DataWord.of(number),
                DataWord.of(difficulty), DataWord.of(gasLimit),
                repository, originalRepository, blockStore, callDepth, false);
    }

    @Override
    public ProgramInvoke createProgramInvoke(Program program,
            DataWord callerAddress, DataWord toAddress,
            long gas, DataWord value, Bytes data,
            Repository repository, BlockStore blockStore, boolean isStaticCall) {

        DataWord origin = program.getOriginAddress();
        DataWord gasPrice = program.getGasPrice();

        DataWord prevHash = program.getBlockPrevHash();
        DataWord coinbase = program.getBlockCoinbase();
        DataWord timestamp = program.getBlockTimestamp();
        DataWord number = program.getBlockNumber();
        DataWord difficulty = program.getBlockDifficulty();
        DataWord gasLimit = program.getBlockGasLimit();

        Repository originalRepository = program.getOriginalRepository();
        int callDepth = program.getCallDepth() + 1;

        return new ProgramInvokeImpl(
                toAddress,
                origin, callerAddress, gas, gasPrice, value, data,
                prevHash, coinbase, timestamp, number, difficulty, gasLimit,
                repository, originalRepository, blockStore, callDepth, isStaticCall);
    }
}
