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
package io.xdag.consensus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.Kernel;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.db.BlockStore;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.net.Peer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Who gets a round when: every peer that is connected when a cycle begins gets one round in it, nobody gets two,
 * and nobody gets ahead of the others by being new. (The store of this test has no sums, so a round is over at
 * once; the test lets one round happen per call.)
 */
public class XdagSyncCycleTest {

    private final List<Channel> channels = new ArrayList<>();
    private XdagSync sync;

    @Before
    public void setUp() {
        Kernel kernel = new Kernel(new DevnetConfig(), ECKeyPair.generate());
        ChannelManager channelMgr = mock(ChannelManager.class);
        when(channelMgr.getActiveChannels()).thenAnswer(invocation -> new ArrayList<>(channels));
        kernel.setChannelMgr(channelMgr);
        kernel.setBlockStore(mock(BlockStore.class));
        sync = new XdagSync(kernel);
        kernel.setSync(sync);
        sync.setLoopTimeBudgetMs(0);
    }

    private Channel connect(String id) {
        Channel channel = mock(Channel.class);
        Peer peer = mock(Peer.class);
        when(peer.getPeerId()).thenReturn(id);
        when(channel.getRemotePeer()).thenReturn(peer);
        when(channel.isActive()).thenReturn(true);
        channels.add(channel);
        return channel;
    }

    @Test
    public void everyPeerOfACycleGetsOneRoundAndNewcomersWait() throws Exception {
        connect("a");
        connect("b");
        sync.syncOnce();
        assertNull("the cycle is not over", sync.getLastCycle());

        // somebody connects while the cycle is under way
        connect("c");
        sync.syncOnce();
        assertNotNull(sync.lastRound("a"));
        assertNotNull(sync.lastRound("b"));
        assertNull("a newcomer waits for the next cycle", sync.lastRound("c"));
        XdagSync.CycleResult first = sync.getLastCycle();
        assertNotNull(first);
        assertEquals(2, first.attempted());
        long firstRoundOfA = sync.lastRound("a").startedAt();

        // ... in which everybody has a round again, whoever is new
        Thread.sleep(5);
        sync.syncOnce();
        sync.syncOnce();
        sync.syncOnce();
        XdagSync.CycleResult second = sync.getLastCycle();
        assertEquals(3, second.attempted());
        assertTrue(second.startedAt() >= first.finishedAt());
        assertNotNull(sync.lastRound("c"));
        assertTrue(sync.lastRound("a").startedAt() > firstRoundOfA);
    }

    @Test
    public void aPeerThatLeftIsNotWaitedFor() {
        connect("a");
        Channel b = connect("b");
        connect("c");
        sync.syncOnce();
        channels.remove(b);
        sync.syncOnce();
        XdagSync.CycleResult cycle = sync.getLastCycle();
        assertNotNull("the two that are still there have had their rounds", cycle);
        assertEquals(2, cycle.attempted());
        assertNull(sync.lastRound("b"));
    }

    @Test
    public void nothingHappensWithoutPeers() {
        assertEquals(false, sync.syncOnce());
        assertNull(sync.getLastCycle());
    }
}
