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

import lombok.Getter;

@Getter
public enum MessageCode {

    // =======================================
    // [0x00, 0x0f] Reserved for p2p basics
    // =======================================

    /**
     * [0x00] Inform peer of disconnecting.
     */
    DISCONNECT(0x00),

    /**
     * [0x01] First message over connection. No messages should be sent until
     * receiving a response.
     */
    /**
     * [0x01] A message containing a random bytes
     */
    HANDSHAKE_INIT(0x01),

    /**
     * [0x02] The new HELLO message
     */
    HANDSHAKE_HELLO(0x02),

    /**
     * [0x13] The new WORLD message.
     */
    HANDSHAKE_WORLD(0x03),

    /**
     * [0x04] Request an immediate reply from the peer.
     */
    PING(0x04),

    /**
     * [0x05] Response to a PING message.
     */
    PONG(0x05),

//    /**
//     * [0x06] Request peer to provide a list of known nodes.
//     */
//    GET_NODES(0x06),
//
//    /**
//     * [0x07] Response to a GET_NODES message.
//     */
//    NODES(0x07),

    // =======================================
    // [0x10, 0x1f] Reserved for node
    // =======================================
    BLOCKS_REQUEST(0x10),
    BLOCKS_REPLY(0x11),
    SUMS_REQUEST(0x12),
    SUMS_REPLY(0x13),
    BLOCKEXT_REQUEST(0x14),
    BLOCKEXT_REPLY(0x15),
    BLOCK_REQUEST(0x16),
//    RECEIVE_BLOCK(0x17),
    NEW_BLOCK(0x18),
    SYNC_BLOCK(0x19),
    SYNCBLOCK_REQUEST(0x1A);


    private static final MessageCode[] map = new MessageCode[256];

    static {
        for (MessageCode mc : MessageCode.values()) {
            map[mc.code] = mc;
        }
    }

    public static MessageCode of(int code) {
        return map[0xff & code];
    }

    private int code;

    MessageCode(int code) {
        this.code = code;
    }

    public byte toByte() {
        return (byte) code;
    }

}
