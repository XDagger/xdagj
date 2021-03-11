package io.xdag.discovery.peers;

import io.xdag.utils.discoveryutils.bytes.BytesValue;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;

import java.security.SecureRandom;

public interface Peer extends PeerId{
    Endpoint getEndpoint();

    /**
     * Generates a random peer ID in a secure manner.
     *
     * @return The generated peer ID.
     */
    static BytesValue randomId() {
        final byte[] id = new byte[37];
//        final byte[] id = new byte[34];
        new SecureRandom().nextBytes(id);
        return BytesValue.wrap(id);
    }

    /**
     * Encodes this peer to its RLP representation.
     *
     * @param out The RLP output stream to which to write.
     */
    default void writeTo(final RLPOutput out) {
        out.startList();
        getEndpoint().encodeInline(out);
        out.writeBytesValue(getId());
        out.endList();
    }
}
