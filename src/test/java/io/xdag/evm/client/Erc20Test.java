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

import io.xdag.crypto.Hash;
import io.xdag.evm.DataWord;
import io.xdag.evm.TestTransactionBase;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class Erc20Test extends TestTransactionBase {

    private final Bytes erc20owner = Bytes.fromHexString("23a6049381fd2cfb0661d9de206613b83d53d7df");
    private final long gas = 2_000_000L;

    @Before
    public void setup() {
        super.setup();
        repository.addBalance(erc20owner, premine);
    }

    @Test
    public void testErc20Token() throws IOException {
        Bytes contractAddress = createContract("solidity/erc20.con", erc20owner, 0L, gas);

        // check balance
        Bytes method = Bytes.wrap(Hash.keccak256("balanceOf(address)".getBytes(StandardCharsets.UTF_8)));
//        Bytes methodData = BytesUtils.merge(Arrays.copyOf(method, 4), DataWord.of(erc20owner).getData());
        Bytes methodData = Bytes.concatenate(method.slice(0, 4), DataWord.of(erc20owner).getData());

        transaction = new TransactionMock(false, erc20owner, contractAddress, 1L, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        assertTrue(receipt.isSuccess());
        BigInteger balance = DataWord.of(receipt.getReturnData()).value();
        assertEquals(new BigInteger("100000000000000000000000000"), balance);

        // transfer
        method = Bytes.wrap(Hash.keccak256("transfer(address,uint256)".getBytes(StandardCharsets.UTF_8)));
        methodData = Bytes.concatenate(method.slice(0, 4), DataWord.of(address).getData(), DataWord.of(BigInteger.TEN).getData());
        transaction = new TransactionMock(false, erc20owner, contractAddress, 2L, value, methodData, gas,
                gasPrice);
        executor = new TransactionExecutor(transaction, block, repository, blockStore);
        receipt = executor.run();
        assertTrue(receipt.isSuccess());

        // check balance of target
        method = Bytes.wrap(Hash.keccak256("balanceOf(address)".getBytes(StandardCharsets.UTF_8)));

//        methodData = BytesUtils.merge(Arrays.copyOf(method, 4), DataWord.of(address).getData());
        methodData = Bytes.concatenate(method.slice(0, 4), DataWord.of(address).getData());

        transaction = new TransactionMock(false, erc20owner, contractAddress, 3L, value, methodData, gas,
                gasPrice);
        executor = new TransactionExecutor(transaction, block, repository, blockStore);
        receipt = executor.run();
        assertTrue(receipt.isSuccess());
        balance = DataWord.of(receipt.getReturnData()).value();
        assertEquals(new BigInteger("10"), balance);
    }
}

