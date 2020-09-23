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
package io.xdag.net.message.impl;

import static io.xdag.net.XdagVersion.V03;

import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.XdagMessageCodes;

public class Xdag03MessageFactory implements MessageFactory {
    
    @Override
    public Message create(byte code, byte[] encoded) {
        XdagMessageCodes receivedCommand = XdagMessageCodes.fromByte(code, V03);

        switch (receivedCommand) {
        case BLOCKS_REQUEST:
            return new BlocksRequestMessage(encoded);
        case BLOCKS_REPLY:
            return new BlocksReplyMessage(encoded);
        case SUMS_REQUEST:
            return new SumRequestMessage(encoded);
        case SUMS_REPLY:
            return new SumReplyMessage(encoded);
        case BLOCKEXT_REQUEST:
            return new BlockExtRequestMessage(encoded);
        case BLOCKEXT_REPLY:
            return new BlockExtReplyMessage(encoded);
        case BLOCK_REQUEST:
            return new BlockRequestMessage(encoded);
        case NEW_BLOCK:
            return new NewBlockMessage(encoded);
        default:
            throw new IllegalArgumentException("No such message code" + code);
        }
    }
    
}
