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
package io.xdag.evm.program;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.xdag.evm.LogInfo;
import org.apache.tuweni.bytes.Bytes;

/**
 * A data structure to hold the results of program.
 */
public class ProgramResult {

    private final long gasLimit; // immutable

    private long gasUsed = 0;
    private Bytes returnData = Bytes.EMPTY;
    private RuntimeException exception = null;
    private boolean isRevert = false;

    // fields below can be merged
    private List<InternalTransaction> internalTransactions = new ArrayList<>();
    private final Set<Bytes> deleteAccounts = new HashSet<>();
    private List<LogInfo> logs = new ArrayList<>();
    private long futureRefund = 0;

    private ProgramResult(long gasLimit) {
        this.gasLimit = gasLimit;
    }

    public long getGasLimit() {
        return gasLimit;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public long getGasLeft() {
        return gasLimit - gasUsed;
    }

    public void spendGas(long gas) {
        gasUsed += gas;
    }

    public void refundGas(long gas) {
        gasUsed -= gas;
    }

    public void setReturnData(Bytes returnData) {
        this.returnData = returnData;
    }

    public Bytes getReturnData() {
        return returnData;
    }

    public RuntimeException getException() {
        return exception;
    }

    public void setException(RuntimeException exception) {
        this.exception = exception;

        // if there is an exception, the return data should always be EMPTY
        // and all gas should be consumed
        if (exception != null) {
            this.gasUsed = gasLimit;
            this.returnData = Bytes.EMPTY;
        }
    }

    public void setRevert(boolean isRevert) {
        this.isRevert = isRevert;

        // if there is a REVERT, the return data should always be EMPTY
        if (isRevert) {
            this.returnData = Bytes.EMPTY;
        }
    }

    public boolean isRevert() {
        return isRevert;
    }

    public Set<Bytes> getDeleteAccounts() {
        return deleteAccounts;
    }

    public void addDeleteAccount(Bytes address) {
        deleteAccounts.add(address);
    }

    public void addDeleteAccounts(Set<Bytes> accounts) {
        deleteAccounts.addAll(accounts);
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public void addLogInfo(LogInfo log) {
        logs.add(log);
    }

    public void addLogs(List<LogInfo> logs) {
        for (LogInfo log : logs) {
            addLogInfo(log);
        }
    }

    public void setLogs(List<LogInfo> logs) {
        this.logs = logs;
    }

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    public void addInternalTransaction(InternalTransaction tx) {
        internalTransactions.add(tx);
    }

    public void addInternalTransactions(List<InternalTransaction> txs) {
        internalTransactions.addAll(txs);
    }

    public void setInternalTransactions(List<InternalTransaction> txs) {
        this.internalTransactions = txs;
    }

    public void rejectInternalTransactions() {
        for (InternalTransaction internalTx : internalTransactions) {
            internalTx.reject();
        }
    }

    public void addFutureRefund(long gasValue) {
        futureRefund += gasValue;
    }

    public long getFutureRefund() {
        return futureRefund;
    }

    public void resetFutureRefund() {
        futureRefund = 0;
    }

    public void merge(ProgramResult another) {
        addInternalTransactions(another.getInternalTransactions());

        if (another.getException() == null && !another.isRevert()) {
            addDeleteAccounts(another.getDeleteAccounts());
            addLogs(another.getLogs());
            addFutureRefund(another.getFutureRefund());
        }
    }

    public static ProgramResult createEmptyResult(long gasLimit) {
        ProgramResult result = new ProgramResult(gasLimit);
        return result;
    }

    public static ProgramResult createExceptionResult(long gasLimit, RuntimeException exception) {
        ProgramResult result = new ProgramResult(gasLimit);
        result.setException(exception);
        return result;
    }
}
