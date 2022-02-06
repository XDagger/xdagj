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

import io.xdag.evm.client.BlockStore;
import io.xdag.evm.client.BlockStoreMock;
import io.xdag.evm.client.Repository;
import io.xdag.evm.client.RepositoryMock;
import io.xdag.evm.program.Program;
import io.xdag.evm.program.invoke.ProgramInvokeImpl;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;

import java.math.BigInteger;
import java.security.Security;

public class TestBase {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    protected final Bytes address = address(1);
    protected final Bytes origin = address(2);
    protected final Bytes caller = address(2);
    protected final long gas = 1_000_000L;
    protected final BigInteger gasPrice = BigInteger.ONE;
    protected final BigInteger value = BigInteger.ZERO;
    protected final Bytes data = Bytes.EMPTY;

//    protected final byte[] prevHash = new byte[32];
    protected final Bytes32 prevHash = MutableBytes32.create();
    protected final Bytes coinbase = address(3);
    protected final long timestamp = System.currentTimeMillis();
    protected final long number = 1;
    protected final BigInteger difficulty = BigInteger.TEN;
    protected final long gasLimit = 10_000_000L;

    protected final int callDepth = 0;
    protected final boolean isStaticCall = false;

    protected Repository repository;
    protected BlockStore blockStore;

    protected Repository track;
    protected Repository originalTrack;

    protected ProgramInvokeImpl invoke;
    protected Program program;

    @Before
    public void setup() {
        this.repository = new RepositoryMock();
        this.blockStore = new BlockStoreMock();

        this.track = repository.startTracking();
        this.originalTrack = track.clone();

        this.invoke = new ProgramInvokeImpl(
                DataWord.of(address),
                DataWord.of(origin),
                DataWord.of(caller),
                gas,
                DataWord.of(gasPrice),
                DataWord.of(value),
                data,
                DataWord.of(prevHash),
                DataWord.of(coinbase),
                DataWord.of(timestamp),
                DataWord.of(number),
                DataWord.of(difficulty),
                DataWord.of(gasLimit),
                track,
                originalTrack,
                blockStore,
                callDepth,
                isStaticCall);
    }

    @After
    public void tearDown() {
    }

//    public byte[] address(int n) {
//        byte[] a = new byte[20];
//        Arrays.fill(a, (byte) n);
//        return a;
//    }
    public Bytes address(int n) {
        MutableBytes a = MutableBytes.create(20);
        a.fill((byte) n);
        return a;
    }
}

