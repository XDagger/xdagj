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
import io.xdag.evm.program.exception.OutOfGasException;

public class ByzantiumSpec extends BaseSpec {

    private static class FeeScheduleByzantium extends FeeSchedule {
        public int getBALANCE() {
            return 400;
        }

        public int getEXT_CODE_SIZE() {
            return 700;
        }

        public int getEXT_CODE_COPY() {
            return 700;
        }

        public int getSLOAD() {
            return 200;
        }

        public int getCALL() {
            return 700;
        }

        public int getSUICIDE() {
            return 5000;
        }

        public int getNEW_ACCT_SUICIDE() {
            return 25000;
        }

        public int getEXP_BYTE_GAS() {
            return 50;
        }
    }

    private static final FeeSchedule feeSchedule = new FeeScheduleByzantium();
    private static final PrecompiledContracts precompiledContracts = new ByzantiumPrecompiledContracts();

    public ByzantiumSpec() {
    }

    @Override
    public FeeSchedule getFeeSchedule() {
        return feeSchedule;
    }

    @Override
    public PrecompiledContracts getPrecompiledContracts() {
        return precompiledContracts;
    }

    private static long maxAllowed(long available) {
        return available - available / 64;
    }

    @Override
    public long getCallGas(OpCode op, long requestedGas, long availableGas) throws OutOfGasException {
        long maxAllowed = maxAllowed(availableGas);
        return requestedGas > maxAllowed ? maxAllowed : requestedGas;
    }

    @Override
    public long getCreateGas(long availableGas) {
        return maxAllowed(availableGas);
    }

    @Override
    public int maxContractSize() {
        return 0x6000;
    }
}

