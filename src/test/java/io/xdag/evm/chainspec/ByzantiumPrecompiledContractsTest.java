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

import io.xdag.crypto.Sign;
import io.xdag.evm.DataWord;
import io.xdag.evm.client.Repository;
import io.xdag.evm.program.InternalTransaction;
import io.xdag.evm.program.ProgramResult;
import io.xdag.utils.HashUtils;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.junit.Test;

import java.math.BigInteger;

import static io.xdag.utils.BytesUtils.bytesToBigInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ByzantiumPrecompiledContractsTest {

    ByzantiumPrecompiledContracts precompiledContracts = new ByzantiumPrecompiledContracts();

    private PrecompiledContractContext wrapData(Bytes data) {
        return new PrecompiledContractContext() {

            private final Repository repo = mock(Repository.class);
            private final ProgramResult result = mock(ProgramResult.class);
            private final InternalTransaction tx = mock(InternalTransaction.class);
            {
                when(tx.getData()).thenReturn(data);
            }

            @Override
            public Repository getTrack() {
                return repo;
            }

            @Override
            public ProgramResult getResult() {
                return result;
            }

            @Override
            public InternalTransaction getInternalTransaction() {
                return tx;
            }
        };
    }

    @Test
    public void identityTest1() {
        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000004");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        Bytes data = Bytes.fromHexString("112233445566");
        Bytes expected = Bytes.fromHexString("112233445566");

        Bytes result = contract.execute(wrapData(data)).getRight();

        assertEquals(expected, result);
    }

    @Test
    public void sha256Test1() {
        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        Bytes data = null;
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        Bytes result = contract.execute(wrapData(data)).getRight();

        assertEquals(expected, result.toUnprefixedHexString());
    }

    @Test
    public void sha256Test2() {

        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        Bytes data = Bytes.EMPTY;
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        Bytes result = contract.execute(wrapData(data)).getRight();

        assertEquals(expected, result.toUnprefixedHexString());
    }

    @Test
    public void sha256Test3() {

        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        Bytes data = Bytes.fromHexString("112233");
        String expected = "49ee2bf93aac3b1fb4117e59095e07abe555c3383b38d608da37680a406096e8";

        Bytes result = contract.execute(wrapData(data)).getRight();

        assertEquals(expected, result.toUnprefixedHexString());
    }

    @Test
    public void Ripempd160Test1() {
        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000003");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        Bytes data = Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
        String expected = "000000000000000000000000ae387fcfeb723c3f5964509af111cf5a67f30661";

        Bytes result = contract.execute(wrapData(data)).getRight();

        assertEquals(expected, result.toUnprefixedHexString());
    }

    @Test
    public void ecRecoverTest1() {
        SECP256K1 secp256K1 = new SECP256K1();
        Bytes32 messageHash = Bytes32.fromHexString("14431339128bd25f2c7f93baa611e367472048757f4ad67f6d71a5ca0da550f5");
        byte v = 0;
        Bytes r = Bytes.fromHexString("51e4dbbbcebade695a3f0fdf10beb8b5f83fda161e1a3105a14c41168bf3dce0");
        Bytes s = Bytes.fromHexString("46eabf35680328e26ef4579caf8aeb2cf9ece05dbf67a4f3d1f28c7b1d0e3546");
        SECPSignature sig = SECPSignature.create(r.toUnsignedBigInteger(),s.toUnsignedBigInteger(), v, Sign.CURVE.getN());

        SECPPublicKey publicKey = secp256K1.recoverPublicKeyFromSignature(messageHash, sig).get();
        Bytes address = HashUtils.sha3omit12(publicKey.getEncodedBytes());

        String expected = "cc9a4f6461ee2c7794bffa47b0a24b47eb012194";
        assertEquals(expected, address.toUnprefixedHexString());

        Bytes data = Bytes.fromHexString("14431339128bd25f2c7f93baa611e367"
                + "472048757f4ad67f6d71a5ca0da550f5"
                + "00000000000000000000000000000000"
                + "00000000000000000000000000000000"
                + "51e4dbbbcebade695a3f0fdf10beb8b5"
                + "f83fda161e1a3105a14c41168bf3dce0"
                + "46eabf35680328e26ef4579caf8aeb2c"
                + "f9ece05dbf67a4f3d1f28c7b1d0e3546");
        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000001");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        String expected2 = "000000000000000000000000cc9a4f6461ee2c7794bffa47b0a24b47eb012194";

        Bytes result = contract.execute(wrapData(data)).getRight();
        assertEquals(expected2, result.toUnprefixedHexString());
    }

    @Test
    public void modExpTest() {
        DataWord addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000005");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(addr);
        assertNotNull(contract);

        Bytes data1 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f");

        assertEquals(13056, contract.getGasForData(data1));

        Bytes res1 = Bytes.wrap(contract.execute(wrapData(data1)).getRight());
        assertEquals(32, res1.size());
        assertEquals(BigInteger.ONE, bytesToBigInteger(res1.toArray()));

        Bytes data2 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f");

        assertEquals(13056, contract.getGasForData(data2));

        Bytes res2 = contract.execute(wrapData(data2)).getRight();
        assertEquals(32, res2.size());
        assertEquals(BigInteger.ZERO, bytesToBigInteger(res2.toArray()));

        Bytes data3 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        // hardly imagine this value could be a real one
        assertEquals(3_674_950_435_109_146_392L, contract.getGasForData(data3));

        Bytes data4 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "ffff" +
                        "8000000000000000000000000000000000000000000000000000000000000000" +
                        "07"); // "07" should be ignored by data parser

        assertEquals(768, contract.getGasForData(data4));

        Bytes res4 = contract.execute(wrapData(data4)).getRight();
        assertEquals(32, res4.size());
        assertEquals(new BigInteger("26689440342447178617115869845918039756797228267049433585260346420242739014315"),
                bytesToBigInteger(res4.toArray()));

        Bytes data5 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "ffff" +
                        "80"); // "80" should be parsed as
        // "8000000000000000000000000000000000000000000000000000000000000000"
        // cause call data is infinitely right-padded with zero bytes

        assertEquals(768, contract.getGasForData(data5));

        Bytes res5 = contract.execute(wrapData(data5)).getRight();
        assertEquals(32, res5.size());
        assertEquals(new BigInteger("26689440342447178617115869845918039756797228267049433585260346420242739014315"),
                bytesToBigInteger(res5.toArray()));

        // check overflow handling in gas calculation
        Bytes data6 = Bytes.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000020000000000000000000000000000000" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        assertEquals(Long.MAX_VALUE, contract.getGasForData(data6));

        // check rubbish data
        Bytes data7 = Bytes.fromHexString(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        assertEquals(Long.MAX_VALUE, contract.getGasForData(data7));

        // check empty data
        Bytes data8 = Bytes.EMPTY;

        assertEquals(0, contract.getGasForData(data8));

        Bytes res8 = contract.execute(wrapData(data8)).getRight();
        assertEquals(Bytes.EMPTY, res8);

        assertEquals(0, contract.getGasForData(null));
        assertEquals(Bytes.EMPTY, contract.execute(wrapData(null)).getRight());
    }
}

