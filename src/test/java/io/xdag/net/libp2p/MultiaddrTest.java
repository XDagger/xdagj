package io.xdag.net.libp2p;

import static org.junit.Assert.*;
import org.junit.Test;

import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;

public class MultiaddrTest {

    @Test
    public void testGetIpAndPort() {
        Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/40002");
        String ip = Protocol.IP4.bytesToAddress(multiaddr.getComponent(Protocol.IP4));
        String port = Protocol.TCP.bytesToAddress(multiaddr.getComponent(Protocol.TCP));
        assertEquals("127.0.0.1", ip);
        assertEquals("40002", port);
    }

}
