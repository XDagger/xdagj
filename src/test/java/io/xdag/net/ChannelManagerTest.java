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
package io.xdag.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.xdag.Kernel;
import io.xdag.config.AbstractConfig;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Blockchain;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * How the node's configuration becomes the P2P layer's: no whitelist, closed until the fork is in force.
 */
public class ChannelManagerTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    private AbstractConfig config(File dir) {
        AbstractConfig config = new DevnetConfig();
        config.setNodeKeyFile(new File(dir, "node.key").getAbsolutePath());
        config.setRootDir(dir.getAbsolutePath());
        return config;
    }

    @Test
    public void seedsAndTrustedPeersAreHandedToTheP2pLayer() throws Exception {
        AbstractConfig config = config(root.newFolder());
        config.getSeedNodes().clear();
        config.getSeedNodes().add(new InetSocketAddress("127.0.0.1", 9001));
        config.getTrustedNodes().add(new InetSocketAddress("127.0.0.1", 9002));
        Kernel kernel = new Kernel(config, ECKeyPair.generate());

        ChannelManager manager = new ChannelManager(kernel);
        P2pConfig p2p = manager.getP2pConfig();

        assertEquals(List.of(new InetSocketAddress("127.0.0.1", 9001)), p2p.getSeedNodes());
        assertEquals(List.of(new InetSocketAddress("127.0.0.1", 9002)), p2p.getActiveNodes());
        assertTrue(p2p.getTrustNodes().contains(InetAddress.getByName("127.0.0.1")));
        assertEquals(config.getNodePort(), p2p.getPort());
        assertEquals(config.getNodeSpec().getNetwork().id(), p2p.getNetworkId());
        assertEquals(config.getNodeSpec().getNetworkVersion(), p2p.getNetworkVersion());
        assertTrue("a devnet lives on private addresses", p2p.isAllowPrivateAddresses());
        // the node identity is the node key, not the wallet key
        assertEquals(manager.getNodeKey().toBase58Address(),
                io.xdag.crypto.keys.AddressUtils.toBase58Address(p2p.getNodeKey().getPublicKey()));
    }

    @Test
    public void networkIsClosedUntilTheForkIsInForce() throws Exception {
        AbstractConfig config = config(root.newFolder());
        Kernel kernel = new Kernel(config, ECKeyPair.generate());
        Blockchain chain = Mockito.mock(Blockchain.class);
        kernel.setBlockchain(chain);

        ChannelManager manager = new ChannelManager(kernel);
        assertFalse("closed by default", manager.isOpen());

        Mockito.when(chain.isOpenNetLatched()).thenReturn(false);
        manager.applyOpenness();
        assertFalse(manager.isOpen());

        Mockito.when(chain.isOpenNetLatched()).thenReturn(true);
        manager.applyOpenness();
        assertTrue("open once the fork is latched", manager.isOpen());
        assertTrue(manager.getP2pService().isPermissionless());
    }

    @Test
    public void nodeKeyIsStableAcrossRestarts() throws Exception {
        File dir = root.newFolder();
        AbstractConfig config = config(dir);
        Kernel kernel = new Kernel(config, ECKeyPair.generate());
        String first = new ChannelManager(kernel).getNodeKey().toBase58Address();
        String second = new ChannelManager(new Kernel(config(dir), ECKeyPair.generate())).getNodeKey().toBase58Address();
        assertEquals(first, second);
    }
}
