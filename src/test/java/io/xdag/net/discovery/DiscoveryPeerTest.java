package io.xdag.net.discovery;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.xdag.net.discovery.discv5.DiscV5ServiceImpl;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class DiscoveryPeerTest {
    DiscoveryService discV5Service1;
    DiscoveryService discV5Service2;
    DiscoveryService discV5Service3;
    DiscoveryPeer discoveryPeer;

    @Before
    public void startup() throws UnknownHostException {

        String priString = "0x0802122074ca7d1380b2c407be6878669ebb5c7a2ee751bb18198f1a0f214bcb93b894b5";
        Bytes privkeybytes = Bytes.fromHexString(priString);
        PrivKey privKey = KeyKt.unmarshalPrivateKey(privkeybytes.toArrayUnsafe());
        PrivKey privKey1 = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        String s = Hex.toHexString(privKey1.bytes());
        System.out.println(Arrays.toString(Hex.decode(s)));
        discoveryPeer = new DiscoveryPeer(
                Bytes.wrap(privKey.publicKey().raw()),
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10001));
        System.out.println(discoveryPeer.getNodeAddress().toString());
        List<String> boot = new ArrayList<>();
        Bytes bytes = Bytes.wrap(privKey.raw());
        discV5Service1 = DiscV5ServiceImpl.create((bytes),
                "127.0.0.1",
                10001,
                Collections.emptyList());
        if(discV5Service1.getEnr().isPresent()){
            boot.add(discV5Service1.getEnr().get());
            System.out.println(discV5Service1.getEnr().get());
        }
        discV5Service2 = DiscV5ServiceImpl.create(Bytes.wrap(KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1().raw()),
                "127.0.0.1",11111, boot);
        discV5Service1.start();
        discV5Service1.searchForPeers();

        discV5Service2.start();
        discV5Service2.searchForPeers();
    }

    @Test
    public void ShouldFindTheSeedNode() {
        if(discV5Service2.streamKnownPeers().findFirst().isPresent()){
            assert discoveryPeer.equals(discV5Service2.streamKnownPeers().findFirst().get());
        }
    }

}