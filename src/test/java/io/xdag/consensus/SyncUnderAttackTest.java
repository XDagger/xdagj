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

import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.MAIN_CHAIN_PERIOD;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.xdag.core.Block;
import io.xdag.core.ChainHarness;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagState;
import io.xdag.net.Channel;
import io.xdag.net.NetNode;
import io.xdag.net.message.consensus.NewBlockMessage;
import io.xdag.utils.XdagTime;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A node that is behind, an honest peer that has the history, and a peer that sends blocks made to deceive:
 * blocks without work but with current timestamps, which become the latest main blocks of the node that is
 * behind (they are the heaviest thing it knows), or blocks that bring its sums to those of the honest peer.
 * Neither may end its synchronisation or make it call itself synchronised.
 * Three real nodes on the loopback interface; the test drives the sync rounds itself.
 */
public class SyncUnderAttackTest {

    private static final int HISTORY = 30;

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    private final List<NetNode> nodes = new ArrayList<>();
    private NetNode honest;
    private NetNode victim;
    private NetNode attacker;
    private final List<Block> history = new ArrayList<>();
    private final List<Block> forged = new ArrayList<>();

    @After
    public void tearDown() {
        for (NetNode n : nodes) {
            try {
                n.stop();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

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

    private NetNode node(int port, List<NetNode> trusted) throws Exception {
        NetNode n = new NetNode(root.newFolder(), port, List.of(),
                trusted.stream().map(NetNode::address).toList(), 0);
        n.syncMgr.setQuietMs(300);
        nodes.add(n);
        return n;
    }

    private static BigInteger work(ChainHarness h, List<Block> chain) {
        BigInteger sum = BigInteger.ZERO;
        for (Block b : chain) {
            sum = sum.add(h.chain.getDiffByRawHash(b.getHash()));
        }
        return sum;
    }

    /** The honest node's chain: one main block candidate per epoch, long ago. */
    private void buildHistory() {
        Bytes32 previous = null;
        for (int e = 1; e <= HISTORY; e++) {
            Block b = previous == null ? honest.h.candidate(e, honest.key, "history-" + e)
                    : honest.h.candidate(e, honest.key, "history-" + e, previous);
            honest.h.add(b);
            history.add(b);
            previous = Bytes32.wrap(b.getHashLow());
        }
        honest.h.settle();
    }

    /**
     * Three blocks for the last three epochs, chained, with arbitrary nonces: no work was done for them. (The
     * salt is varied until they weigh less than the history, as blocks without work do against a real chain;
     * with the hash-based difficulty of the tests that is not a given.)
     */
    private void forgeRecentBlocks() {
        forgeRecentBlocks(history.subList(0, HISTORY - 1));
    }

    private void forgeRecentBlocks(List<Block> heavier) {
        long now = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
        BigInteger historyWork = work(honest.h, heavier);
        for (int attempt = 0; ; attempt++) {
            forged.clear();
            Bytes32 previous = null;
            for (int k = 3; k >= 1; k--) {
                long time = now - k * MAIN_CHAIN_PERIOD;
                Block b = previous == null ? attacker.h.candidateAt(time, attacker.key, "forged-" + attempt + "-" + k)
                        : attacker.h.candidateAt(time, attacker.key, "forged-" + attempt + "-" + k, previous);
                forged.add(b);
                previous = Bytes32.wrap(b.getHashLow());
            }
            if (work(attacker.h, forged).multiply(BigInteger.valueOf(4)).compareTo(historyWork) < 0) {
                break;
            }
        }
        for (Block b : forged) {
            attacker.h.add(b);
        }
    }

    /** The attacker sends its blocks to the victim as news (a time-to-live of one: the victim keeps them to itself). */
    private void pushForgedBlocks() throws InterruptedException {
        Channel toVictim = attacker.peer(victim);
        assertNotNull(toVictim);
        for (Block b : forged) {
            toVictim.getP2pHandler().sendMessage(new NewBlockMessage(b, 1));
        }
        // ... and anything that carries network statistics, which is all the old code waited for before it looked
        toVictim.getP2pHandler().sendGetSums(0, 1L << 48);
        Block last = forged.getLast();
        await("the forged blocks on the victim", 10_000, () -> victim.h.chain.getBlockByHash(last.getHashLow(), false) != null
                && victim.syncMgr.getIsUpdateXdagStats().get());
        victim.h.settle();
        // this is what made the old code call the node synchronised: its latest main block is a few epochs old
        assertTrue("the forged blocks are the victim's latest main blocks",
                victim.sync.getLastTime() >= XdagTime.getCurrentTimestamp() - 4 * MAIN_CHAIN_PERIOD);
    }

    private void assertFollowsHistory() {
        victim.h.settle();
        Block tip = history.get(HISTORY - 2); // the honest node's own tip is still in its memory pool
        assertNotNull("history fetched", victim.h.chain.getBlockByHash(tip.getHashLow(), false));
        assertArrayEquals("the victim follows the chain with the work",
                tip.getHashLow().toArray(), victim.h.chain.getXdagTopStatus().getTop());
        for (Block b : forged) {
            assertEquals("forged block is not a main block", 0, victim.h.flags(b) & BI_MAIN);
        }
        assertEquals(honest.h.flags(history.get(HISTORY - 4)) & BI_MAIN, victim.h.flags(history.get(HISTORY - 4)) & BI_MAIN);
    }

    @Test
    public void blocksWithoutWorkDoNotEndSynchronisation() throws Exception {
        honest = node(18811, List.of());
        attacker = node(18812, List.of());
        victim = node(18813, List.of(honest, attacker));
        buildHistory();
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, honest, attacker);
        forgeRecentBlocks();
        pushForgedBlocks();

        // a recent tip proves nothing
        victim.syncMgr.checkState();
        assertFalse("not synchronised just because the latest main block is recent", victim.syncMgr.getSyncDone().get());

        // the comparison with the peers does not care about the tip: the history is fetched
        victim.sync.syncOnce();
        assertFollowsHistory();
        victim.syncMgr.checkState();
        assertFalse("still catching up a moment ago", victim.syncMgr.getSyncDone().get());

        // nothing left to fetch from anybody, and quiet: now the node is synchronised
        Thread.sleep(400);
        victim.sync.syncOnce();
        assertEquals("a round with either peer", 2, victim.sync.getLastCycle().attempted());
        assertEquals(0, victim.sync.getLastCycle().behind());
        victim.syncMgr.checkState();
        assertTrue(victim.syncMgr.getSyncDone().get());
        assertEquals(XdagState.SDST, victim.h.kernel.getXdagState());
        assertEquals(XdagSync.Status.SYNC_DONE, victim.sync.getStatus());
        assertFollowsHistory();
    }

    @Test
    public void nodeThatWasWrongAboutBeingSynchronisedGoesBackToSynchronising() throws Exception {
        honest = node(18821, List.of());
        attacker = node(18822, List.of());
        victim = node(18823, List.of(attacker));
        buildHistory();
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, attacker);
        forgeRecentBlocks();
        pushForgedBlocks();

        // all the victim can talk to is the attacker: it has nothing the attacker lacks
        victim.sync.syncOnce();
        victim.syncMgr.checkState();
        assertTrue("an eclipsed node cannot know better", victim.syncMgr.getSyncDone().get());
        assertEquals(XdagState.SDST, victim.h.kernel.getXdagState());

        // an honest peer appears; the comparison is still running and brings the history
        victim.channelMgr.connect("127.0.0.1", honest.port);
        victim.awaitConnectedTo(30_000, honest);
        victim.sync.syncOnce();
        assertFalse("history with more work arrived: not synchronised after all", victim.syncMgr.getSyncDone().get());
        assertEquals("no blocks are produced in this state", XdagState.CDST, victim.h.kernel.getXdagState());
        assertEquals(XdagSync.Status.SYNCING, victim.sync.getStatus());
        assertFollowsHistory();

        Thread.sleep(400);
        victim.sync.syncOnce();
        victim.syncMgr.checkState();
        assertTrue(victim.syncMgr.getSyncDone().get());
        assertEquals(XdagState.SDST, victim.h.kernel.getXdagState());
    }

    @Test
    public void blocksOnlyThisNodeHasAreNotFetchedAgainAndAgain() throws Exception {
        honest = node(18831, List.of());
        victim = node(18832, List.of(honest));
        buildHistory();
        // a block the honest node does not have, in a span where it has blocks: the sums differ for good
        Block extra = victim.h.link(ChainHarness.timeIn(5, 100), victim.key, "only here");
        victim.h.add(extra);
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, honest);

        victim.sync.syncOnce();
        victim.h.settle();
        assertNotNull(victim.h.chain.getBlockByHash(history.get(HISTORY - 2).getHashLow(), false));
        assertEquals(null, honest.h.chain.getBlockByHash(extra.getHashLow(), false));
        long fetched = victim.sync.getLeafRequests().get();
        assertTrue("the spans of the history were fetched", fetched >= 1);

        // the span with the extra block still differs, but the peer has nothing more to give for it
        victim.sync.syncOnce();
        victim.sync.syncOnce();
        assertEquals("a span is settled for a peer once it was fetched from it", fetched, victim.sync.getLeafRequests().get());

        // another block only this node has, in a span that was settled: the peer's sums did not change, so
        // nothing somebody sends to this node makes it ask the peer again
        victim.h.add(victim.h.link(ChainHarness.timeIn(7, 100), victim.key, "only here, later"));
        victim.sync.syncOnce();
        assertEquals(fetched, victim.sync.getLeafRequests().get());

        Thread.sleep(400);
        victim.sync.syncOnce();
        victim.syncMgr.checkState();
        assertTrue("differences of that kind do not keep the node from being synchronised",
                victim.syncMgr.getSyncDone().get());
    }

