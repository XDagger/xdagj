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

import static io.xdag.config.Constants.DNET_PKT_XDAG;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.encoders.Hex;

import java.util.zip.CRC32;

public class NewBlockMessage extends Message {

    private XdagBlock xdagBlock;
    private Block block;
    private int ttl;

    /** 不处理crc */
    public NewBlockMessage(MutableBytes bytes) {
        super(bytes);
    }

    /** 处理crc 创建新的用于发送Block的message */
    public NewBlockMessage(Block block, int ttl) {
        this.block = block;
        this.ttl = ttl;
        this.parsed = true;
        encode();
    }

    /** 不处理crc */
    public NewBlockMessage(XdagBlock xdagBlock, int ttl) {
        super(xdagBlock.getData().mutableCopy());
        this.xdagBlock = xdagBlock;
        this.ttl = ttl;
    }

    public Block getBlock() {
        parse();
        return block;
    }

    private void parse() {
        if (parsed) {
            return;
        }
        block = new Block(xdagBlock);
        parsed = true;
    }

    private void encode() {
        this.encoded = this.block.getXdagBlock().getData().mutableCopy();
        long transportheader = ((long) ttl << 8) | DNET_PKT_XDAG | (512 << 16);
//        System.arraycopy(BytesUtils.longToBytes(transportheader, true), 0, this.encoded, 0, 8);
        this.encoded.set(0, Bytes.wrap(BytesUtils.longToBytes(transportheader, true)));
        updateCrc();
    }

    public void updateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(encoded.toArray(), 0, 512);
//        System.arraycopy(BytesUtils.intToBytes((int) crc32.getValue(), true), 0, encoded, 4, 4);
        this.encoded.set(4, Bytes.wrap(BytesUtils.intToBytes((int) crc32.getValue(), true)));
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @Override
    public Bytes getEncoded() {
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.NEW_BLOCK;
    }

    @Override
    public String toString() {
        return "NewBlock Message:" + encoded.toHexString();
    }
}
