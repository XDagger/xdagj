package io.xdag.libp2p.peer;

import java.util.Objects;
import java.util.Optional;

public class PeerAddress {
    private final NodeId id;

    public PeerAddress(final NodeId id) {
        this.id = id;
    }

    public String toExternalForm() {
        return toString();
    }

    public NodeId getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> as(final Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return Optional.of((T) this);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PeerAddress that = (PeerAddress) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

