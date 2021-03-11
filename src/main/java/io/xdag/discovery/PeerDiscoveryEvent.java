package io.xdag.discovery;

import com.google.common.base.MoreObjects;
import io.xdag.discovery.peers.DiscoveryPeer;

public abstract class PeerDiscoveryEvent {
    private final DiscoveryPeer peer;
    private final long timestamp;

    private PeerDiscoveryEvent(final DiscoveryPeer peer, final long timestamp) {
        this.peer = peer;
        this.timestamp = timestamp;
    }

    public DiscoveryPeer getPeer() {
        return peer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("peer", peer)
                .add("timestamp", timestamp)
                .toString();
    }


    public static class PeerBondedEvent extends PeerDiscoveryEvent {
        public PeerBondedEvent(final DiscoveryPeer peer, final long timestamp) {
            super(peer, timestamp);
        }
    }

}
