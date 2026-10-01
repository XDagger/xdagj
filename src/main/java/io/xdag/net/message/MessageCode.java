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

/**
 * Codes of the XDAG protocol messages carried by the P2P layer (xdagj-p2p). Codes below 0x20 belong to the
 * P2P layer itself (discovery, handshake, keep-alive); the application range starts at 0x20.
 */
@Getter
public enum MessageCode {

    // =======================================
    // [0x20, 0x2f] XDAG node protocol
    // =======================================
    BLOCKS_REQUEST(0x20),
    BLOCKS_REPLY(0x21),
    SUMS_REQUEST(0x22),
    SUMS_REPLY(0x23),
    BLOCKEXT_REQUEST(0x24),
    BLOCKEXT_REPLY(0x25),
    BLOCK_REQUEST(0x26),
    NEW_BLOCK(0x28),
    SYNC_BLOCK(0x29),
    SYNCBLOCK_REQUEST(0x2A);

    private static final MessageCode[] map = new MessageCode[256];

    static {
        for (MessageCode mc : MessageCode.values()) {
            map[mc.code] = mc;
        }
    }

    public static MessageCode of(int code) {
        return map[0xff & code];
    }

    private final int code;

    MessageCode(int code) {
        this.code = code;
    }

    public byte toByte() {
        return (byte) code;
    }
}
