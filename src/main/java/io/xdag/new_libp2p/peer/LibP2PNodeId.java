package io.xdag.new_libp2p.peer;

import io.libp2p.core.PeerId;
import org.apache.tuweni.bytes.Bytes;

public class  LibP2PNodeId extends NodeId {
    private final PeerId peerId;

    public LibP2PNodeId(final PeerId peerId) {
        this.peerId = peerId;
    }

    @Override
    public Bytes toBytes() {
        return Bytes.wrap(peerId.getBytes());
    }

    @Override
    public String toBase58() {
        return peerId.toBase58();
    }
}
