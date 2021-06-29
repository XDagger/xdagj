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
package io.xdag.evm.program.exception;

import java.math.BigInteger;

import io.xdag.evm.OpCode;
import io.xdag.evm.program.Program;
import io.xdag.utils.BytesUtils;

public class ExceptionFactory {

    public static OutOfGasException notEnoughOpGas(OpCode op, long opGas, long programGas) {
        return new OutOfGasException("Not enough gas for '%s' operation executing: opGas[%d], programGas[%d];", op,
                opGas, programGas);
    }

    public static OutOfGasException notEnoughSpendingGas(String cause, long gasValue, Program program) {
        return new OutOfGasException("Not enough gas for '%s' cause spending: gas[%d], usedGas[%d];",
                cause, gasValue, program.getResult().getGasUsed());
    }

    public static OutOfGasException gasOverflow(BigInteger actual, BigInteger limit) {
        return new OutOfGasException("Gas value overflow: actual[%d], limit[%d];", actual.longValue(),
                limit.longValue());
    }

    public static IllegalOperationException invalidOpCode(byte opCode) {
        return new IllegalOperationException("Invalid operation code: opCode[%s];",
                BytesUtils.toHexString(new byte[] { opCode }));
    }

    public static BadJumpDestinationException badJumpDestination(int pc) {
        return new BadJumpDestinationException("Operation with pc isn't 'JUMPDEST': PC[%d];", pc);
    }

    public static StackUnderflowException tooSmallStack(int expectedSize, int actualSize) {
        return new StackUnderflowException("Expected stack size %d but actual %d;", expectedSize, actualSize);
    }

    public static StackOverflowException tooLargeStack(int expectedSize, int maxSize) {
        return new StackOverflowException("Expected stack size %d exceeds stack limit %d", expectedSize, maxSize);
    }
}
