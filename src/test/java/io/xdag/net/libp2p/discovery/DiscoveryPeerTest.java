package io.xdag.net.libp2p.discovery;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.xdag.net.libp2p.discovery.discv5.DiscV5ServiceImpl;
import io.xdag.net.libp2p.peer.DiscoveryPeerConverter;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class DiscoveryPeerTest {
    DiscoveryService discV5Service1;
    DiscoveryService discV5Service2;
    DiscoveryPeer discoveryPeer;

    @Before
    public void startup() throws UnknownHostException {
        PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        discoveryPeer = new DiscoveryPeer(
                Bytes.wrap(privKey.publicKey().raw()),
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10000));
        List<String> boot = new ArrayList<>();
        Bytes bytes = Bytes.wrap(privKey.raw());
        discV5Service1 = DiscV5ServiceImpl.create((bytes),
                "127.0.0.1",
                10000,
                Collections.emptyList());
        boot.add(discV5Service1.getEnr().get());
        discV5Service2 = DiscV5ServiceImpl.create(Bytes.wrap(KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1().raw()),
                "127.0.0.1",11111, boot);
        discV5Service1.start();
        discV5Service1.searchForPeers();

        discV5Service2.start();
        discV5Service2.searchForPeers();
    }

    @Test
    public void ShouldFindTheSeedNode() {

        assert discoveryPeer.equals(discV5Service2.streamKnownPeers().findFirst().get());
    }

}