    // ------------------------------------------------------------------------------------------------------
    // sums that are made equal
    // ------------------------------------------------------------------------------------------------------

    private static final long LEAF = XdagSync.LEAF_SPAN;

    /** Sum and size of the request span that starts at {@code leaf}, as the node reports them to its peers. */
    private static long[] sums(NetNode node, long leaf) {
        long parent = leaf & -(LEAF << 4);
        MutableBytes sums = MutableBytes.create(256);
        node.h.kernel.getBlockStore().loadSum(parent, parent + (LEAF << 4), sums);
        int i = (int) ((leaf - parent) / LEAF);
        return new long[]{sums.getLong(i * 16, ByteOrder.LITTLE_ENDIAN), sums.getLong(i * 16 + 8, ByteOrder.LITTLE_ENDIAN)};
    }

    /** A chain on the honest node that reaches the present: one main block per epoch, up to two epochs ago. */
    private List<Block> buildChainToThePresent(int length) {
        long end = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
        List<Block> chain = new ArrayList<>();
        Bytes32 previous = null;
        for (int k = length + 1; k >= 2; k--) {
            long time = end - k * MAIN_CHAIN_PERIOD;
            Block b = previous == null ? honest.h.candidateAt(time, honest.key, "present-" + k)
                    : honest.h.candidateAt(time, honest.key, "present-" + k, previous);
            honest.h.add(b);
            chain.add(b);
            previous = Bytes32.wrap(b.getHashLow());
        }
        honest.h.settle();
        return chain;
    }

