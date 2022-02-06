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

import io.xdag.evm.LogInfo;
import io.xdag.evm.program.InternalTransaction;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;

@Getter
@Setter
public class TransactionReceipt {

    private final Transaction tx;

    private final boolean success;
    private final long gasUsed;
    private final Bytes returnData;
    private final List<LogInfo> logs;

    // transient
    private final List<Bytes> deletedAccounts;
    private final List<InternalTransaction> internalTransactions;

    public TransactionReceipt(Transaction tx, boolean success, long gasUsed, Bytes returnData, List<LogInfo> logs,
                              List<Bytes> deletedAccounts, List<InternalTransaction> internalTransactions) {
        this.tx = tx;
        this.success = success;
        this.gasUsed = gasUsed;
        this.returnData = returnData;
        this.logs = logs;
        this.deletedAccounts = deletedAccounts;
        this.internalTransactions = internalTransactions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (InternalTransaction tx : internalTransactions) {
            sb.append("\n|--").append(tx.toString());
        }

        return "TransactionReceipt{"
                + "success=" + success
                + ", gasUsed=" + gasUsed
                + ", returnData=" + returnData.toHexString()
                + ", deletedAccounts=" + deletedAccounts + ", "
                + "logs=" + logs + "}"
                + sb;
    }
}

