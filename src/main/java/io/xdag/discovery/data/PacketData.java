package io.xdag.discovery.data;

import io.xdag.utils.discoveryutils.bytes.RLPOutput;

public interface PacketData {
    /**
     * Expiration is not standardised. We use Geth's expiration period (60 seconds); whereas Parity's
     * is 20 seconds.
     */
    long DEFAULT_EXPIRATION_PERIOD_MS = 60000;

    /**
     * Serializes the implementing packet data onto the provided RLP output buffer.
     *
     * @param out The RLP output buffer.
     */
    void writeTo(RLPOutput out);
}

