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

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;

/**
 * Represent a network packet frame in the xdagj. Numbers are signed and in big-endian.
 *
 * <ul>
 * <li><code>FRAME := HEADER (16 bytes) + BODY (variable length)</code></li>
 * <li><code>HEADER := VERSION + COMPRESS_TYPE + PACKET_TYPE + PACKET_ID + PACKET_SIZE + BODY_SIZE</code></li>
 * <li><code>BODY := BINARY_DATA</code></li>
 * </ul>
 */
@Getter
@Setter
public class Frame {

    public static final int HEADER_SIZE = 16;
    public static final short VERSION = 0;
    public static final byte COMPRESS_NONE = 0;
    public static final byte COMPRESS_SNAPPY = 1;

    protected final short version;     /* version,       2 bytes */
    protected final byte compressType; /* compress type, 1 byte  */
    protected final byte packetType;   /* packet type,   1 byte  */
    protected final int packetId;      /* packet id,     4 bytes */
    protected final int packetSize;    /* packet size,   4 bytes */
    protected final int bodySize;      /* body size,     4 bytes */

    protected byte[] body;

    public Frame(short version, byte compressType, byte packetType, int packetId, int packetSize, int bodySize,
            byte[] body) {
        this.version = version;
        this.compressType = compressType;
        this.packetType = packetType;
        this.packetId = packetId;
        this.packetSize = packetSize;
        this.bodySize = bodySize;

        this.body = body;
    }

    /**
     * Returns whether the packet is chunked.
     */
    public boolean isChunked() {
        return bodySize != packetSize;
    }

    /**
     * Writes frame header into the buffer.
     *
     */
    public void writeHeader(ByteBuf buf) {
        buf.writeShort(getVersion());
        buf.writeByte(getCompressType());
        buf.writeByte(getPacketType());
        buf.writeInt(getPacketId());
        buf.writeInt(getPacketSize());
        buf.writeInt(getBodySize());
    }

    /**
     * Reads frame header from the given buffer.
     */
    public static Frame readHeader(ByteBuf in) {
        short version = in.readShort();
        byte compressType = in.readByte();
        byte packetType = in.readByte();
        int packetId = in.readInt();
        int packetSize = in.readInt();
        int bodySize = in.readInt();

        return new Frame(version, compressType, packetType, packetId, packetSize, bodySize, null);
    }

    @Override
    public String toString() {
        return "PacketFrame [version=" + version + ", compressType=" + compressType + ", packetType=" + packetType
                + ", packetId=" + packetId + ", packetSize=" + packetSize + ", bodySize=" + bodySize + "]";
    }

}
