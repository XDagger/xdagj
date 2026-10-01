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
package io.xdag.net.message;

import io.xdag.net.message.consensus.BlockExtReplyMessage;
import io.xdag.net.message.consensus.BlockExtRequestMessage;
import io.xdag.net.message.consensus.BlockRequestMessage;
import io.xdag.net.message.consensus.BlocksReplyMessage;
import io.xdag.net.message.consensus.BlocksRequestMessage;
import io.xdag.net.message.consensus.NewBlockMessage;
import io.xdag.net.message.consensus.SumReplyMessage;
import io.xdag.net.message.consensus.SumRequestMessage;
import io.xdag.net.message.consensus.SyncBlockMessage;
import io.xdag.net.message.consensus.SyncBlockRequestMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageFactory {

    /**
     * Decodes a message that came from a peer. Nothing in the body is trusted: a body that does not decode
     * into a message of the declared kind is a {@link MessageException}.
     *
     * @return the message, or null if the code is not an XDAG message code
     */
    public Message create(byte code, byte[] body) throws MessageException {
        MessageCode c = MessageCode.of(code);
        if (c == null) {
            return null;
        }
        if (body == null) {
            throw new MessageException("Message body is null for code: " + c);
        }

        try {
            return switch (c) {
                case BLOCKS_REQUEST -> new BlocksRequestMessage(body);
                case BLOCKS_REPLY -> new BlocksReplyMessage(body);
                case SUMS_REQUEST -> new SumRequestMessage(body);
                case SUMS_REPLY -> new SumReplyMessage(body);
                case BLOCKEXT_REQUEST -> new BlockExtRequestMessage(body);
                case BLOCKEXT_REPLY -> new BlockExtReplyMessage(body);
                case BLOCK_REQUEST -> new BlockRequestMessage(body);
                case NEW_BLOCK -> new NewBlockMessage(body);
                case SYNC_BLOCK -> new SyncBlockMessage(body);
                case SYNCBLOCK_REQUEST -> new SyncBlockRequestMessage(body);
            };
        } catch (Exception e) {
            throw new MessageException("Failed to decode message " + c + ": " + e.getMessage(), e);
        }
    }
}
