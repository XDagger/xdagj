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

import io.xdag.core.Block;
import io.xdag.utils.SimpleEncoder;
import io.xdag.core.XdagBlock;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.SimpleDecoder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncBlockMessage extends Message {

    private XdagBlock xdagBlock;
    private Block block;
    private int ttl;

    public SyncBlockMessage(byte[] body) {
        super(MessageCode.SYNC_BLOCK, null);

        SimpleDecoder dec = new SimpleDecoder(body);

        this.body = dec.readBytes();
        this.xdagBlock = new XdagBlock(this.body);
        this.block = new Block(this.xdagBlock);
        this.ttl = dec.readInt();
    }

    public SyncBlockMessage(Block block, int ttl) {
        super(MessageCode.SYNC_BLOCK, null);

        this.block = block;
        this.ttl = ttl;

        SimpleEncoder enc = encode();
        this.body = enc.toBytes();
    }

    private SimpleEncoder encode() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(this.block.toBytes());
        enc.writeInt(ttl);
        return enc;
    }

}
