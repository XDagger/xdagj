package io.xdag.discovery.peer;


import io.xdag.discovery.Utils.bytes.Bytes32;
import io.xdag.discovery.Utils.bytes.BytesValue;

public interface PeerId {
    BytesValue getId();

    /**
     * The Keccak-256 hash value of the peer's ID. The value may be memoized to avoid recomputation
     * overhead.
     *
     * @return The Keccak-256 hash of the peer's ID.
     */
    Bytes32 keccak256();
}
