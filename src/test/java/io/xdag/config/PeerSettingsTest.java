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
package io.xdag.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.ConfigFactory;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Test;

/** The peer settings that replaced the node whitelist. */
public class PeerSettingsTest {

    private static final String BASE = "admin.telnet.password = x\n";

    @Test
    public void seedsAndTrustedPeersAreParsed() {
        DevnetConfig config = new DevnetConfig();
        config.applySettings(ConfigFactory.parseString(BASE
                + "node.seeds = [\"127.0.0.1:8001\", \"127.0.0.1:8002\"]\n"
                + "node.trustedPeers = [\"127.0.0.1:8003\"]\n"
                + "node.discovery.enabled = false\n"
                + "node.allowPrivateAddresses = false\n"
                + "node.bindIp = 127.0.0.1\n"
                + "node.minConnections = 3\n"
                + "node.maxConnections = 20\n"
                + "node.maxInboundConnections = 10\n"));
        assertEquals(List.of(new InetSocketAddress("127.0.0.1", 8001), new InetSocketAddress("127.0.0.1", 8002)),
                config.getSeedNodes());
        assertEquals(List.of(new InetSocketAddress("127.0.0.1", 8003)), config.getTrustedNodes());
        assertFalse(config.isDiscoveryEnabled());
        assertFalse(config.isAllowPrivateAddresses());
        assertEquals("127.0.0.1", config.getNodeBindIp());
        assertEquals(3, config.getMinConnections());
        assertEquals(20, config.getMaxConnections());
        assertEquals(10, config.getMaxInboundConnections());
    }

    @Test
    public void formerWhitelistBecomesSeedsAndTrustedPeers() {
        DevnetConfig config = new DevnetConfig();
        config.getSeedNodes().clear();
        config.getTrustedNodes().clear();
        config.applySettings(ConfigFactory.parseString(BASE
                + "node.whiteIPs = [\"127.0.0.1:8001\", \"127.0.0.1:8002\"]\n"));
        assertEquals(2, config.getSeedNodes().size());
        assertEquals(2, config.getTrustedNodes().size());
        assertTrue(config.getSeedNodes().contains(new InetSocketAddress("127.0.0.1", 8002)));
    }

    @Test
    public void unusableEntriesAreSkipped() {
        DevnetConfig config = new DevnetConfig();
        config.getSeedNodes().clear();
        config.applySettings(ConfigFactory.parseString(BASE
                + "node.seeds = [\"nonsense\", \"127.0.0.1:notaport\", \"127.0.0.1:8001\"]\n"));
        assertEquals(List.of(new InetSocketAddress("127.0.0.1", 8001)), config.getSeedNodes());
    }

    @Test
    public void defaultsWithoutPeerSettings() {
        DevnetConfig config = new DevnetConfig();
        assertTrue(config.isDiscoveryEnabled());
        assertTrue("devnet default", config.isAllowPrivateAddresses());
        assertTrue(config.getNodeKeyFile().endsWith("/node.key"));
        MainnetConfig mainnet = new MainnetConfig();
        assertFalse("mainnet default", mainnet.isAllowPrivateAddresses());
    }
}
