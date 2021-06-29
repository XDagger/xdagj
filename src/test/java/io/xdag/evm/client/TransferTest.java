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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import io.xdag.crypto.Hash;
import io.xdag.evm.DataWord;
import io.xdag.evm.TestTransactionBase;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class TransferTest extends TestTransactionBase {

    @Test
    public void testTransfer() throws IOException {
        long nonce = 0;

        repository.addBalance(address, premine);
        Bytes contractAddress = createContract("solidity/transfer.con", address, nonce++, gas);
        repository.addBalance(contractAddress, premine);

        // call method to transfer to 'caller'
        BigInteger balanceBefore = repository.getBalance(caller);

        BigInteger toSend = premine.divide(BigInteger.TEN);
        Bytes method = Bytes.wrap(Hash.keccak256("transfer(address,uint256)".getBytes(StandardCharsets.UTF_8)));
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(caller).getData(), DataWord.of(toSend).getData());

        Transaction transaction = new TransactionMock(false, address, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        BigInteger balanceAfter = repository.getBalance(caller);

        assertEquals(balanceBefore.add(toSend), balanceAfter);
    }

    @Test
    public void testSend() throws IOException {
        long nonce = 0;

        repository.addBalance(address, premine);
        Bytes contractAddress = createContract("solidity/transfer.con", address, nonce, gas);
        nonce++;
        repository.addBalance(contractAddress, premine);

        // call method to transfer to 'caller'
        BigInteger balanceBefore = repository.getBalance(caller);

        BigInteger toSend = premine.divide(BigInteger.TEN);
        Bytes method = Bytes.wrap(Hash.keccak256("send(address,uint256)".getBytes(StandardCharsets.UTF_8)));
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(caller).getData(), DataWord.of(toSend).getData());

        Transaction transaction = new TransactionMock(false, address, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        BigInteger balanceAfter = repository.getBalance(caller);

        assertEquals(balanceBefore.add(toSend), balanceAfter);
    }

    @Test
    public void testTransfer2() throws IOException {
        long nonce = 0;

        repository.addBalance(address, premine);
        Bytes contractAddress = createContract("solidity/transfer.con", address, nonce, gas);
        nonce++;
        repository.addBalance(contractAddress, premine);

        // call method to transfer to 'caller'
        BigInteger balanceBefore = repository.getBalance(caller);

        BigInteger toSend = premine.divide(BigInteger.TEN);
        Bytes method = Bytes.wrap(Hash.keccak256("transfer(address,uint256)".getBytes(StandardCharsets.UTF_8)));
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(caller).getData(), DataWord.of(toSend).getData());

        Transaction transaction = new TransactionMock(false, address, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        BigInteger balanceAfter = repository.getBalance(caller);

        assertEquals(balanceBefore.add(toSend), balanceAfter);
    }
}

