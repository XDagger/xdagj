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
package io.xdag.evm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.xdag.evm.program.Program;
import io.xdag.evm.program.invoke.ProgramInvoke;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

import java.math.BigInteger;

public class VMComplexTest extends TestBase {

    @Test // contract call recursive
    public void test1() {

        // a = contract.storage[999]
        // if (a > 0) {
        // contract.storage[999] = a - 1
        // send((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0)
        // } else {
        // stop
        // }

        DataWord key1 = DataWord.of(999);
        DataWord value1 = DataWord.of(3);

        // Set contract into Database
        String address = "77045e71a7a2c50903d88e564cd72fab11e82051";
        String code = "PUSH2 0x03e7 SLOAD PUSH1 0x00 MSTORE PUSH1 0x00 PUSH1 0x00 MLOAD GT ISZERO PUSH4 0x0000004c JUMPI PUSH1 0x01 PUSH1 0x00 MLOAD SUB PUSH2 0x03e7 SSTORE PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH20 0x"
                + address + " PUSH1 0x08 PUSH1 0x0a GAS DIV MUL CALL PUSH4 0x0000004c STOP JUMP JUMPDEST STOP";

        Bytes addressB = Bytes.fromHexString(address);
        Bytes codeB = Bytes.wrap(BytecodeCompiler.compile(code));

        ProgramInvoke pi = spy(invoke);
        when(pi.getOwnerAddress()).thenReturn(DataWord.of(addressB));

        repository.createAccount(addressB);
        repository.saveCode(addressB, codeB);
        repository.putStorageRow(addressB, key1, value1);

        // Play the program
        EVM vm = new EVM();
        Program program = new Program(codeB, pi);

        try {
            while (!program.isStopped()) {
                vm.step(program);
            }
        } catch (RuntimeException e) {
            program.setException(e);
        }

        long gasUsed = program.getResult().getGasUsed();
        Exception exception = program.getResult().getException();
        BigInteger balance = repository.getBalance(addressB);

        System.out.println();
        System.out.println("============ Results ============");
        System.out.println("*** Used gas: " + gasUsed);
        System.out.println("*** Exception: " + exception);
        System.out.println("*** Contract Balance: " + balance);

        assertEquals(18223, program.getResult().getGasUsed());
        assertNull(exception);
    }
}

