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
