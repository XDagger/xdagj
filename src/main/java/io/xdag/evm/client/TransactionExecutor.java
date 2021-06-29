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
package io.xdag.evm.client;

import static io.xdag.utils.Numeric.isCovers;

import io.xdag.evm.DataWord;
import io.xdag.evm.OpCode;
import io.xdag.evm.chainspec.Spec;
import io.xdag.evm.program.Program;
import io.xdag.evm.program.ProgramResult;
import io.xdag.evm.program.invoke.ProgramInvoke;
import io.xdag.evm.program.invoke.ProgramInvokeFactory;
import io.xdag.evm.program.invoke.ProgramInvokeFactoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;
import java.util.ArrayList;

@Slf4j
public class TransactionExecutor {
    private final Transaction tx;
    private final Block block;
    private long basicTxCost;

    private final Repository repo;
    private final BlockStore blockStore;

    private final Spec spec;
    private final ProgramInvokeFactory invokeFactory;
    private final long gasUsedInTheBlock;

    private TransactionReceipt receipt;

    public TransactionExecutor(Transaction tx, Block block, Repository repo, BlockStore blockStore) {
        this(tx, block, repo, blockStore, Spec.DEFAULT, new ProgramInvokeFactoryImpl(), 0);
    }

    public TransactionExecutor(Transaction tx, Block block, Repository repo, BlockStore blockStore,
                               Spec spec, ProgramInvokeFactory invokeFactory, long gasUsedInTheBlock) {
        this.tx = tx;
        this.block = block;
        this.basicTxCost = spec.getTransactionCost(tx);

        this.repo = repo;
        this.blockStore = blockStore;

        this.spec = spec;
        this.invokeFactory = invokeFactory;
        this.gasUsedInTheBlock = gasUsedInTheBlock;
    }

    /**
     * Do basic validation, e.g. nonce, balance and gas check.
     *
     * @return true if the transaction is ready to execute; otherwise false.
     */
    protected boolean prepare() {
        BigInteger txGas = BigInteger.valueOf(tx.getGas());
        BigInteger blockGasLimit = BigInteger.valueOf(block.getGasLimit());

        if (txGas.add(BigInteger.valueOf(gasUsedInTheBlock)).compareTo(blockGasLimit) > 0) {
            log.warn("Too much gas used in this block");
            return false;
        }

        if (txGas.compareTo(BigInteger.valueOf(basicTxCost)) < 0) {
            log.warn("Not enough gas to cover basic transaction cost: required = {}, actual = {}", basicTxCost,
                    txGas);
            return false;
        }

        long reqNonce = repo.getNonce(tx.getFrom());
        long txNonce = tx.getNonce();
        if (reqNonce != txNonce) {
            log.warn("Invalid nonce: required = {}, actual = {}", reqNonce, txNonce);
            return false;
        }

        BigInteger txGasCost = tx.getGasPrice().multiply(txGas);
        BigInteger totalCost = tx.getValue().add(txGasCost);
        BigInteger senderBalance = repo.getBalance(tx.getFrom());
        if (!isCovers(senderBalance, totalCost)) {
            log.warn("Not enough balance: required = {}, actual = {}", totalCost, senderBalance);
            return false;
        }

        return true;
    }

    /**
     * Executes the transaction.
     */
    protected ProgramResult execute() {
        // [PRE-INVOKE] debit the gas from the sender's account
        repo.addBalance(tx.getFrom(), tx.getGasPrice().multiply(BigInteger.valueOf(tx.getGas())).negate());

        // phantom invoke
        Bytes ops = Bytes.EMPTY;
        ProgramInvoke invoke = invokeFactory.createProgramInvoke(tx, block, repo, blockStore);
        Program program = new Program(ops, invoke, spec);

        // [1] spend basic transaction cost
        program.spendGas(basicTxCost, "Basic transaction cost");

        // [2] make a CALL/CREATE invocation
        ProgramResult invokeResult;
        Bytes txData = tx.getData();
        program.memorySave(0, txData.toArray()); // write the tx data into memory
        if (tx.isCreate()) {
            long gas = program.getGasLeft();

            // nonce and gas spending are within the createContract method

            invokeResult = program.createContract(DataWord.of(tx.getValue()), DataWord.ZERO,
                    DataWord.of(txData.size()), gas);
        } else {
            OpCode type = OpCode.CALL;
            long gas = program.getGasLeft();
            DataWord codeAddress = DataWord.of(tx.getTo());
            DataWord value = DataWord.of(tx.getValue());
            DataWord inDataOffs = DataWord.ZERO;
            DataWord inDataSize = DataWord.of(txData.size());
            DataWord outDataOffs = DataWord.of(txData.size());
            DataWord outDataSize = DataWord.ZERO;

            // increase the nonce of sender
            repo.increaseNonce(tx.getFrom());
            // spend the call cost
            program.spendGas(gas, "transaction call");

            invokeResult = program.callContract(type, gas, codeAddress, value, inDataOffs, inDataSize, outDataOffs,
                    outDataSize);
        }

        // [3] post-invocation processing
        if (invokeResult.getException() == null && !invokeResult.isRevert()) {
            // commit deleted accounts
            for (Bytes address : invokeResult.getDeleteAccounts()) {
                repo.delete(address);
            }

            // handle future refund
            long gasUsed = program.getGasUsed();
            long suicideRefund = invokeResult.getDeleteAccounts().size() * spec.getFeeSchedule().getSUICIDE_REFUND();
            long qualifiedRefund = Math.min(invokeResult.getFutureRefund() + suicideRefund, gasUsed / 2);
            program.refundGas(qualifiedRefund, "Future refund");
            program.resetFutureRefund();
        }

        // [4] fix the program's result
        ProgramResult result = program.getResult();
        result.setReturnData(invokeResult.getReturnData());
        result.setException(invokeResult.getException());
        result.setRevert(invokeResult.isRevert());
        result.setInternalTransactions(new ArrayList<>(invokeResult.getInternalTransactions()));
        // others have been merged after the invocation

        // [POST-INVOKE] credit the sender for remaining gas
        repo.addBalance(tx.getFrom(), tx.getGasPrice().multiply(BigInteger.valueOf(program.getGasLeft())));

        return result;
    }

    /**
     * Execute the transaction and returns a receipt.
     *
     * @return a transaction receipt, or NULL if the transaction is rejected
     */
    public TransactionReceipt run() {
        if (receipt != null) {
            return receipt;
        }

        // prepare
        if (!prepare()) {
            return null;
        }

        // execute
        ProgramResult result = execute();

        receipt = new TransactionReceipt(tx,
                result.getException() == null && !result.isRevert(),
                result.getGasUsed(),
                result.getReturnData(),
                result.getLogs(),
                new ArrayList<>(result.getDeleteAccounts()),
                result.getInternalTransactions());
        return receipt;
    }
}
