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

import io.xdag.net.message.consensus.*;
import io.xdag.net.message.p2p.DisconnectMessage;
import io.xdag.net.message.p2p.HelloMessage;
import io.xdag.net.message.p2p.InitMessage;
import io.xdag.net.message.p2p.PingMessage;
import io.xdag.net.message.p2p.PongMessage;
import io.xdag.net.message.p2p.WorldMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageFactory {
    /**
     * Decode a raw message.
     *
     * @param code
     *            The message code
     * @param body
     *            The message body
     * @return The decoded message, or NULL if the message type is not unknown
     * @throws MessageException
     *             when the encoding is illegal
     */
    public Message create(byte code, byte[] body) throws MessageException {

        MessageCode c = MessageCode.of(code);
        if (c == null) {
            //log.debug("Invalid message code: {}", Hex.encode0x(Bytes.of(code)));
            return null;
        }

        try {
            return switch (c) {
                case HANDSHAKE_INIT -> new InitMessage(body);
                case HANDSHAKE_HELLO -> new HelloMessage(body);
                case HANDSHAKE_WORLD -> new WorldMessage(body);
                case DISCONNECT -> new DisconnectMessage(body);
                case PING -> new PingMessage(body);
                case PONG -> new PongMessage(body);
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
            throw new MessageException("Failed to decode message", e);
        }
    }

}
