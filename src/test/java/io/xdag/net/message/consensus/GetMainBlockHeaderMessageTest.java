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

import org.junit.Test;

import io.xdag.net.message.MessageCode;
import io.xdag.net.message.consensus.GetMainBlockHeaderMessage;
import io.xdag.net.message.consensus.MainBlockHeaderMessage;

public class GetMainBlockHeaderMessageTest {

    @Test
    public void testSerialization() {
        long number = 1;

        GetMainBlockHeaderMessage m = new GetMainBlockHeaderMessage(number);
        assertThat(m.getCode()).isEqualTo(MessageCode.GET_MAIN_BLOCK_HEADER);
        assertThat(m.getResponseMessageClass()).isEqualTo(MainBlockHeaderMessage.class);

        GetMainBlockHeaderMessage m2 = new GetMainBlockHeaderMessage(m.getBody());
        assertThat(m2.getCode()).isEqualTo(MessageCode.GET_MAIN_BLOCK_HEADER);
        assertThat(m2.getResponseMessageClass()).isEqualTo(MainBlockHeaderMessage.class);
        assertThat(m2.getNumber()).isEqualTo(number);
    }
}
