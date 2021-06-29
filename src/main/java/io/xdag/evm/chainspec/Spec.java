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

public interface Spec {

    /**
     * The default specification.
     */
    Spec DEFAULT = new ByzantiumSpec();

    /**
     * Returns the fee schedule.
     *
     * @return
     */
    FeeSchedule getFeeSchedule();

    /**
     * Returns the precompiled contracts
     *
     * @return
     */
    PrecompiledContracts getPrecompiledContracts();

    /**
     * Returns the gas limit for an internal CALL.
     *
     * @param op
     *            the call opcode, e.g. CALL, CALLCODE and DELEGATECALL
     * @param requestedGas
     *            the requested gas
     * @param availableGas
     *            the available gas
     * @return
     * @throws OutOfGasException
     */
    long getCallGas(OpCode op, long requestedGas, long availableGas) throws OutOfGasException;

    /**
     * Returns the gas limit for an internal CREATE.
     *
     * @param availableGas
     *            the available gas
     * @return
     */
    long getCreateGas(long availableGas);

    /**
     * Returns the basic transaction cost.
     *
     * @param tx
     *            a transaction
     * @return
     */
    long getTransactionCost(Transaction tx);

    /**
     * Returns the max size of a contract.
     *
     * @return
     */
    int maxContractSize();

    /**
     * Whether to create an empty contract or not when running out of gas.
     *
     * @return
     */
    boolean createEmptyContractOnOOG();

    /**
     * EIP1052: https://eips.ethereum.org/EIPS/eip-1052 EXTCODEHASH opcode
     */
    boolean eip1052();

    /**
     * EIP145: https://eips.ethereum.org/EIPS/eip-145 Bitwise shifting instructions
     * in EVM
     */
    boolean eip145();

    /**
     * EIP 1283: https://eips.ethereum.org/EIPS/eip-1283 Net gas metering for SSTORE
     * without dirty maps
     */
    boolean eip1283();

    /**
     * EIP 1014: https://eips.ethereum.org/EIPS/eip-1014 Skinny CREATE2: same as
     * CREATE but with deterministic address
     */
    boolean eip1014();
}

