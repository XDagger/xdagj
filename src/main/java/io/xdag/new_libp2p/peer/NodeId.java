package io.xdag.new_libp2p.peer;


import org.apache.tuweni.bytes.Bytes;

public abstract class NodeId {

    public abstract Bytes toBytes();

    public abstract String toBase58();

    @Override
    public final int hashCode() {
        return toBytes().hashCode();
    }

    @Override
    public final boolean equals(final Object obj) {
        if (!(obj instanceof NodeId)) {
            return false;
        }
        return toBytes().equals(((NodeId) obj).toBytes());
    }

    @Override
    public final String toString() {
        return toBase58();
    }
}