    /**
     * Blocks nobody needs, with timestamps in the span, as many as the victim has fewer there than the honest
     * node - and eight bytes of the remark of the last one make up for whatever is then missing to the honest
     * node's sum.
     */
    private List<Block> junkFor(long leaf) {
        long[] want = sums(honest, leaf);
        long[] have = sums(victim, leaf);
        int count = (int) ((want[1] - have[1]) / 512);
        List<Block> junk = new ArrayList<>();
        long sum = have[0];
        for (int j = 0; j < count; j++) {
            byte[] raw = attacker.h.link(leaf + 100 + j, attacker.key, "junk").getXdagBlock().getData().toArray();
            if (j == count - 1) {
                // (the remark is the second field; "junk" only takes its first bytes)
                assertEquals(0, ByteBuffer.wrap(raw, 40, 8).getLong());
                long missing = want[0] - sum - new XdagBlock(raw.clone()).getSum();
                ByteBuffer.wrap(raw, 40, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(missing);
            }
            Block b = new Block(new XdagBlock(raw));
            sum += b.getXdagBlock().getSum();
            junk.add(b);
        }
        return junk;
    }

    /**
     * The victim has the first {@code known} blocks of the honest node's chain; the attacker brings its sums to
     * those of the honest node in every request span but the two that are being written to.
     *
     * @return the spans in which the victim now has the honest node's sums and not its blocks
     */
    private List<Long> startWithSumsMadeEqual(List<Block> chain, int known) throws Exception {
        for (Block b : chain.subList(0, known)) {
            victim.h.add(b);
        }
        victim.h.settle();
        // the attacker would learn these sums by asking for them, as any peer can
        long current = XdagTime.getCurrentTimestamp() & -LEAF;
        List<Long> stuffed = new ArrayList<>();
        List<Block> junk = new ArrayList<>();
        for (long leaf = chain.getFirst().getTimestamp() & -LEAF; leaf < current - LEAF; leaf += LEAF) {
            if (!Arrays.equals(sums(honest, leaf), sums(victim, leaf))) {
                List<Block> blocks = junkFor(leaf);
                assertFalse(blocks.isEmpty());
                junk.addAll(blocks);
                stuffed.add(leaf);
            }
        }
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, honest, attacker);
        Channel toVictim = attacker.peer(victim);
        for (Block b : junk) {
            toVictim.getP2pHandler().sendMessage(new NewBlockMessage(b, 1));
        }
        await("the victim's sums to be those of the honest node", 20_000, () -> {
            for (long leaf : stuffed) {
                if (!Arrays.equals(sums(honest, leaf), sums(victim, leaf))) {
                    return false;
                }
            }
            return true;
        });
        // By the sums the victim now has what the honest node has in every span but the two that are being
        // written to - and it has none of the blocks.
        assertEquals(null, victim.h.chain.getBlockByHash(chain.get(known + 20).getHashLow(), false));
        return stuffed;
    }

