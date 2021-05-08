package io.xdag.net.libp2p;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.xdag.net.libp2p.peer.LibP2PNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import io.xdag.utils.MultiaddrUtil;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

public class Libp2pNetworkTest {

    //node 0
    PrivKey privKey0 = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1,0).getFirst();
    PeerId peerId0 = PeerId.fromPubKey(privKey0.publicKey());
    NodeId nodeId0 = new LibP2PNodeId(peerId0);
    Multiaddr advertisedAddr =
            MultiaddrUtil.fromInetSocketAddress(
                    new InetSocketAddress("127.0.0.1", 11111),nodeId0);
    Libp2pNetwork node0 = new Libp2pNetwork( privKey0,advertisedAddr);

    // node 1
    PrivKey privKey1 = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1,0).getFirst();
    PeerId peerId1 = PeerId.fromPubKey(privKey1.publicKey());
    NodeId nodeId1 = new LibP2PNodeId(peerId1);
    Multiaddr advertisedAddr1 =
            MultiaddrUtil.fromInetSocketAddress(
                    new InetSocketAddress("127.0.0.1", 12121),nodeId1);
    Libp2pNetwork node1 = new Libp2pNetwork( privKey1,advertisedAddr1);
    @Before
    public void startup(){
        node0.start();
        node1.start();
    }
    @Test
    public void testlibp2pconnect() throws InterruptedException {
        assert node0.peerManager.getPeerCount() == 0;
        assert node1.peerManager.getPeerCount() == 0;
        // Alternative connection format
        String peer0 = advertisedAddr.toString();
        peer0 = peer0.replaceAll("p2p","ipfs");
        // connect
        node1.dail(peer0);
        // wait connect success
        Thread.sleep(1000);
        assert node1.peerManager.getPeerCount() == 1;


    }
}