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
package io.xdag.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class FrameTest {

    @Test
    public void testReadAndWrite() {
        short version = 0x1122;
        byte compressType = 0x33;
        byte packetType = 0x44;
        int packetId = 0x55667788;
        int packetSize = 0x99aabbcc;
        int bodySize = 0xddeeff00;
        Frame frame = new Frame(version, compressType, packetType, packetId, packetSize, bodySize, null);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[Frame.HEADER_SIZE]);

        buf.writerIndex(0);
        frame.writeHeader(buf);

        buf.readerIndex(0);
        Frame frame2 = Frame.readHeader(buf);

        assertEquals(frame.getVersion(), frame2.getVersion());
        assertEquals(frame.getCompressType(), frame2.getCompressType());
        assertEquals(frame.getPacketType(), frame2.getPacketType());
        assertEquals(frame.getPacketId(), frame2.getPacketId());
        assertEquals(frame.getPacketSize(), frame2.getPacketSize());
        assertEquals(frame.getBodySize(), frame2.getBodySize());
        assertEquals(frame.getBody(), frame2.getBody());
    }
}
