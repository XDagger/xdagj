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
package io.xdag.net.message.consensus;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import io.xdag.Network;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.crypto.SampleKeys;
import io.xdag.net.message.p2p.TransactionMessage;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class TransactionMessageTest {
    @Test
    public void testSerialization() {
        Network network = Network.DEVNET;
        TransactionType type = TransactionType.TRANSFER;
        byte[] to = BytesUtils.random(20);
        XAmount value = XAmount.of(2);
        XAmount fee = XAmount.of(50_000_000L);
        long nonce = 1;
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] data = BytesUtils.of("data");

        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, data);
        tx.sign(SampleKeys.KEY_PAIR);

        TransactionMessage msg = new TransactionMessage(tx);
        TransactionMessage msg2 = new TransactionMessage(msg.getBody());
        assertArrayEquals(msg2.getTransaction().getHash(), tx.getHash());
    }
}
