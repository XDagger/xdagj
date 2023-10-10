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

import java.math.BigInteger;
import java.time.Duration;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

import io.xdag.config.Constants;
import io.xdag.core.BlockHeader;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.Numeric;
import io.xdag.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlockHeaderTest {

    private final long number = 1;
    private final byte[] coinbase = BytesUtils.random(20);
    private final byte[] prevHash = BytesUtils.random(32);
    private final long timestamp = TimeUtils.currentTimeMillis();
    private final byte[] transactionsRoot = BytesUtils.random(32);
    private final byte[] resultsRoot = BytesUtils.random(32);
    private final long difficultyTarget = Constants.EASIEST_DIFFICULTY_TARGET;
    private final long nonce = 0;
    private final byte[] data = BytesUtils.of("data");

    private byte[] hash;

    @Test
    public void testNew() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, difficultyTarget, nonce, data);
        hash = header.getHash();

        testFields(header);
    }

    @Test
    public void testSerialization() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, difficultyTarget, nonce, data);
        hash = header.getHash();

        testFields(BlockHeader.fromBytes(header.toBytes()));
    }

    @Test
    public void testBlockHeaderSize() {
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, difficultyTarget, nonce, data);
        byte[] bytes = header.toBytes();

        log.info("block header size: {}", bytes.length);
        log.info("block header size (1y): {} GB", 1.0 * bytes.length * Constants.MAIN_BLOCKS_PER_YEAR / 1024 / 1024 / 1024);
    }

    @Test
    public void testCheckProofOfWork() {
        long difficultyTarget = Constants.EASIEST_DIFFICULTY_TARGET;
        long nonce = 0;
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, difficultyTarget, nonce, data);
        StopWatch watch = new StopWatch();
        watch.start();
        while (true) {
            if (header.checkProofOfWork()) {
                watch.stop();
                break;
            }
            nonce += 1;
            header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, difficultyTarget, nonce, data);
        }
        BigInteger target = header.getDifficultyTargetAsInteger();
        System.out.println("Nonce:"+ nonce +", Hash is higher than target: " + Bytes.wrap(header.getHash()) + " vs " + target.toString(16));
    }

    private void testFields(BlockHeader header) {
        assertArrayEquals(hash, header.getHash());
        assertEquals(number, header.getNumber());
        assertArrayEquals(coinbase, header.getCoinbase());
        assertArrayEquals(prevHash, header.getParentHash());
        assertEquals(timestamp, header.getTimestamp());
        assertArrayEquals(transactionsRoot, header.getTransactionsRoot());
        assertArrayEquals(resultsRoot, header.getResultsRoot());
        assertArrayEquals(data, header.getData());
    }
}
