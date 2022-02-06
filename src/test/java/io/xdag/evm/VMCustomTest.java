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
import io.xdag.evm.program.exception.IllegalOperationException;
import io.xdag.evm.program.exception.OutOfGasException;
import io.xdag.evm.program.exception.StackUnderflowException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

public class VMCustomTest extends TestBase {

    private final Bytes callData = Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a1" +
            "00000000000000000000000000000000000000000000000000000000000000b1");

    @Before
    public void additionalSetup() {
        invoke = spy(invoke);
        when(invoke.getData()).thenReturn(callData);
        repository.addBalance(address, BigInteger.valueOf(1000L));
    }

    @Test // CALLDATASIZE OP
    public void testCALLDATASIZE_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("36"), invoke);
        String s_expected_1 = DataWord.of(callData.size()).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("600035"), invoke);
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000a1";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_2() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("600235"), invoke);
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000a10000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_3() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("602035"), invoke);
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000b1";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_4() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("602335"), invoke);
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000b1000000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_5() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("603F35"), invoke);
        String s_expected_1 = "b100000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test(expected = RuntimeException.class) // CALLDATALOAD OP mal
    public void testCALLDATALOAD_6() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("35"), invoke);
        try {
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60206000600037"), invoke);
        String m_expected = "00000000000000000000000000000000000000000000000000000000000000a1";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Bytes.wrap(program.getMemory()).toUnprefixedHexString());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_2() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60406000600037"), invoke);
        String m_expected = "00000000000000000000000000000000000000000000000000000000000000a1" +
                "00000000000000000000000000000000000000000000000000000000000000b1";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Bytes.wrap(program.getMemory()).toUnprefixedHexString());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_3() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60406004600037"), invoke);
        String m_expected = "000000000000000000000000000000000000000000000000000000a100000000" +
                "000000000000000000000000000000000000000000000000000000b100000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Bytes.wrap(program.getMemory()).toUnprefixedHexString());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_4() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60406000600437"), invoke);
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000a100000000000000000000000000000000000000000000000000000000" +
                "000000b100000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Bytes.wrap(program.getMemory()).toUnprefixedHexString());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_5() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60406000600437"), invoke);
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000a100000000000000000000000000000000000000000000000000000000" +
                "000000b100000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Bytes.wrap(program.getMemory()).toUnprefixedHexString());
    }

    @Test(expected = StackUnderflowException.class) // CALLDATACOPY OP mal
    public void testCALLDATACOPY_6() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("6040600037"), invoke);

        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test(expected = OutOfGasException.class) // CALLDATACOPY OP mal
    public void testCALLDATACOPY_7() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("6020600073CC0929EB16730E7C14FEFC63006AC2D794C5795637"), invoke);

        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // ADDRESS OP
    public void testADDRESS_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("30"), invoke);
        String s_expected_1 = DataWord.of(address).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // BALANCE OP
    public void testBALANCE_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("3031"), invoke);
        String s_expected_1 = DataWord.of(1000L).toString();

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // ORIGIN OP
    public void testORIGIN_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("32"), invoke);
        String s_expected_1 = DataWord.of(origin).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLER OP
    public void testCALLER_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("33"), invoke);
        String s_expected_1 = DataWord.of(caller).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // CALLVALUE OP
    public void testCALLVALUE_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("34"), invoke);
        String s_expected_1 = DataWord.of(value).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // SHA3 OP
    public void testSHA3_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60016000536001600020"), invoke);
        String s_expected_1 = "5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // SHA3 OP
    public void testSHA3_2() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("6102016000526002601E20"), invoke);
        String s_expected_1 = "114a3fe82a0219fcc31abd15617966a125f12b0fd3409105fc83b487a9d82de4";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test(expected = StackUnderflowException.class) // SHA3 OP mal
    public void testSHA3_3() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("610201600052600220"), invoke);
        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // BLOCKHASH OP
    public void testBLOCKHASH_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("600140"), invoke);
        String s_expected_1 = DataWord.of(blockStore.getBlockHashByNumber(1)).toString();

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // COINBASE OP
    public void testCOINBASE_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("41"), invoke);
        String s_expected_1 = DataWord.of(coinbase).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // TIMESTAMP OP
    public void testTIMESTAMP_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("42"), invoke);
        String s_expected_1 = DataWord.of(timestamp).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // NUMBER OP
    public void testNUMBER_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("43"), invoke);
        String s_expected_1 = DataWord.of(number).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // DIFFICULTY OP
    public void testDIFFICULTY_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("44"), invoke);
        String s_expected_1 = DataWord.of(difficulty).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // GASPRICE OP
    public void testGASPRICE_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("3A"), invoke);
        String s_expected_1 = DataWord.of(gasPrice).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // GAS OP
    public void testGAS_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("5A"), invoke);
        String s_expected_1 = DataWord.of(gas - OpCode.GAS.getTier().asInt()).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test // GASLIMIT OP
    public void testGASLIMIT_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("45"), invoke);
        String s_expected_1 = DataWord.of(gasLimit).toString();

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, item1.toString());
    }

    @Test(expected = IllegalOperationException.class) // INVALID OP
    public void testINVALID_1() {
        EVM vm = new EVM();
        program = new Program(Bytes.fromHexString("60012F6002"), invoke);
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        try {
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
            DataWord item1 = program.stackPop();
            assertEquals(s_expected_1, item1.toString());
        }
    }
}
