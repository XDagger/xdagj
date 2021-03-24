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
package io.xdag.discovery.data;

import io.xdag.discovery.message.FindNeighborsPacketData;
import io.xdag.discovery.message.NeighborsPacketData;
import io.xdag.discovery.message.PingPacketData;
import io.xdag.discovery.message.PongPacketData;
import io.xdag.utils.discoveryutils.RLPInput;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public enum PacketType {
    PING(0x01, PingPacketData::readFrom),
    PONG(0x02, PongPacketData::readFrom),
    FIND_NEIGHBORS(0x03, FindNeighborsPacketData::readFrom),
    NEIGHBORS(0x04, NeighborsPacketData::readFrom);

    private static final int MAX_VALUE = 0x7F;
    private static final int BYTE_MASK = 0xFF;

    private static final PacketType[] INDEX = new PacketType[PacketType.MAX_VALUE];

    static {
        Arrays.stream(values()).forEach(type -> INDEX[type.value] = type);
    }

    private final byte value;
    private final Deserializer<?> deserializer;

    public static Optional<PacketType> forByte(final byte b) {
        return b >= MAX_VALUE || b < 0 ? Optional.empty() : Optional.ofNullable(INDEX[b]);
    }

    PacketType(final int value, final Deserializer<?> deserializer) {
        checkArgument(value <= MAX_VALUE, "Packet type ID must be in range [0x00, 0x80)");
        this.deserializer = deserializer;
        this.value = (byte) (value & BYTE_MASK);
    }

    public byte getValue() {
        return value;
    }

    public Deserializer<?> getDeserializer() {
        return deserializer;
    }

    @FunctionalInterface
    @Immutable
    public interface Deserializer<T extends PacketData> {
        T deserialize(RLPInput in);
    }
}
