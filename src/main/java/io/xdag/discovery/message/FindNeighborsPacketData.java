package io.xdag.discovery.message;


import io.xdag.discovery.data.PacketData;
import io.xdag.utils.discoveryutils.RLPInput;
import io.xdag.utils.discoveryutils.bytes.BytesValue;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;

import static com.google.common.base.Preconditions.checkArgument;

public class FindNeighborsPacketData implements PacketData {
    private static final int TARGET_SIZE = 37;

    /* Node ID. */
    private final BytesValue target;

    /* In millis after epoch. */
    private final long expiration;

    private FindNeighborsPacketData(final BytesValue target, final long expiration) {
        checkArgument(target != null && target.size() == TARGET_SIZE, "target must be a valid node id");
        checkArgument(expiration >= 0, "expiration must be positive");

        this.target = target;
        this.expiration = expiration;
    }

    public static FindNeighborsPacketData create(final BytesValue target) {
        return new FindNeighborsPacketData(
                target, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        out.writeBytesValue(target);
        out.writeLongScalar(expiration);
        out.endList();
    }

    public static FindNeighborsPacketData readFrom(final RLPInput in) {
        in.enterList();
        final BytesValue target = in.readBytesValue();
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new FindNeighborsPacketData(target, expiration);
    }

    public long getExpiration() {
        return expiration;
    }

    public BytesValue getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "FindNeighborsPacketData{" + "expiration=" + expiration + ", target=" + target + '}';
    }
}
