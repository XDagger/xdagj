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

import io.xdag.utils.HashUtils;
import io.xdag.evm.client.*;
import io.xdag.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TestTransactionBase extends TestBase {

    protected final BigInteger premine = BigInteger.valueOf(100L).multiply(Unit.ETH);

    // by default, it's a CALL transaction with 1 million gas and empty payload
    protected Transaction transaction;
    protected Block block;

    @Before
    public void setup() {
        super.setup();
        transaction = new TransactionMock(false, caller, address, 0, value, data, gas, gasPrice);
        block = new BlockMock(number, prevHash, coinbase, timestamp, gasLimit);
        repository.addBalance(caller, premine);
    }

    protected Bytes deploy(String code) {
        return deploy(code, Bytes.EMPTY);
    }

    protected Bytes deploy(String code, Bytes arguments) {
        Bytes contractAddress = Bytes.wrap(HashUtils.calcNewAddress(caller.toArray(), repository.getNonce(caller)));

        Transaction tx = spy(transaction);
        when(tx.isCreate()).thenReturn(true);
        when(tx.getTo()).thenReturn(Bytes.EMPTY);
        when(tx.getData()).thenReturn(Bytes.concatenate(Bytes.fromHexString(code), arguments));

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        assertNotNull(repository.getCode(contractAddress));
        assertEquals(receipt.getReturnData(), repository.getCode(contractAddress));

        return contractAddress;
    }

    protected Bytes readContract(String fileName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String path = classLoader.getResource(fileName).getPath();
        List<String> lines = Files.readAllLines(Paths.get(path), Charset.forName("UTF8"));
        return Bytes.fromHexString(lines.get(0));
    }

    protected Bytes createContract(Bytes code, Bytes args, Bytes address, long nonce, long gas) throws IOException {
        Bytes data = Bytes.wrap(BytesUtils.merge(code.toArray(), args.toArray()));
        Bytes contractAddress = Bytes.wrap(HashUtils.calcNewAddress(address.toArray(), nonce));

        Transaction transaction = new TransactionMock(true, address, address, nonce, value, data, gas, gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        assertTrue(receipt.isSuccess());

        return contractAddress;
    }

    protected Bytes createContract(String contractLocation, Bytes address, long nonce, long gas) throws IOException {
        return createContract(readContract(contractLocation), Bytes.EMPTY, address, nonce, gas);
    }
}
