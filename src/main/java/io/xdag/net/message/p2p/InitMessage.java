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
package io.xdag.net.message.p2p;

import io.xdag.core.SimpleEncoder;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleDecoder;
import lombok.Getter;

@Getter
public class InitMessage extends Message {

    public static final int SECRET_LENGTH = 32;

    private final byte[] secret;
    private final long timestamp;

    public InitMessage(byte[] secret, long timestamp) {
        super(MessageCode.HANDSHAKE_INIT, null);

        this.secret = secret;
        this.timestamp = timestamp;

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(secret);
        enc.writeLong(timestamp);

        this.body = enc.toBytes();
    }

    public InitMessage(byte[] body) {
        super(MessageCode.HANDSHAKE_INIT, null);

        SimpleDecoder dec = new SimpleDecoder(body);
        this.secret = dec.readBytes();
        this.timestamp = dec.readLong();

        this.body = body;
    }

    public boolean validate() {
        return secret != null && secret.length == SECRET_LENGTH && timestamp > 0;
    }

    @Override
    public String toString() {
        return "InitMessage{" +
                "secret=" + BytesUtils.toHexString(secret) +
                ", timestamp=" + timestamp +
                '}';
    }
}
