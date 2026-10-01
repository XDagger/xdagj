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

import static io.xdag.config.Constants.MAIN_CHAIN_PERIOD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ChainHarness;
import io.xdag.crypto.keys.ECKeyPair;
import java.io.File;
import io.xdag.utils.XdagTime;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Two nodes on the loopback interface, talking through the real P2P layer: connect through a seed, gossip a
 * block, answer a block request, and drop a peer that sends rubbish. Nothing here leaves the machine.
 */
public class TwoNodeNetworkTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    private NetNode a;
    private NetNode b;

    private static void await(String what, long timeoutMs, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("timed out waiting for " + what);
    }

    @After
    public void tearDown() {
        if (b != null) {
            b.stop();
        }
        if (a != null) {
            a.stop();
        }
    }

    @Test
    public void nodesConnectThroughASeedAndExchangeBlocks() throws Exception {
        int portA = 18801;
        int portB = 18802;
        a = new NetNode(root.newFolder(), portA, List.of());
        b = new NetNode(root.newFolder(), portB, List.of(new InetSocketAddress("127.0.0.1", portA)));
        a.start();
        b.start();

        // the devnet chain has the fork in force from genesis: both nodes are open
        assertTrue(a.channelMgr.isOpen());
        assertTrue(b.channelMgr.isOpen());

        b.awaitConnectedTo(20_000, a);
        Channel aSeesB = a.peer();
        Channel bSeesA = b.peer();
        assertEquals(b.channelMgr.getNodeKey().toBase58Address(), aSeesB.getRemotePeer().getPeerId());
        assertEquals(a.channelMgr.getNodeKey().toBase58Address(), bSeesA.getRemotePeer().getPeerId());
        assertEquals(portB, aSeesB.getRemotePeer().getPort());
        assertTrue(aSeesB.isInbound() != bSeesA.isInbound());

        // gossip: a block created on A reaches B
        long lately = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp()) - 2 * MAIN_CHAIN_PERIOD;
        Block gossiped = a.h.candidateAt(lately, a.key, "gossip");
        a.h.add(gossiped);
        a.channelMgr.onNewForeignBlock(new BlockWrapper(gossiped, 5, null, false));
        await("the gossiped block on B", 10_000, () -> b.h.chain.getBlockByHash(gossiped.getHashLow(), false) != null);

        // request: a block that only A has is fetched by B on request - also one from long ago, which B would
        // not take as news
        Block requested = a.h.candidate(1, a.key, "requested");
        a.h.add(requested);
        a.channelMgr.onNewForeignBlock(new BlockWrapper(requested, 5, null, false));
        Thread.sleep(500);
        assertEquals(null, b.h.chain.getBlockByHash(requested.getHashLow(), false));
        bSeesA.getP2pHandler().sendGetBlock(requested.getHashLow().mutableCopy(), false);
        await("the requested block on B", 10_000, () -> b.h.chain.getBlockByHash(requested.getHashLow(), false) != null);
        assertNotNull(b.h.chain.getBlockByHash(requested.getHashLow(), true));

        // a peer that sends rubbish is dropped
        bSeesA.getTransport().send(Bytes.of(0x20, 1, 2, 3));
        await("A to drop B", 10_000, () -> !aSeesB.isActive() && a.peer() == null);
        assertFalse(aSeesB.isActive());
    }

    @Test
    public void closedNodeOnlyTalksToConfiguredPeers() throws Exception {
        int portClosed = 18803;
        int portStranger = 18804;
        // the fork is not scheduled on this chain: the node runs closed, with nobody configured
        a = new NetNode(root.newFolder(), portClosed, List.of(), List.of(), io.xdag.config.Constants.OPEN_NET_FORK_NOT_SCHEDULED);
        b = new NetNode(root.newFolder(), portStranger, List.of(new InetSocketAddress("127.0.0.1", portClosed)));
        a.start();
        b.start();
        assertFalse("not open before the fork", a.channelMgr.isOpen());
        assertTrue(b.channelMgr.isOpen());

        Thread.sleep(6_000);
        assertEquals("a stranger is refused by a closed node", 0, a.channelMgr.getActiveChannels().size());
        assertEquals(0, b.channelMgr.getActiveChannels().size());

        // the operator lists the peer: now it is welcome, and dialled
        a.channelMgr.connect("127.0.0.1", portStranger);
        await("the configured peer to connect", 20_000, () -> a.peer() != null && b.peer() != null);
        assertFalse("still closed", a.channelMgr.isOpen());
    }
}
