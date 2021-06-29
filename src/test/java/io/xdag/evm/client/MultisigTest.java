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
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class MultisigTest extends TestTransactionBase {

    private final long gas = 5_000_000L;
    private final BigInteger gasPrice = BigInteger.TEN;

    @Test
    public void testMultisig() throws IOException {
        Bytes user1 = address(1);
        Bytes user2 = address(2);
        Bytes user3 = address(3);
        Bytes user4 = address(4);
        repository.addBalance(user1, premine);
        repository.addBalance(user2, premine);
        repository.addBalance(user3, premine);

        long nonceUser1 = 0;
        long nonceUser2 = 0;
        long nonceUser3 = 0;

        Bytes code = readContract("solidity/multisig.con");
        Bytes args = Bytes.concatenate(
                DataWord.of(64).getData(),
                DataWord.of(2).getData(),
                DataWord.of(3).getData(),
                DataWord.of(user1).getData(),
                DataWord.of(user2).getData(),
                DataWord.of(user3).getData());
        Bytes contractAddress = createContract(code, args, user1, nonceUser1++, gas);

        Bytes sendRecipient = user4;
        BigInteger sendAmount = BigInteger.TEN.pow(18); // 1 SEM
        repository.addBalance(contractAddress, sendAmount); // transfer to the contract

        // submit this transaction
        Bytes txid = submitTransaction(contractAddress, sendRecipient, sendAmount, user1, nonceUser1++);

        // let user1 confirm (confirmed by user1)
        assertTrue(confirmTransaction(contractAddress, txid, user1, nonceUser1++));

        // try to commit
        executeTransaction(contractAddress, txid, user1, nonceUser1++);
        assertFalse(isConfirmed(contractAddress, txid, user1, nonceUser1++));
        assertEquals(BigInteger.ZERO, repository.getBalance(sendRecipient));

        // let user2 confirm (confirmed by user2)
        assertTrue(confirmTransaction(contractAddress, txid, user2, nonceUser2++));

        // try to commit, again
        executeTransaction(contractAddress, txid, user1, nonceUser1++);
        assertTrue(isConfirmed(contractAddress, txid, user1, nonceUser1++));
        assertEquals(sendAmount, repository.getBalance(sendRecipient));
    }

    private boolean isConfirmed(Bytes contractAddress, Bytes txid, Bytes user, long nonce) {
        Bytes method = Bytes.wrap(Hash.keccak256("isConfirmed(uint256)".getBytes(StandardCharsets.UTF_8)));
//        Bytes methodData = BytesUtils.merge(Arrays.copyOf(method, 4),
//                DataWord.of(txid).getData());
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(txid).getData());

        Transaction transaction = new TransactionMock(false, user, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        assertTrue(receipt.isSuccess());

        return !DataWord.of(receipt.getReturnData()).isZero();
    }

    private boolean confirmTransaction(Bytes contractAddress, Bytes txid, Bytes user, long nonce) {
        Bytes method = Bytes.wrap(Hash.keccak256("confirmTransaction(uint256)".getBytes(StandardCharsets.UTF_8)));
//        Bytes methodData = BytesUtils.merge(Arrays.copyOf(method, 4),
//                DataWord.of(txid).getData());
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(txid).getData());

        Transaction transaction = new TransactionMock(false, user, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        return receipt.isSuccess();
    }

    private boolean executeTransaction(Bytes contractAddress, Bytes txid, Bytes user, long nonce) {

        Bytes method = Bytes.wrap(Hash.keccak256("executeTransaction(uint256)".getBytes(StandardCharsets.UTF_8)));
//        Bytes methodData = BytesUtils.merge(Arrays.copyOf(method, 4),
//                DataWord.of(txid).getData());
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(txid).getData());

        Transaction transaction = new TransactionMock(false, user, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        return receipt.isSuccess();
    }

    private Bytes submitTransaction(Bytes contractAddress, Bytes to, BigInteger amount, Bytes user, long nonce) {
        Bytes method = Bytes.wrap(Hash.keccak256("submitTransaction(address,uint256)".getBytes(StandardCharsets.UTF_8)));
//        byte[] methodData = BytesUtils.merge(Arrays.copyOf(method, 4),
//                DataWord.of(to).getData(), DataWord.of(amount).getData());
        Bytes methodData = Bytes.concatenate(method.slice(0,4).copy(),
                DataWord.of(to).getData(), DataWord.of(amount).getData());

        Transaction transaction = new TransactionMock(false, user, contractAddress, nonce, value, methodData, gas,
                gasPrice);
        TransactionExecutor executor = new TransactionExecutor(transaction, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();
        assertTrue(receipt.isSuccess());
        assertNotNull(receipt.getReturnData());
        return receipt.getReturnData();
    }
}

