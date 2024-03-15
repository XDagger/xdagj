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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.xerial.snappy.Snappy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.xdag.config.Config;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageException;
import io.xdag.net.message.MessageFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagMessageHandler extends MessageToMessageCodec<Frame, Message>  {
    private static final int MAX_PACKETS = 16;

    private static final byte COMPRESS_TYPE = Frame.COMPRESS_SNAPPY;

    private final Cache<Integer, Pair<List<Frame>, AtomicInteger>> incompletePackets = Caffeine.newBuilder()
            .maximumSize(MAX_PACKETS).build();

    private final Config config;

    private final MessageFactory messageFactory;
    private final AtomicInteger count;

    private final int netMaxPacketSize;

    public XdagMessageHandler(Config config) {
        this.config = config;
        this.netMaxPacketSize = config.getNodeSpec().getNetMaxPacketSize();
        this.messageFactory = new MessageFactory();
        this.count = new AtomicInteger(0);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        byte[] data = msg.getBody();
        byte[] dataCompressed = data;

        switch (COMPRESS_TYPE) {
        case Frame.COMPRESS_SNAPPY:
            dataCompressed = Snappy.compress(data);
            break;
        case Frame.COMPRESS_NONE:
            break;
        default:
            log.error("Unsupported compress type: " + COMPRESS_TYPE);
            return;
        }

        byte packetType = msg.getCode().toByte();
        int packetId = count.incrementAndGet();
        int packetSize = dataCompressed.length;

        if (data.length > netMaxPacketSize || dataCompressed.length > netMaxPacketSize) {
            log.error("Invalid packet size, max = {}, actual = {}", netMaxPacketSize, packetSize);
            return;
        }

        int limit = config.getNodeSpec().getNetMaxFrameBodySize();
        int total = (dataCompressed.length - 1) / limit + 1;
        for (int i = 0; i < total; i++) {
            byte[] body = new byte[(i < total - 1) ? limit : dataCompressed.length % limit];
            System.arraycopy(dataCompressed, i * limit, body, 0, body.length);

            out.add(new Frame(Frame.VERSION, COMPRESS_TYPE, packetType, packetId, packetSize, body.length, body));
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Frame frame, List<Object> out) throws Exception {
        Message decodedMsg = null;

        if (frame.isChunked()) {
            synchronized (incompletePackets) {
                int packetId = frame.getPacketId();
                Pair<List<Frame>, AtomicInteger> pair = incompletePackets.getIfPresent(packetId);
                if (pair == null) {
                    int packetSize = frame.getPacketSize();
                    if (packetSize < 0 || packetSize > netMaxPacketSize) {
                        // this will kill the connection
                        throw new IOException("Invalid packet size: " + packetSize);
                    }

                    pair = Pair.of(new ArrayList<>(), new AtomicInteger(packetSize));
                    incompletePackets.put(packetId, pair);
                }

                pair.getLeft().add(frame);
                int remaining = pair.getRight().addAndGet(-frame.getBodySize());
                if (remaining == 0) {
                    decodedMsg = decodeMessage(pair.getLeft());

                    // remove complete packets from cache
                    incompletePackets.invalidate(packetId);
                } else if (remaining < 0) {
                    throw new IOException("Packet remaining size went to negative");
                }
            }
        } else {
            decodedMsg = decodeMessage(Collections.singletonList(frame));
        }

        if (decodedMsg != null) {
            out.add(decodedMsg);
        }
    }

    protected Message decodeMessage(List<Frame> frames) throws MessageException {
        if (frames == null || frames.isEmpty()) {
            throw new MessageException("Frames can't be null or empty");
        }
        Frame head = frames.get(0);

        byte packetType = head.getPacketType();
        int packetSize = head.getPacketSize();

        byte[] data = new byte[packetSize];
        int pos = 0;
        for (Frame frame : frames) {
            System.arraycopy(frame.getBody(), 0, data, pos, frame.getBodySize());
            pos += frame.getBodySize();
        }

        switch (head.getCompressType()) {
        case Frame.COMPRESS_SNAPPY:
            try {
                // check uncompressed length to avoid OOM vulnerability
                int length = Snappy.uncompressedLength(data);
                if (length > netMaxPacketSize) {
                    throw new MessageException("Uncompressed data length is too big: " + length);
                }
                data = Snappy.uncompress(data);
            } catch (IOException e) {
                throw new MessageException(e);
            }
            break;
        case Frame.COMPRESS_NONE:
            break;
        default:
            throw new MessageException("Unsupported compress type: " + head.getCompressType());
        }

        return messageFactory.create(packetType, data);
    }
}
