package io.xdag.utils.discoveryutils;

public enum PeerDiscoveryStatus {

    /**
     * Represents a newly discovered {@link DiscoveryPeer}, prior to commencing the bonding exchange.
     */
    KNOWN,

    /**
     * Bonding with this peer is in progress. If we're unable to establish communication and/or
     * complete the bonding exchange, the {@link DiscoveryPeer} remains in this state, until we
     * ultimately desist.
     */
    BONDING,

    /**
     * We have successfully bonded with this {@link DiscoveryPeer}, and we are able to exchange
     * messages with them.
     */
    BONDED;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

