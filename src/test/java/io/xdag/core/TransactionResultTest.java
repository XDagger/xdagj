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
package io.xdag.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.xdag.core.TransactionResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionResultTest {

    @Test
    public void testCode() {
        for (TransactionResult.Code code : TransactionResult.Code.values()) {
            if (code.name().startsWith("SUCCESS")) {
                assertTrue(code.isSuccess());
                assertTrue(code.isAcceptable());
                assertFalse(code.isRejected());
            } else if (code.name().startsWith("FAILURE")) {
                assertFalse(code.isSuccess());
                assertTrue(code.isAcceptable());
                assertFalse(code.isRejected());
            } else {
                assertFalse(code.isSuccess());
                assertFalse(code.isAcceptable());
                assertTrue(code.isRejected());
            }
        }
    }
    @Test
    public void testTransactionResultSize() {
        TransactionResult res = new TransactionResult(TransactionResult.Code.SUCCESS);
        byte[] bytes = res.toBytes();

        log.info("result size: {} B, {} GB per 1M txs", bytes.length, 1000000.0 * bytes.length / 1024 / 1024 / 1024);
    }

    @Test
    public void testSerializationFull() {
        TransactionResult.Code code = TransactionResult.Code.INVALID;

        TransactionResult tr1 = new TransactionResult(code);

        TransactionResult tr2 = TransactionResult.fromBytes(tr1.toBytes());
        assertEquals(code, tr2.getCode());
    }
}
