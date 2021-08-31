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
<<<<<<< HEAD:src/test/java/io/xdag/net/libp2p/DiscoveryPeerTest.java
package io.xdag.net.libp2p;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.xdag.net.libp2p.discovery.DiscoveryPeer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;

public class DiscoveryPeerTest {
    DiscoveryPeer peer;
    String expectStirng;
    @Before
    public void start() throws UnknownHostException {
        PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        peer = new DiscoveryPeer(
                Bytes.wrap(privKey.publicKey().raw()),
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10000));
        expectStirng = "/ip4/127.0.0.1/tcp/10000/ipfs/"+ new LibP2PNodeId(PeerId.fromPubKey(privKey.publicKey())).toString();

    }
    @Test
    public void TestDiscoveryPeerToDailNodeId(){
        assertEquals(expectStirng, Libp2pUtils.discoveryPeerToDailId(peer));
    }
=======

package io.xdag.net.libp2p.discovery;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.xdag.utils.SafeFuture;
import org.junit.Test;

public class DiscoveryNetworkTest {

    private final DiscV5Service discoveryService = mock(DiscV5Service.class);

    @Test
    public void DiscoveryStart() {
        final SafeFuture<Void> discoveryStart = new SafeFuture<>();
        doReturn(discoveryStart).when(discoveryService).start();
    }

    @Test
    @SuppressWarnings({"FutureReturnValueIgnored"})
    public void shouldStopNetworkAndDiscoveryWhenConnectionManagerStopFails() {
        doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    }

>>>>>>> feature/snapshot:src/test/java/io/xdag/net/libp2p/discovery/DiscoveryNetworkTest.java
}
