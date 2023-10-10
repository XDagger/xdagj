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
package io.xdag.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.xdag.config.Config;
import io.xdag.config.spec.DagSpec;
import io.xdag.core.state.Account;
import io.xdag.core.state.AccountState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Transaction executor
 */
@Slf4j
@Getter
@Setter
public class TransactionExecutor {

    private DagSpec dagSpec;

    /**
     * Creates a new transaction executor.
     */
    public TransactionExecutor(Config config) {
        this.dagSpec = config.getDagSpec();
    }

    /**
     * Execute a list of transactions.
     * <p>
     * NOTE: transaction format and signature are assumed to be success.
     *
     * @param txs
     *            transactions
     * @param as
     *            account state
     */
    public List<TransactionResult> execute(List<Transaction> txs, AccountState as) {
        List<TransactionResult> results = new ArrayList<>();

        for (Transaction tx : txs) {
            TransactionResult result = new TransactionResult();
            results.add(result);

            TransactionType type = tx.getType();
            byte[] from = tx.getFrom();
            byte[] to = tx.getTo();
            XAmount value = tx.getValue();
            long nonce = tx.getNonce();
            XAmount fee = tx.getFee();
            byte[] data = tx.getData();

            Account acc = as.getAccount(from);
            XAmount available = acc.getAvailable();

            try {
                // check nonce
                if (nonce != acc.getNonce()) {
                    result.setCode(TransactionResult.Code.INVALID_NONCE);
                    continue;
                }


                if (fee.lessThan(dagSpec.getMinTransactionFee())) {
                    result.setCode(TransactionResult.Code.INVALID_FEE);
                    continue;
                }


                // check data length
                if (data.length > dagSpec.getMaxTransactionDataSize(type)) {
                    result.setCode(TransactionResult.Code.INVALID_DATA);
                    continue;
                }

                switch (type) {
                case TRANSFER: {
                    if (fee.lessThanOrEqual(available) && value.lessThanOrEqual(available)
                            && value.add(fee).lessThanOrEqual(available)) {
                        as.adjustAvailable(from, value.add(fee).negate());
                        as.adjustAvailable(to, value);
                    } else {
                        result.setCode(TransactionResult.Code.INSUFFICIENT_AVAILABLE);
                    }
                    break;
                }
                default:
                    // unsupported transaction type
                    result.setCode(TransactionResult.Code.INVALID_TYPE);
                    break;
                }
            } catch (ArithmeticException ae) {
                log.warn("An arithmetic exception occurred during transaction execution: {}", tx);
                result.setCode(TransactionResult.Code.INVALID);
            }

            if (result.getCode().isAcceptable()) {
                as.increaseNonce(from);
            }
        }

        return results;
    }

    /**
     * Execute one transaction.
     * <p>
     * NOTE: transaction format and signature are assumed to be success.
     *
     * @param as
     *            account state
     */
    public TransactionResult execute(Transaction tx, AccountState as) {
        return execute(Collections.singletonList(tx), as).get(0);
    }
}