    /**
     * The request spans, older than the two that are being written to, in which the honest node has blocks of
     * the chain that the victim was not given. (Not always the spans whose sums were made equal: of the last
     * block it was given the victim only knows, it has not stored it yet - where that block is the last of its
     * span, the sums of the span differed although the victim has every block in it.)
     */
    private static java.util.Set<Long> spansWithMissingBlocks(List<Block> chain, int known) {
        long current = XdagTime.getCurrentTimestamp() & -LEAF;
        java.util.Set<Long> spans = new java.util.TreeSet<>();
        // (the last block of the chain is still in the honest node's memory pool: it does not send it)
        for (Block b : chain.subList(known, chain.size() - 1)) {
            long leaf = b.getTimestamp() & -LEAF;
            if (leaf < current - LEAF) {
                spans.add(leaf);
            }
        }
        return spans;
    }

    /** The victim fetches the history although the sums are equal, and only then calls itself synchronised. */
    private void recoverAndSynchronise(List<Block> chain) throws Exception {
        // (no waiting for the parents that are asked for one by one: the test is about the spans)
        victim.sync.setSettleMs(0);
        victim.sync.syncOnce();
        Block tip = chain.get(chain.size() - 2); // the honest node's own tip is still in its memory pool
        for (Block b : chain.subList(0, chain.size() - 1)) {
            assertNotNull("history fetched although the sums were equal", victim.h.chain.getBlockByHash(b.getHashLow(), false));
        }
        victim.h.settle();
        assertArrayEquals(tip.getHashLow().toArray(), victim.h.chain.getXdagTopStatus().getTop());
        victim.syncMgr.checkState();
        assertFalse("still catching up a moment ago", victim.syncMgr.getSyncDone().get());

        Thread.sleep(400);
        victim.sync.syncOnce();
        assertEquals("the honest peer's newest blocks are part of the victim's blocks now", 1,
                victim.sync.getLastCycle().inSync());
        assertEquals(0, victim.sync.getLastCycle().behind());
        victim.syncMgr.checkState();
        assertTrue(victim.syncMgr.getSyncDone().get());
    }

    @Test
    public void blocksThatMakeTheSumsEqualDoNotHideMissingHistory() throws Exception {
        honest = node(18841, List.of());
        attacker = node(18842, List.of());
        victim = node(18843, List.of(honest, attacker));
        List<Block> chain = buildChainToThePresent(150);
        // the victim was synchronised some two and a half hours ago
        List<Long> stuffed = startWithSumsMadeEqual(chain, 10);
        assertTrue("the missing history covers several request spans", stuffed.size() >= 5);
        int missing = spansWithMissingBlocks(chain, 10).size();
        long fetchedBefore = victim.sync.getLeafRequests().get();

        recoverAndSynchronise(chain);
        long recovered = victim.sync.getRecoveredSpans().get();
        assertTrue("spans were fetched whatever the sums said: " + recovered + " of " + missing, recovered >= missing);
        assertEquals("... and the comparison had nothing to fetch", recovered, victim.sync.getLeafRequests().get() - fetchedBefore);
    }

    @Test
    public void whatIsFetchedAgainstTheSumsBeginsWhereTheNodesBlocksEnd() throws Exception {
        honest = node(18861, List.of());
        attacker = node(18862, List.of());
        victim = node(18863, List.of(honest, attacker));
        List<Block> chain = buildChainToThePresent(150);
        // the victim has the first two request spans of the chain completely, and a part of the third
        List<Long> stuffed = startWithSumsMadeEqual(chain, 40);
        assertTrue(stuffed.size() >= 4);
        int missing = spansWithMissingBlocks(chain, 40).size();

        recoverAndSynchronise(chain);
        long recovered = victim.sync.getRecoveredSpans().get();
        // (one more if the test happened to run across the beginning of a request span)
        assertTrue("every span with missing blocks was fetched: " + recovered + " of " + missing, recovered >= missing);
        assertTrue("and none of those the victim had completely: " + recovered + " for " + missing, recovered <= missing + 1);
    }

