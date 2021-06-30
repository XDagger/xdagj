/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.net.libp2p.discovery;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class DiscoveryPeerTest {
    DiscV5Service discV5Service1;
    DiscV5Service discV5Service2;
    DiscoveryPeer discoveryPeer;

    @Before
    public void startup() throws UnknownHostException {

        String priString = "0x0802122074ca7d1380b2c407be6878669ebb5c7a2ee751bb18198f1a0f214bcb93b894b5";
        Bytes privkeybytes = Bytes.fromHexString(priString);
        PrivKey privKey = KeyKt.unmarshalPrivateKey(privkeybytes.toArrayUnsafe());
//        PrivKey privKey1 = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
//        String s = Hex.toHexString(privKey1.bytes());
//        System.out.println(Arrays.toString(Hex.decode(s)));
        discoveryPeer = new DiscoveryPeer(
                Bytes.wrap(privKey.publicKey().raw()),
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10001));
//        System.out.println(discoveryPeer.getNodeAddress().toString());
        List<String> boot = new ArrayList<>();
        Bytes bytes = Bytes.wrap(privKey.raw());
        discV5Service1 = DiscV5Service.create((bytes),
                "127.0.0.1",
                10001,
                Collections.emptyList());
        if(discV5Service1.getEnr().isPresent()){
            boot.add(discV5Service1.getEnr().get());
        }
        discV5Service2 = DiscV5Service.create(Bytes.wrap(KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1().raw()),
                "127.0.0.1",11111, boot);
        discV5Service1.start();
        discV5Service1.searchForPeers();

        discV5Service2.start();
        discV5Service2.searchForPeers();
    }

    @Test
    public void ShouldFindTheSeedNode() throws InterruptedException {
        Thread.sleep(1000);
        assert discV5Service2.streamKnownPeers().findFirst().isEmpty() || discoveryPeer.equals(discV5Service2.streamKnownPeers().findFirst().get());
    }
    @Test
    public void exitnetwork() throws InterruptedException {
        discV5Service2.stop();
        Thread.sleep(1000);
        assert discV5Service1.streamKnownPeers().count() == 0;
    }

}