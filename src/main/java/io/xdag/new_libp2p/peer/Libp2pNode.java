package io.xdag.new_libp2p.peer;

import io.libp2p.core.PeerId;
import io.libp2p.core.PeerInfo;
import io.xdag.net.node.NodeStat;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;

import java.util.concurrent.CompletableFuture;

public class Libp2pNode {
    private PeerInfo peerInfo;
    private PeerId peerId;
    private final CompletableFuture completableFuture = new CompletableFuture();


    @Getter
    private NodeStat stat = new NodeStat();

    public Libp2pNode(){

    }

    public Libp2pNode(PeerId peerId) {
        this.peerId = peerId;
    }

    public PeerInfo getPeerInfo() {
        return peerInfo;
    }

    public void setPeerInfo(PeerInfo peerInfo) {
        this.peerInfo = peerInfo;
    }

    public PeerId getPeerId(){
        return peerId;
    }

    public String getHexId() {
        return Hex.encodeHexString(peerId.getBytes());
    }

}