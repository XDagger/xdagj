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
package io.xdag.evm.chainspec;

import io.xdag.evm.FeeSchedule;
import io.xdag.evm.OpCode;
import io.xdag.evm.client.Transaction;
import io.xdag.evm.program.exception.OutOfGasException;

/**
 * An abstract implementation of the chain spec, based on the Homestead code base.
 *
 * Ethereum forks in history:
 *
 * <ul>
 * <li>Frontier</li>
 * <li>Ice Age</li>
 * <li>Homestead</li>
 * <li>DAO</li>
 * <li>Tangerine Whistle (EIP-150: IO-opcode gas changes, max call/create gas,
 * EIP-158: state clearing)</li>
 * <li>Spurious Dragon (EIP-160: EXP cost increase, EIP-161: State trie
 * clearing, EIP-170 contract size limit)</li>
 * <li>Byzantium</li>
 * <li>Constantinople</li>
 * </ul>
 */
public class BaseSpec implements Spec {
    private static final FeeSchedule feeSchedule = new FeeSchedule();
    private static final PrecompiledContracts precompiledContracts = new BasePrecompiledContracts();

    @Override
    public FeeSchedule getFeeSchedule() {
        return feeSchedule;
    }

    @Override
    public PrecompiledContracts getPrecompiledContracts() {
        return precompiledContracts;
    }

    @Override
    public long getCallGas(OpCode op, long requestedGas, long availableGas) throws OutOfGasException {
        return availableGas;
    }

    @Override
    public long getCreateGas(long availableGas) {
        return availableGas;
    }

    @Override
    public long getTransactionCost(Transaction tx) {
        FeeSchedule fs = getFeeSchedule();

        long cost = tx.isCreate() ? fs.getTRANSACTION_CREATE_CONTRACT() : fs.getTRANSACTION();
        for (byte b : tx.getData().toArray()) {
            cost += (b == 0) ? fs.getTX_ZERO_DATA() : fs.getTX_NO_ZERO_DATA();
        }

        return cost;
    }

    @Override
    public int maxContractSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean createEmptyContractOnOOG() {
        return false;
    }

    @Override
    public boolean eip1052() {
        return false;
    }

    @Override
    public boolean eip145() {
        return false;
    }

    @Override
    public boolean eip1283() {
        return false;
    }

    @Override
    public boolean eip1014() {
        return false;
    }
}

