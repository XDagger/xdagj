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
import io.xdag.net.message.p2p.DisconnectMessage;
import io.xdag.net.message.p2p.HelloMessage;
import io.xdag.net.message.p2p.InitMessage;
import io.xdag.net.message.p2p.PingMessage;
import io.xdag.net.message.p2p.PongMessage;
import io.xdag.net.message.p2p.WorldMessage;
import io.xdag.utils.exception.UnreachableException;
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
            switch (c) {
            case HANDSHAKE_INIT:
                return new InitMessage(body);
            case HANDSHAKE_HELLO:
                return new HelloMessage(body);
            case HANDSHAKE_WORLD:
                return new WorldMessage(body);
            case DISCONNECT:
                return new DisconnectMessage(body);
            case PING:
                return new PingMessage(body);
            case PONG:
                return new PongMessage(body);
            case BLOCKS_REQUEST:
                return new BlocksRequestMessage(body);
            case BLOCKS_REPLY:
                return new BlocksReplyMessage(body);
            case SUMS_REQUEST:
                return new SumRequestMessage(body);
            case SUMS_REPLY:
                return new SumReplyMessage(body);
            case BLOCKEXT_REQUEST:
                return new BlockExtRequestMessage(body);
            case BLOCKEXT_REPLY:
                return new BlockExtReplyMessage(body);
            case BLOCK_REQUEST:
                return new BlockRequestMessage(body);
            case NEW_BLOCK:
                return new NewBlockMessage(body);

            default:
                throw new UnreachableException();
            }
        } catch (Exception e) {
            throw new MessageException("Failed to decode message", e);
        }
    }

}
