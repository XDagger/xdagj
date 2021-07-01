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

import io.xdag.utils.HashUtils;
import io.xdag.evm.DataWord;
import io.xdag.evm.TestTransactionBase;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PrecompiledContractCallTest extends TestTransactionBase {

    private final Bytes owner = Bytes.fromHexString("23a6049381fd2cfb0661d9de206613b83d53d7df");
    private final long gas = 10_000_000L;

    @Test
    public void testECRecover() {
        // contract Test {
        // function verify(bytes32 hash, uint8 v, bytes32 r, bytes32 s) constant
        // returns(address) {
        // bytes memory prefix = "\x19Ethereum Signed Message:\n32";
        // bytes32 prefixedHash = keccak256(prefix, hash);
        // return ecrecover(prefixedHash, v, r, s);
        // }
        // }
        String code = "608060405234801561001057600080fd5b5061024c806100206000396000f300608060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063f1835db714610046575b600080fd5b34801561005257600080fd5b5061009e6004803603810190808035600019169060200190929190803560ff169060200190929190803560001916906020019092919080356000191690602001909291905050506100e0565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000606060006040805190810160405280601c81526020017f19457468657265756d205369676e6564204d6573736167653a0a333200000000815250915081876040518083805190602001908083835b6020831015156101555780518252602082019150602081019050602083039250610130565b6001836020036101000a03801982511681845116808217855250505050505090500182600019166000191681526020019250505060405180910390209050600181878787604051600081526020016040526040518085600019166000191681526020018460ff1660ff1681526020018360001916600019168152602001826000191660001916815260200194505050505060206040516020810390808403906000865af115801561020a573d6000803e3d6000fd5b50505060206040510351925050509493505050505600a165627a7a72305820c28038a95a2d8c5fee2fb4c1ba7b20c6ee5405e3528f5d6883bae1108a17987a0029";
        Bytes contractAddress = deploy(code, DataWord.ONE.getData());

        Bytes method = Bytes.wrap(Arrays.copyOf(Hash.keccak256("verify(bytes32,uint8,bytes32,bytes32)".getBytes(StandardCharsets.UTF_8)), 4));
        Bytes hash = Bytes.wrap(Hash.keccak256("hello".getBytes(StandardCharsets.UTF_8)));
        System.out.println(hash.toHexString());
        Bytes v = DataWord.of(28).getData();
        Bytes r = Bytes.fromHexString("9242685bf161793cc25603c231bc2f568eb630ea16aa137d2664ac8038825608");
        Bytes s = Bytes.fromHexString("4f8ae3bd7535248d0bd448298cc2e2071e56992d0774dc340c368ae950852ada");
        Bytes data = Bytes.concatenate(method, hash, v, r, s);

        Transaction tx = spy(transaction);
        when(tx.getTo()).thenReturn(contractAddress);
        when(tx.getData()).thenReturn(data);
        when(tx.getNonce()).thenReturn(1L);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repository, blockStore);
        TransactionReceipt receipt = executor.run();

        assertTrue(receipt.isSuccess());
        assertEquals(DataWord.of("7156526fbd7a3c72969b54f64e42c10fbb768c8a"), DataWord.of(receipt.getReturnData()));
    }

    @Test
    public void testZK() throws IOException {
        repository.addBalance(owner, premine);
        createContract("solidity/verifier.con", owner, 0, gas);
    }
}

