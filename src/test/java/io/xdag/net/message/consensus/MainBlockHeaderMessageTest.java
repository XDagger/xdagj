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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Test;

import io.xdag.core.BlockHeader;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;

public class MainBlockHeaderMessageTest {

    @Test
    public void testSerialization() {
        long number = 1;
        byte[] coinbase = BytesUtils.random(20);
        byte[] prevHash = BytesUtils.random(32);
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(Collections.emptyList());
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(Collections.emptyList());
        byte[] data = {};

        BlockHeader header = BlockUtils.createProofOfWorkHeader(prevHash, number, coinbase, timestamp, transactionsRoot, resultsRoot, 0L, data);

        MainBlockHeaderMessage m = new MainBlockHeaderMessage(header);
        assertThat(m.getCode()).isEqualTo(MessageCode.MAIN_BLOCK_HEADER);
        assertThat(m.getResponseMessageClass()).isNull();

        MainBlockHeaderMessage m2 = new MainBlockHeaderMessage(m.getBody());
        assertThat(m2.getCode()).isEqualTo(MessageCode.MAIN_BLOCK_HEADER);
        assertThat(m2.getResponseMessageClass()).isNull();
        assertThat(m2.getHeader()).isEqualToComparingFieldByField(header);
    }
}
