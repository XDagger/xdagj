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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.Kernel;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Blockchain;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.core.XdagTopStatus;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * When a node calls itself synchronised: by what its peers say while the network is closed, by what the rounds
 * with its peers found once the network is open.
 */
public class SyncDoneDecisionTest {

    private Kernel kernel;
    private Blockchain chain;
    private ChannelManager channelMgr;
    private XdagSync sync;
    private XdagStats stats;
    private XdagTopStatus top;
    private SyncManager manager;

    @Before
    public void setUp() {
        kernel = new Kernel(new DevnetConfig(), io.xdag.crypto.keys.ECKeyPair.generate());
        chain = mock(Blockchain.class);
        channelMgr = mock(ChannelManager.class);
        sync = mock(XdagSync.class);
        stats = new XdagStats();
        top = new XdagTopStatus();
        when(chain.getXdagStats()).thenReturn(stats);
        when(chain.getXdagTopStatus()).thenReturn(top);
        kernel.setBlockchain(chain);
        kernel.setChannelMgr(channelMgr);
        kernel.setSync(sync);
        kernel.setXdagState(XdagState.WDST);
        manager = new SyncManager(kernel);
        manager.setQuietMs(0);
        kernel.setSyncMgr(manager);
    }

    private void peers(int n) {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            channels.add(mock(Channel.class));
        }
        when(channelMgr.getActiveChannels()).thenReturn(channels);
    }

    /** A cycle that began at {@code startedAt}: rounds with so many peers, of which so many failed, ... */
    private static XdagSync.CycleResult cycleAt(long startedAt, int attempted, int failed, int inSync, int behind) {
        return new XdagSync.CycleResult(startedAt, startedAt, attempted, failed, inSync, behind);
    }

    private static XdagSync.CycleResult cycle(int attempted, int failed, int inSync, int behind) {
        return cycleAt(System.currentTimeMillis(), attempted, failed, inSync, behind);
    }

    private boolean decide(int peers, XdagSync.CycleResult lastCycle) {
        manager.getSyncDone().set(false);
        peers(peers);
        when(sync.getLastCycle()).thenReturn(lastCycle);
        manager.checkState();
        return manager.getSyncDone().get();
    }

    @Test
    public void openNetworkDecidesByWhatTheRoundsFound() {
        when(chain.isOpenNetLatched()).thenReturn(true);
        // what peers claim plays no part: a claimed height far above ours does not keep the node syncing ...
        stats.setTotalnmain(1_000_000);
        stats.setMaxdifficulty(BigInteger.TWO.pow(100));
        assertTrue(decide(1, cycle(1, 0, 1, 0)));
        // ... and a recent tip, or nobody claiming more than we have, does not make it synchronised
        stats.setTotalnmain(0);
        stats.setMaxdifficulty(BigInteger.ZERO);
        when(sync.getLastTime()).thenReturn(io.xdag.utils.XdagTime.getCurrentTimestamp());
        assertFalse("no round held yet", decide(3, null));
        assertFalse("nobody to compare with", decide(0, cycle(1, 0, 1, 0)));

        // of the peers that showed blocks from the present, most must have none that we lack
        assertFalse("one says in sync, one says behind", decide(3, cycle(3, 0, 1, 1)));
        assertTrue(decide(3, cycle(3, 0, 2, 1)));
        assertFalse(decide(3, cycle(3, 0, 1, 2)));
        assertFalse(decide(8, cycle(8, 0, 3, 3)));
        assertTrue(decide(8, cycle(8, 0, 4, 3)));
        assertFalse("a peer that has blocks we lack is not outvoted by peers that know nothing",
                decide(5, cycle(5, 0, 0, 1)));
        assertTrue("peers that showed nothing from the present say nothing either way", decide(5, cycle(5, 0, 1, 0)));
        // a peer that did not answer may be the one that knows better
        assertFalse(decide(2, cycle(2, 1, 1, 0)));
        assertFalse(decide(4, cycle(4, 2, 2, 0)));
        assertTrue(decide(4, cycle(4, 1, 2, 0)));
        assertFalse(decide(4, cycle(4, 1, 2, 1)));
    }

    @Test
    public void whereNobodyShowsThePresentHavingTalkedToMostPeersIsEnough() {
        when(chain.isOpenNetLatched()).thenReturn(true);
        // a network on which no blocks are produced (yet): somebody has to begin
        assertTrue(decide(3, cycle(3, 0, 0, 0)));
        assertTrue(decide(3, cycle(3, 1, 0, 0)));
        assertFalse("most rounds failed", decide(3, cycle(3, 2, 0, 0)));
        assertFalse(decide(2, cycle(2, 1, 0, 0)));
    }

    @Test
    public void theRoundsMustBeAboutThePeersTheNodeHasNow() {
        when(chain.isOpenNetLatched()).thenReturn(true);
        // one peer was there when the cycle ran - the first to connect - and now there are eight
        assertFalse(decide(8, cycle(1, 0, 1, 0)));
        assertFalse(decide(8, cycle(3, 0, 3, 0)));
        assertTrue(decide(8, cycle(4, 0, 4, 0)));
    }

    @Test
    public void openNetworkWaitsWhileTheNodeWasCatchingUp() {
        when(chain.isOpenNetLatched()).thenReturn(true);
        long now = System.currentTimeMillis();
        manager.setQuietMs(60_000);
        manager.setLastCatchUpTime(now - 1_000);
        assertFalse("caught up a second ago", decide(2, cycleAt(now, 2, 0, 2, 0)));
        manager.setLastCatchUpTime(now - 61_000);
        assertFalse("the peers had their rounds before the node last caught up",
                decide(2, cycleAt(now - 62_000, 2, 0, 2, 0)));
        assertTrue(decide(2, cycleAt(now - 60_000, 2, 0, 2, 0)));
    }

    @Test
    public void onlyANodeThatIsAloneStartsBecauseItWaitedLongEnough() {
        // (the kernel of this test started at epoch 0: the waiting time is over)
        when(chain.isOpenNetLatched()).thenReturn(true);
        peers(0);
        assertTrue(manager.isTimeToStart());
        peers(1);
        assertFalse("an open node with peers decides by its rounds with them", manager.isTimeToStart());
        when(chain.isOpenNetLatched()).thenReturn(false);
        assertTrue("closed network: as before", manager.isTimeToStart());
    }

    @Test
    public void closedNetworkDecidesAsBefore() {
        when(chain.isOpenNetLatched()).thenReturn(false);
        manager.getIsUpdateXdagStats().set(true);
        stats.setNmain(10);
        stats.setTotalnmain(1000);
        stats.setMaxdifficulty(BigInteger.TEN);
        top.setTopDiff(BigInteger.ONE);
        assertFalse("a configured peer reports a longer chain", decide(1, cycle(1, 0, 1, 0)));
        stats.setTotalnmain(10);
        assertTrue(decide(1, null));
        assertEquals(XdagState.SDST, kernel.getXdagState());
    }
}
