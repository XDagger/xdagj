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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Test;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionTest {

    private final Config config = new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR);
    private final KeyPair key = SampleKeys.KEY1;

    private final Network network = Network.DEVNET;
    private final TransactionType type = TransactionType.TRANSFER;
    private final byte[] to = Hex.decode("db7cadb25fdcdd546fb0268524107582c3f8999c");
    private final XAmount value = XAmount.of(2);
    private final XAmount fee = config.getDagSpec().getMinTransactionFee();
    private final long nonce = 1;
    private final long timestamp = 1523028482000L;
    private final byte[] data = BytesUtils.of("data");
    private final byte[] encodedBytes = Hex.decode(
            "020114db7cadb25fdcdd546fb0268524107582c3f8999c00000000000000020000000005f5e1000000000000000001000001629b9257d00464617461");

    @Test
    public void testNew() {
        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, data);
        assertNotNull(tx.getHash());
        assertNull(tx.getSignature());
        tx.sign(key);
        assertTrue(tx.validate(network));

        testFields(tx);
    }

    /**
     * Test serialization of a signed tx.
     */
    @Test
    public void testSerialization() {
        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, data);
        tx.sign(key);

        testFields(Transaction.fromBytes(tx.toBytes()));
    }

    @Test
    public void testTransactionSize() {
        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, BytesUtils.random(128))
                .sign(key);
        byte[] bytes = tx.toBytes();

        log.info("tx size: {} B, {} GB per 1M txs", bytes.length, 1000000.0 * bytes.length / 1024 / 1024 / 1024);
    }

    private void testFields(Transaction tx) {
        assertEquals(type, tx.getType());
        assertArrayEquals(Keys.toBytesAddress(key), tx.getFrom());
        assertArrayEquals(to, tx.getTo());
        assertEquals(value, tx.getValue());
        assertEquals(fee, tx.getFee());
        assertEquals(nonce, tx.getNonce());
        assertEquals(timestamp, tx.getTimestamp());
        assertArrayEquals(data, tx.getData());
    }

    @Test
    public void testEquality() {
        Transaction tx = new Transaction(network, type, to, value, fee, nonce, timestamp, BytesUtils.random(128))
                .sign(key);
        Transaction tx2 = new Transaction(network, type, to, value, fee, nonce, timestamp, tx.getData())
                .sign(key);

        assertEquals(tx, tx2);
        assertEquals(tx.hashCode(), tx2.hashCode());
    }

    /**
     * Test encoding of an unsigned tx.
     */
    @Test
    public void testEncoding() {
        Transaction tx = new Transaction(
                network,
                type,
                to,
                value,
                fee,
                nonce,
                timestamp,
                data);

        assertArrayEquals(encodedBytes, tx.getEncoded());
    }

    /**
     * Test decoding of an unsigned tx.
     */
    @Test
    public void testDecoding() {
        Transaction tx = Transaction.fromEncoded(encodedBytes);

        assertEquals(network.id(), tx.getNetworkId());
        assertEquals(type, tx.getType());
        assertArrayEquals(to, tx.getTo());
        assertEquals(value, tx.getValue());
        assertEquals(fee, tx.getFee());
        assertEquals(nonce, tx.getNonce());
        assertEquals(timestamp, tx.getTimestamp());
        assertArrayEquals(data, tx.getData());
    }
}
