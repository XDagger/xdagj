package io.xdag.libp2p;

import io.xdag.libp2p.peer.NodeId;
import io.xdag.libp2p.peer.PeerAddress;
import io.xdag.libp2p.utils.SafeFuture;
import org.apache.tuweni.bytes.Bytes;

import java.util.Optional;
import java.util.stream.Stream;

public interface P2PNetwork<P> {

    void dail(String peer);

    enum State {
        IDLE,
        RUNNING,
        STOPPED
    }

//    void connect1(String peer);


    PeerAddress createPeerAddress(String peerAddress);

    boolean isConnected(PeerAddress peerAddress);

    Bytes getPrivateKey();

    Optional getPeer(NodeId id);

    Stream streamPeers();

    NodeId parseNodeId(final String nodeId);

    int getPeerCount();

    String getNodeAddress();

    NodeId getNodeId();



    /**
     * Starts the P2P network layer.
     *
     * @return
     */
    SafeFuture<?> start();

    /** Stops the P2P network layer. */
    SafeFuture<?> stop();
}

