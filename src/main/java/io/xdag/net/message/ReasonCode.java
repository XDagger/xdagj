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

public enum ReasonCode {

    /**
     * [0x00] Bad network.
     */
    BAD_NETWORK(0x00),

    /**
     * [0x01] Incompatible protocol.
     */
    BAD_NETWORK_VERSION(0x01),

    /**
     * [0x02] Too many active peers.
     */
    TOO_MANY_PEERS(0x02),

    /**
     * [0x03] Invalid handshake message.
     */
    INVALID_HANDSHAKE(0x03),

    /**
     * [0x04] Duplicated peerId.
     */
    DUPLICATED_PEER_ID(0x04),

    /**
     * [0x05] The message queue is full.
     */
    MESSAGE_QUEUE_FULL(0x05),

    /**
     * [0x06] Another validator peer tries to connect using the same IP.
     */
    VALIDATOR_IP_LIMITED(0x06),

    /**
     * [0x07] The peer tries to re-handshake.
     */
    HANDSHAKE_EXISTS(0x07),

    /**
     * [0x08] The manifests malicious behavior.
     */
    BAD_PEER(0x08);

    private int code;

    private static final ReasonCode[] intToCode = new ReasonCode[256];

    static {
        for (ReasonCode mc : ReasonCode.values()) {
            intToCode[mc.code] = mc;
        }
    }

    public static ReasonCode of(int code) {
        return intToCode[0xff & code];
    }

    ReasonCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public byte toByte() {
        return (byte) code;
    }
}
