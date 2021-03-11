package io.xdag.discovery.message;

import io.xdag.discovery.data.PacketData;
import io.xdag.utils.discoveryutils.RLPInput;
import io.xdag.utils.discoveryutils.bytes.RLPOutput;
import lombok.extern.slf4j.Slf4j;
import io.xdag.discovery.peers.DefaultPeer;
import io.xdag.discovery.peers.DiscoveryPeer;
import io.xdag.discovery.peers.Peer;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class NeighborsPacketData implements PacketData {

    private final List<DiscoveryPeer> peers;

    /* In millis after epoch. */
    private final long expiration;

    private NeighborsPacketData(final List<DiscoveryPeer> peers, final long expiration) {
        checkArgument(peers != null, "peer list cannot be null");
        checkArgument(expiration >= 0, "expiration must be positive");

        this.peers = peers;
        this.expiration = expiration;
    }

    @SuppressWarnings("unchecked")
    public static NeighborsPacketData create(final List<DiscoveryPeer> peers) {
        log.info("create NeighborsPacketData success");
        return new NeighborsPacketData(
                peers, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
    }

    public static NeighborsPacketData readFrom(final RLPInput in) {
        in.enterList();
        final List<DiscoveryPeer> peers =
                in.readList(rlp -> new DiscoveryPeer(DefaultPeer.readFrom(rlp)));
        System.out.println("peers nums = "+peers.size());
        final long expiration = in.readLongScalar();
        in.leaveList();
        return new NeighborsPacketData(peers, expiration);
    }

    @Override
    public void writeTo(final RLPOutput out) {
        out.startList();
        out.writeList(peers, Peer::writeTo);
        out.writeLongScalar(expiration);
        out.endList();
    }

    public List<DiscoveryPeer> getNodes() {
        return peers;
    }

    public long getExpiration() {
        return expiration;
    }

    @Override
    public String toString() {
        return String.format("NeighborsPacketData{peers=%s, expiration=%d}", peers, expiration);
    }
}