    @Test
    public void aNodeIsNotSynchronisedWhileAPeersNewestBlocksDoNotAttach() throws Exception {
        honest = node(18851, List.of());
        attacker = node(18852, List.of());
        victim = node(18853, List.of(honest, attacker));
        List<Block> chain = buildChainToThePresent(150);
        startWithSumsMadeEqual(chain, 10);

        // A round that has no time for anything but the comparison and the look at the present: the sums say
        // there is nothing to fetch, the blocks say otherwise, and the blocks decide.
        victim.sync.setSettleMs(0);
        victim.sync.setRoundTimeLimitMs(0);
        victim.sync.syncOnce();
        XdagSync.CycleResult cycle = victim.sync.getLastCycle();
        assertEquals(2, cycle.attempted());
        assertEquals("the honest peer has blocks the victim cannot attach", 1, cycle.behind());
        assertEquals(0, cycle.inSync());
        Thread.sleep(400);
        victim.syncMgr.checkState();
        assertFalse("equal sums do not make a node synchronised", victim.syncMgr.getSyncDone().get());
    }

    @Test
    public void blocksNobodyNeedsDoNotKeepANodeFromBeingSynchronised() throws Exception {
        honest = node(18871, List.of());
        victim = node(18872, List.of(honest));
        List<Block> chain = buildChainToThePresent(40);
        for (Block b : chain.subList(0, chain.size() - 1)) {
            victim.h.add(b);
        }
        victim.h.settle();
        // The honest node holds blocks from long ago that the victim does not have and nothing refers to (it
        // took them from somebody when it was synchronising itself). By the sums it has more than the victim.
        for (int e : new int[]{1, 40, 80, 120}) {
            honest.h.add(honest.h.link(ChainHarness.timeIn(e, 100), honest.key, "nobody needs this"));
        }
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, honest);

        // a round that is over before the comparison gets anywhere - as it is, round after round, for a node
        // whose peers are fed such blocks faster than it fetches them
        victim.sync.setRoundTimeLimitMs(0);
        victim.sync.syncOnce();
        assertEquals("nothing was fetched", 0, victim.sync.getLeafRequests().get());
        assertFalse("the comparison is not finished", victim.sync.lastRound(honest.channelMgr.getNodeKey().toBase58Address()).complete());
        // ... and yet the newest blocks of the peer are part of the victim's blocks: it has the chain
        assertEquals(1, victim.sync.getLastCycle().inSync());
        victim.syncMgr.checkState();
        assertTrue(victim.syncMgr.getSyncDone().get());
    }

    @Test
    public void theLoopsOfARunningNodeSynchroniseItAndSayWhenItIs() throws Exception {
        honest = node(18881, List.of());
        attacker = node(18882, List.of());
        victim = node(18883, List.of(honest, attacker));
        List<Block> chain = buildChainToThePresent(60);
        for (NetNode n : nodes) {
            n.start();
        }
        victim.awaitConnectedTo(30_000, honest, attacker);
        forgeRecentBlocks(chain.subList(0, 30));
        pushForgedBlocks();

        // nobody calls syncOnce() or checkState() here: the node's own loops do, only faster than in real life
        victim.h.kernel.setPow(org.mockito.Mockito.mock(XdagPow.class));
        victim.sync.setLoopInitialDelayMs(200);
        victim.sync.setLoopDelayMs(300);
        victim.syncMgr.setCheckStateDelayMs(100);
        victim.syncMgr.setCheckStatePeriodMs(100);
        victim.syncMgr.start();
        victim.sync.start();
        try {
            await("the victim to call itself synchronised", 60_000, () -> victim.syncMgr.getSyncDone().get());
            Block tip = chain.get(chain.size() - 2);
            assertNotNull("... which it only does with the history", victim.h.chain.getBlockByHash(tip.getHashLow(), false));
            assertEquals(XdagState.SDST, victim.h.kernel.getXdagState());
            victim.h.settle();
            assertArrayEquals(tip.getHashLow().toArray(), victim.h.chain.getXdagTopStatus().getTop());
            // (the state changes first, block production starts a moment later)
            org.mockito.Mockito.verify(victim.h.kernel.getPow(), org.mockito.Mockito.timeout(5_000)).start();
        } finally {
            victim.sync.stop();
            victim.syncMgr.stop();
        }
    }
}
