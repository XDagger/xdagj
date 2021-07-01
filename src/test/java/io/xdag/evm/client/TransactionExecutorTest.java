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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.xdag.evm.DataWord;
import io.xdag.evm.FeeSchedule;
import io.xdag.evm.OpCode;
import io.xdag.evm.TestTransactionBase;
import io.xdag.evm.chainspec.Spec;
import io.xdag.evm.program.InternalTransaction;
import io.xdag.evm.BytecodeCompiler;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TransactionExecutorTest extends TestTransactionBase {

    @Test
    public void testBasicTx() {
        // transfer 1 ETH
        transaction = spy(transaction);
        when(transaction.getValue()).thenReturn(Unit.ETH);
        when(transaction.getGas()).thenReturn(21_000L + 1000L);

        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        System.out.println(receipt);

        assertTrue(receipt.isSuccess());
        assertEquals(21_000L, receipt.getGasUsed());
        assertEquals(Bytes.EMPTY, receipt.getReturnData());
        assertTrue(receipt.getLogs().isEmpty());

        BigInteger balance1 = repository.getBalance(caller);
        BigInteger balance2 = repository.getBalance(address);
        assertEquals(premine.subtract(Unit.ETH).subtract(BigInteger.valueOf(21_000L)), balance1);
        assertEquals(Unit.ETH, balance2);
    }

    @Test
    public void testRecursiveCall() {
        // contract Test {
        // function f(uint n) {
        // if (n > 0) {
        // this.call(bytes4(sha3("f(uint256)")), n - 1);
        // }
        // }
        // }
        Bytes code = Bytes.fromHexString(
                "608060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063b3de648b14610046575b600080fd5b34801561005257600080fd5b5061007160048036038101908080359060200190929190505050610073565b005b6000811115610139573073ffffffffffffffffffffffffffffffffffffffff1660405180807f662875696e743235362900000000000000000000000000000000000000000000815250600a01905060405180910390207c01000000000000000000000000000000000000000000000000000000009004600183036040518263ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401808281526020019150506000604051808303816000875af192505050505b5056");
        repository.saveCode(address, code);

        Bytes method = Bytes.wrap(Hash.keccak256("f(uint256)".getBytes(StandardCharsets.UTF_8)));
        Bytes data = Bytes.concatenate(method.slice(0, 4).copy(), DataWord.of(1000).getData());
        Transaction tx = spy(transaction);
        when(tx.getData()).thenReturn(data);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        assertTrue(receipt.getInternalTransactions().size() > 1);
        assertTrue(receipt.getInternalTransactions().size() < 1000); // due the 63/64 gas rule
        assertEquals(0, receipt.getReturnData().size());
        assertTrue(receipt.getGasUsed() < gas);

        List<InternalTransaction> txs = receipt.getInternalTransactions();
        for (int i = 0; i < txs.size(); i++) {
            InternalTransaction itx = txs.get(i);
            assertEquals(i, itx.getDepth());
            assertEquals(0, itx.getIndex());
            assertEquals(OpCode.CALL.name(), itx.getType());
            assertEquals(i >= txs.size() - 2, itx.isRejected());
        }
    }

    @Test
    public void testCallWithMaxGas() {
        String asm = "PUSH1 0x88" // out size
                + " PUSH1 0x00" // out offset
                + " PUSH1 0x00" // in size
                + " PUSH1 0x00" // in offset
                + " PUSH1 0x01" // value
                + " PUSH20 0x" + address(128).toUnprefixedHexString() // address
                + " PUSH32 0x" + DataWord.of(DataWord.MAX_VALUE) // gas
                + " CALL";
        Bytes code = Bytes.wrap(BytecodeCompiler.compile(asm));
        repository.saveCode(address, code);
        repository.addBalance(address, BigInteger.ONE.multiply(Unit.ETH));

        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        assertEquals(1, receipt.getInternalTransactions().size());

        Spec spec = Spec.DEFAULT;
        FeeSchedule fs = spec.getFeeSchedule();
        int memWords = (0x88 + 31) / 32;
        long availableGas = gas - spec.getTransactionCost(transaction) // basic cost
                - OpCode.Tier.VeryLowTier.asInt() * 7 // 7 push ops
                - fs.getCALL() // call
                - fs.getVT_CALL() // extra: value transfer
                - (fs.getMEMORY() * memWords + memWords * memWords / 512) // memory expansion
                ;
        assertEquals(availableGas - availableGas / 64 + fs.getSTIPEND_CALL(),
                receipt.getInternalTransactions().get(0).getGas());
    }

    @Test
    public void testDeploy() {
        // contract Test {
        // uint public n;
        //
        // constructor(uint _n) {
        // n = _n;
        // }
        // }
        String code = "608060405234801561001057600080fd5b506040516020806100e7833981018060405281019080805190602001909291905050508060008190555050609e806100496000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632e52d606146044575b600080fd5b348015604f57600080fd5b506056606c565b6040518082815260200191505060405180910390f35b600054815600a165627a7a72305820efb6a6369e3c5d7fe9b3274b20753bb0fe188b763fc2adee86cd844de935c8220029";
        Bytes contractAddress = deploy(code, DataWord.ONE.getData());

        Bytes method = Bytes.wrap(Hash.keccak256("n()".getBytes(StandardCharsets.UTF_8)));

//        Bytes data = Arrays.copyOf(method, 4);
        Bytes data = method.slice(0, 4).copy();

        Transaction tx = spy(transaction);
        when(tx.getTo()).thenReturn(contractAddress);
        when(tx.getData()).thenReturn(data);
        when(tx.getNonce()).thenReturn(1L);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        assertTrue(receipt.isSuccess());
        assertEquals(DataWord.ONE, DataWord.of(receipt.getReturnData()));
    }

    @Test
    public void testMultipleCreates() {
        // contract Test {
        // function f(uint n) {
        // for (uint i = 0; i < n; i++) {
        // new Resource(i);
        // }
        // }
        // }
        //
        // contract Resource {
        // uint a;
        //
        // constructor(uint _a) {
        // a = _a;
        // }
        // }
        String code = "608060405234801561001057600080fd5b50610179806100206000396000f300608060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063b3de648b14610046575b600080fd5b34801561005257600080fd5b5061007160048036038101908080359060200190929190505050610073565b005b60008090505b818110156100bf578061008a6100c3565b80828152602001915050604051809103906000f0801580156100b0573d6000803e3d6000fd5b50508080600101915050610079565b5050565b604051607b806100d38339019056006080604052348015600f57600080fd5b50604051602080607b83398101806040528101908080519060200190929190505050806000819055505060358060466000396000f3006080604052600080fd00a165627a7a723058200980eb06443a6d542e49e879013722262ef2955dfa50c09a8f8608d37354758b0029a165627a7a723058201fc71e1d1c7eef260f90edc83a2a7cc72e1c11992ba2448ffb1073ea8b8559b50029";
        Bytes contractAddress = deploy(code);

        Bytes method = Bytes.wrap(Hash.keccak256("f(uint256)".getBytes(StandardCharsets.UTF_8)));
//        Bytes data = BytesUtils.merge(Arrays.copyOf(method, 4), DataWord.of(8).getData());
        Bytes data = Bytes.concatenate(method.slice(0, 4).copy(), DataWord.of(8).getData());

        Transaction tx = spy(transaction);
        when(tx.getTo()).thenReturn(contractAddress);
        when(tx.getData()).thenReturn(data);
        when(tx.getNonce()).thenReturn(1L);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        for (int i = 0; i < 8; i++) {
            assertFalse(receipt.getInternalTransactions().get(i).isRejected());
            assertEquals(i, receipt.getInternalTransactions().get(i).getIndex());
        }
    }

    @Test
    public void testSuicide() {
        String asm = "CALLER" + " SUICIDE";
        Bytes code = Bytes.wrap(BytecodeCompiler.compile(asm));
        repository.saveCode(address, code);
        repository.addBalance(address, Unit.ETH);

        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        System.out.println(receipt);

        assertTrue(receipt.isSuccess());
        assertEquals(1, receipt.getInternalTransactions().size());
        assertEquals(address, receipt.getDeletedAccounts().get(0));

        FeeSchedule fs = Spec.DEFAULT.getFeeSchedule();
        long gasUsed = fs.getTRANSACTION() + OpCode.CALLER.getTier().asInt() + fs.getSUICIDE();
        long gasRefund = Math.min(fs.getSUICIDE_REFUND(), gasUsed / 2);
        BigInteger balance1 = repository.getBalance(caller);
        BigInteger balance2 = repository.getBalance(address);
        assertEquals(premine.add(Unit.ETH).subtract(BigInteger.valueOf(gasUsed - gasRefund).multiply(gasPrice)),
                balance1);
        assertEquals(BigInteger.ZERO, balance2);
    }

    // contract Test {
    // function f() returns (bool success) {
    // return true;
    // }
    // }
    @Test
    public void testSolidityReturn() {
        // prepare the code
        String deploymentCode = "6080604052348015600f57600080fd5b5060a58061001e6000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806326121ff0146044575b600080fd5b348015604f57600080fd5b5060566070565b604051808215151515815260200191505060405180910390f35b600060019050905600a165627a7a723058202a9a4eef7c8bd2ed11241a2692b3d9093e2cfe4c3e6cefe4ef91c18e141efc980029";
        String contractCode = deploymentCode.substring(deploymentCode.indexOf("60806040", 1));
        repository.saveCode(address, Bytes.fromHexString(contractCode));

        // call the `f()` method
//        byte[] methodSignature = Arrays.copyOf(Hash.keccak256("f()".getBytes(StandardCharsets.UTF_8)), 4);
        Bytes methodSignature = Bytes.wrap(Hash.keccak256("f()".getBytes(StandardCharsets.UTF_8))).slice(0, 4).copy();
        Transaction tx = spy(transaction);
        when(tx.getData()).thenReturn(methodSignature);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        assertEquals(DataWord.ONE, DataWord.of(receipt.getReturnData()));
    }
}

