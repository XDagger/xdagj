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

import static io.xdag.config.Constants.MAIN_CHAIN_PERIOD;
import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;
import static io.xdag.config.Constants.REQUEST_WAIT;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.SettableFuture;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.core.AbstractXdagLifecycle;
import io.xdag.core.Block;
import io.xdag.core.XdagState;
import io.xdag.db.BlockStore;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.utils.XdagTime;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * Fetches the blocks this node lacks from its peers, and finds out where the node stands with each of them.
 *
 * <p>How this differs from 0.8.x, and why. The old loop only looked forward from the time of the node's latest
 * main block, stopped for good once the node called itself synchronised, and believed the sums: two nodes whose
 * sums for a span are equal were taken to hold the same blocks there. None of that survives peers that cannot be
 * trusted:
 * <ul>
 * <li>a node that is behind can be given a "latest main block" with a current timestamp by anybody, at no cost -
 *     a block without work on top of its stale tip is the heaviest thing it knows;</li>
 * <li>the sums are sums. Blocks made for the purpose bring the sums of a span this node has not got to exactly
 *     the values an honest peer reports for it, and nothing is fetched there any more.</li>
 * </ul>
 *
 * <p>The work is done in <em>rounds</em> with one peer at a time, in <em>cycles</em>: every peer that is connected
 * when a cycle begins gets one round in it (a peer that connects again and again under new names waits for the
 * next cycle like everybody else). Rounds never stop, also not when the node considers itself synchronised. A
 * round has up to three parts:
 * <ol>
 * <li><b>Compare.</b> The whole sums tree (16 sums per span, spans of 16^n request spans) is compared with the
 *     peer's, wherever the node's tip is, and the request spans in which the peer has more than this node are
 *     fetched, oldest first. That is the fast path, and all it takes among honest nodes. Nothing below the
 *     snapshot the node was booted from is asked for. A span that was fetched from a peer is settled for that
 *     peer while <em>the peer's</em> sums for it stay the same (this node never forgets a block, so nothing
 *     somebody else sends can undo that); spans in which this node already holds at least as much as the peer -
 *     which is what blocks only this node has look like, and anybody can send it such blocks - are looked into
 *     a few at a time.</li>
 * <li><b>Verify</b> (while the node is not synchronised, unless the round ran out of time with the peer's
 *     history still coming in). The two request spans that are being written to right now are fetched whatever
 *     the sums say. A block is only accepted when everything it refers to is there, so if all of them attach
 *     to this node's blocks, the node has the history behind the newest blocks of the peer. This cannot be
 *     faked with blocks that are not that history, and it does not wait for a comparison that is kept busy
 *     with blocks nobody needs.</li>
 * <li><b>Recover.</b> If they do not attach although the comparison found nothing to fetch, the sums are wrong
 *     somewhere. The node looks - without importing anything - at spans of the peer until it has found where
 *     its own blocks end (a span in which the peer has no block it lacks, followed by one in which it has), and
 *     fetches everything from there on, whatever the sums say.</li>
 * </ol>
 * What the rounds of a cycle found is what {@link SyncManager} decides on.
 */
@Slf4j
public class XdagSync extends AbstractXdagLifecycle {

    private static final ThreadFactory factory = BasicThreadFactory.builder()
            .namingPattern("XdagSync-thread-%d")
            .daemon(true)
            .build();

    /** Span of one blocks request, and of the smallest sums. */
    static final long LEAF_SPAN = REQUEST_BLOCKS_MAX_TIME;
    static final long ROOT_SPAN = 1L << 48;
    /** A peer that does not answer within the wait is not much of a peer; a few such silences and it is dropped. */
    static final int SCORE_NO_ANSWER = 25;
    /** Rounds per loop once the node is synchronised (before that: as many as fit into the loop). */
    static final int ROUNDS_PER_LOOP_WHEN_SYNCED = 2;
    /**
     * Request spans a synchronised node fetches from one peer in one round. Old blocks that arrive at such a node
     * are either nothing it needs or the first sign that it is not synchronised after all - and then it no longer
     * is, and the limit no longer applies.
     */
    static final int LEAVES_PER_ROUND_WHEN_SYNCED = 16;
    /** Spans looked into per round although this node holds at least as much there as the peer. */
    static final int BACKGROUND_PER_ROUND = 16;
    /** A peer that sent a block this young has shown what the present looks like where it is. */
    static final long FRESH_AGE = 8 * MAIN_CHAIN_PERIOD;
    private static final long SETTLED_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_TTL_MS = 10 * 60 * 1000L;
    private static final long RECENT_TTL_MS = 30 * 1000L;
    /** What a peer whose last round promised blocks and brought none gets to answer a request (normally {@code REQUEST_WAIT}). */
    private static final long UNPRODUCTIVE_SUMS_WAIT_MS = 10 * 1000L;
    private static final long UNPRODUCTIVE_BLOCKS_WAIT_MS = 30 * 1000L;

    private final ChannelManager channelMgr;
    private final BlockStore blockStore;
    private final ScheduledExecutorService sendTask;
    @Getter
    private final ConcurrentHashMap<Long, SettableFuture<Bytes>> sumsRequestMap;
    @Getter
    private final ConcurrentHashMap<Long, SettableFuture<Bytes>> blocksRequestMap;

    /** What {@link SyncManager} last decided; rounds run in either state. */
    @Getter
    @Setter
    private volatile Status status;

    private final Kernel kernel;
    private ScheduledFuture<?> sendFuture;

    /** When the first loop begins after the start, and the pause between loops. */
    @Setter
    private long loopInitialDelayMs = 32_000;
    @Setter
    private long loopDelayMs = 10_000;
    /** Longest a round with one peer may take, and the time one loop may spend on rounds. */
    @Setter
    private long roundTimeLimitMs = 120_000;
    @Setter
    private long loopTimeBudgetMs = 5_000;
    /** Longest a round may take with a peer whose last round promised blocks and brought none. */
    @Setter
    private long unproductiveRoundTimeLimitMs = 10_000;
    /** How long a round waits for the parents it asked the peer for, before it calls the peer's blocks unattached. */
    @Setter
    private long settleMs = 3_000;

    /** Spans that need no fetching from a peer while the peer's sums for them stay what they were. */
    private final Cache<SpanKey, Settled> settled = Caffeine.newBuilder().maximumSize(500_000)
            .expireAfterWrite(SETTLED_TTL_MS, TimeUnit.MILLISECONDS).build();
    /** Latest round with every peer, by peer id. */
    private final Map<String, RoundResult> rounds = new ConcurrentHashMap<>();
    /** Where to fetch from whatever the sums say, for the peers with which that was found necessary ({@link #recover}). */
    private final Map<String, Long> frontiers = new ConcurrentHashMap<>();
    /** The cycle that is under way (only the thread that runs the rounds touches it). */
    private Cycle cycle;
    /** The last cycle in which every peer had its round. */
    @Getter
    private volatile CycleResult lastCycle;
    /** Request spans fetched because of the comparison or the recovery since start (figures for tests and logs). */
    @Getter
    private final AtomicLong leafRequests = new AtomicLong();
    @Getter
    private final AtomicLong recoveredSpans = new AtomicLong();

    private record SpanKey(String peerId, long start, long span) {
    }

    /** @param fetched the span was fetched from the peer (and not just put off, or settled through its parts) */
    private record Settled(long sum, long size, long until, boolean fetched) {
    }

    private record Span(long start, long span) {
    }

    /** Where this node stands with a peer after a round. */
    public enum Verdict {
        /** The newest blocks the peer has are part of this node's blocks. */
        IN_SYNC,
        /** The peer has blocks this node lacks. */
        BEHIND,
        /** The round failed, or the peer showed nothing from the present: it is behind itself, or nobody produces blocks. */
        UNKNOWN
    }

    /**
     * @param compared the comparison of the sums got to its end
     * @param complete nothing was left to do with the peer
     * @param failed   the peer did not answer, or went away
     * @param fetched  request spans fetched
     * @param imported blocks the peer sent in the round that this node did not have
     */
    public record RoundResult(long startedAt, long finishedAt, boolean compared, boolean complete, boolean failed,
                              int fetched, int imported, Verdict verdict) {
    }

    /**
     * What the rounds of one cycle found.
     *
     * @param startedAt when the cycle began (wall clock, ms): every round in it is younger
     * @param attempted peers a round was held with
     * @param failed    rounds that failed
     * @param inSync    peers with verdict {@link Verdict#IN_SYNC}
     * @param behind    peers with verdict {@link Verdict#BEHIND}
     */
    public record CycleResult(long startedAt, long finishedAt, int attempted, int failed, int inSync, int behind) {
    }

    private static final class Cycle {
        final long startedAt = System.currentTimeMillis();
        final Deque<String> todo;
        int attempted;
        int failed;
        int inSync;
        int behind;

        Cycle(Collection<String> peers) {
            this.todo = new ArrayDeque<>(peers);
        }

        void count(RoundResult result) {
            attempted++;
            if (result.failed()) {
                failed++;
            } else if (result.verdict() == Verdict.IN_SYNC) {
                inSync++;
            } else if (result.verdict() == Verdict.BEHIND) {
                behind++;
            }
        }

        CycleResult result() {
            return new CycleResult(startedAt, System.currentTimeMillis(), attempted, failed, inSync, behind);
        }
    }

    /** One round with one peer. */
    private final class Round {
        final Channel peer;
        final String peerId;
        final long startedAt = System.currentTimeMillis();
        final long deadline;
        /** How long the peer may take to answer a sums request, and a blocks request (whose blocks are imported meanwhile). */
        final long sumsWaitMs;
        final long blocksWaitMs;
        /** The peer's sums as it gave them in this round. */
        final Map<Span, Bytes> sums = new HashMap<>();
        PeerEvidence evidence;
        boolean failed;
        /** Something was left for the next round: the time was up, or the limit of a synchronised node reached. */
        boolean more;
        int fetched;
        int background = BACKGROUND_PER_ROUND;

        Round(Channel peer, boolean unproductive) {
            this.peer = peer;
            this.peerId = peerIdOf(peer);
            this.deadline = startedAt + (unproductive ? unproductiveRoundTimeLimitMs : roundTimeLimitMs);
            long wait = REQUEST_WAIT * 1000;
            this.sumsWaitMs = unproductive ? Math.min(wait, UNPRODUCTIVE_SUMS_WAIT_MS) : wait;
            this.blocksWaitMs = unproductive ? Math.min(wait, UNPRODUCTIVE_BLOCKS_WAIT_MS) : wait;
        }

        boolean timeIsUp() {
            return System.currentTimeMillis() >= deadline;
        }
    }

    public XdagSync(Kernel kernel) {
        this.kernel = kernel;
        this.channelMgr = kernel.getChannelMgr();
        this.blockStore = kernel.getBlockStore();
        sendTask = new ScheduledThreadPoolExecutor(1, factory);
        sumsRequestMap = new ConcurrentHashMap<>();
        blocksRequestMap = new ConcurrentHashMap<>();
    }

    @Override
    protected void doStart() {
        if (status != Status.SYNCING) {
            status = Status.SYNCING;
            sendFuture = sendTask.scheduleWithFixedDelay(this::syncLoop, loopInitialDelayMs, loopDelayMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void doStop() {
        try {
            if (sendFuture != null) {
                sendFuture.cancel(true);
            }
            // Shutdown thread pool
            sendTask.shutdownNow();
            sendTask.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        log.debug("sync stop done");
    }

    private void syncLoop() {
        try {
            // while a peer still has history for us there is no reason to pause between loops
            while (syncOnce() && !Thread.currentThread().isInterrupted()) {
                log.trace("more history to fetch, next sync loop");
            }
        } catch (Throwable e) {
            // an exception that escapes would silently cancel the periodic task
            log.error("error in the sync loop: {}", e.toString(), e);
        }
    }

    private boolean synced() {
        SyncManager syncMgr = kernel.getSyncMgr();
        return syncMgr != null && syncMgr.getSyncDone().get();
    }

    /**
     * One loop: rounds of the cycle that is under way, until every peer in it has had its round or the time
     * budget of the loop is used up. While the node is not synchronised as many as fit into the budget,
     * afterwards a couple per loop (a round with a peer that has nothing new costs a handful of small requests).
     *
     * @return true if a round ended with spans still to be fetched from its peer
     */
    public boolean syncOnce() {
        long begin = System.currentTimeMillis();
        int limit = synced() ? ROUNDS_PER_LOOP_WHEN_SYNCED : Integer.MAX_VALUE;
        boolean more = false;
        for (int i = 0; i < limit && !Thread.currentThread().isInterrupted(); i++) {
            Channel peer = nextPeer();
            if (peer == null) {
                break;
            }
            RoundResult result = runRound(peer);
            cycle.count(result);
            more |= !result.failed() && !result.complete() && result.imported() > 0;
            if (System.currentTimeMillis() - begin >= loopTimeBudgetMs) {
                break;
            }
        }
        Map<String, Channel> peers = activePeers();
        if (cycle != null && cycle.todo.stream().noneMatch(peers::containsKey)) {
            // every peer of the cycle has had its round (or has gone)
            if (cycle.attempted > 0) {
                lastCycle = cycle.result();
                log.debug("sync cycle over: {}", lastCycle);
            }
            cycle = null;
        }
        rounds.keySet().retainAll(peers.keySet());
        frontiers.keySet().retainAll(peers.keySet());
        return more;
    }

    private static String peerIdOf(Channel channel) {
        return channel.getRemotePeer() != null && channel.getRemotePeer().getPeerId() != null
                ? channel.getRemotePeer().getPeerId() : String.valueOf(channel.getRemoteAddress());
    }

    private Map<String, Channel> activePeers() {
        Map<String, Channel> peers = new LinkedHashMap<>();
        for (Channel channel : getAnyNode()) {
            if (channel.isActive()) {
                peers.putIfAbsent(peerIdOf(channel), channel);
            }
        }
        return peers;
    }

    /**
     * The next peer of the cycle that is under way, null if nobody is left in it. A new cycle begins with the
     * peers that are connected at that moment, those whose last round is the oldest first.
     */
    private Channel nextPeer() {
        Map<String, Channel> peers = activePeers();
        if (cycle == null) {
            if (peers.isEmpty()) {
                return null;
            }
            List<String> order = new ArrayList<>(peers.keySet());
            order.sort(Comparator.comparingLong(id -> {
                RoundResult last = rounds.get(id);
                return last == null ? 0 : last.startedAt();
            }));
            cycle = new Cycle(order);
        }
        for (String id = cycle.todo.poll(); id != null; id = cycle.todo.poll()) {
            Channel peer = peers.get(id);
            if (peer != null) {
                return peer;
            }
        }
        return null;
    }

    /** The latest round held with a peer; null if there was none, or the peer has gone. */
    RoundResult lastRound(String peerId) {
        return rounds.get(peerId);
    }

    private RoundResult runRound(Channel peer) {
        RoundResult previous = rounds.get(peerIdOf(peer));
        // a peer that did not answer, or whose sums promised blocks for a whole round of which none was new,
        // gets less of this node's time
        boolean unproductive = previous != null && previous.imported() == 0
                && (previous.failed() || !previous.compared());
        Round round = new Round(peer, unproductive);
        SyncManager syncMgr = kernel.getSyncMgr();
        round.evidence = syncMgr == null ? null : syncMgr.watch(round.peerId);
        Verdict verdict = Verdict.UNKNOWN;
        boolean compared = false;
        try {
            descend(round, 0, ROOT_SPAN);
            compared = !round.failed && !round.more;
            if (!round.failed && round.evidence != null && !synced()) {
                if (round.more && syncMgr.getLastCatchUpTime() >= round.startedAt) {
                    // the time was up while the peer's history was coming in
                    verdict = Verdict.BEHIND;
                } else {
                    // Also when spans are left to fetch that did not move the chain: blocks nobody needs keep
                    // a comparison busy for as long as somebody keeps making them, and that must not be what
                    // decides whether the node is synchronised.
                    verdict = verify(round);
                }
            }
        } finally {
            if (syncMgr != null) {
                syncMgr.unwatch(round.peerId);
            }
        }
        boolean complete = !round.failed && !round.more;
        int imported = round.evidence == null ? 0 : round.evidence.imported();
        RoundResult result = new RoundResult(round.startedAt, System.currentTimeMillis(), compared, complete,
                round.failed, round.fetched, imported, round.failed ? Verdict.UNKNOWN : verdict);
        rounds.put(round.peerId, result);
        log.debug("sync round with {}: {}", peer.getRemoteAddress(), result);
        return result;
    }

    /** Snapshot this node was booted from: nothing below it is accepted, so nothing below it is asked for. */
    private long floor() {
        return kernel.getConfig().getSnapshotSpec().isSnapshotEnabled()
                ? kernel.getConfig().getSnapshotSpec().getSnapshotTime() : 0;
    }

    /** The request span that contains now, and the one before it: they differ between any two nodes at any time. */
    private static boolean isRecent(long start) {
        return start + LEAF_SPAN > XdagTime.getCurrentTimestamp() - LEAF_SPAN;
    }

    private static long sumOf(Bytes sums, int i) {
        return sums.getLong(i * 16, ByteOrder.LITTLE_ENDIAN);
    }

    private static long sizeOf(Bytes sums, int i) {
        return sums.getLong(i * 16 + 8, ByteOrder.LITTLE_ENDIAN);
    }

    // ---------------------------------------------------------------------------------------------------------
    // compare
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Compares the 16 parts of a span with the peer and deals with those that differ, oldest first.
     *
     * @return until when (wall clock, ms) nothing below this span needs attention as long as the peer's sums stay
     *         the same; 0 if something below it is still open
     */
    private long descend(Round round, long t, long span) {
        MutableBytes local = MutableBytes.create(256);
        if (blockStore.loadSum(t, t + span, local) <= 0) {
            return Long.MAX_VALUE;
        }
        Bytes remote = remoteSums(round, t, span);
        if (remote == null) {
            return 0;
        }
        long child = span >> 4;
        long floor = floor();
        long until = Long.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            long lSum = sumOf(local, i);
            long lSize = sizeOf(local, i);
            long rSum = sumOf(remote, i);
            long rSize = sizeOf(remote, i);
            if (lSum == rSum && lSize == rSize) {
                continue;
            }
            if (rSum == 0 && rSize == 0) {
                // the peer has nothing there
                continue;
            }
            long start = t + i * child;
            if (start + child <= floor) {
                continue;
            }
            long now = System.currentTimeMillis();
            SpanKey key = new SpanKey(round.peerId, start, child);
            Settled known = settled.getIfPresent(key);
            if (known != null && known.sum() == rSum && known.size() == rSize && known.until() > now) {
                until = Math.min(until, known.until());
                continue;
            }
            if (child == LEAF_SPAN && isRecent(start)) {
                // A synchronised node gets these blocks as news; one that is not fetches both spans when it
                // verifies. Fetching them here from every peer would move the same blocks again and again.
                until = Math.min(until, now + RECENT_TTL_MS);
                continue;
            }
            if (round.timeIsUp() || (round.fetched >= LEAVES_PER_ROUND_WHEN_SYNCED && synced())) {
                round.more = true;
                return 0;
            }
            if (Long.compareUnsigned(rSize, lSize) <= 0) {
                // This node holds at least as much there as the peer. The usual reason is blocks only this node
                // has, and anybody can send it such blocks, to any span; that must not make it fetch its whole
                // history again from every peer. A few of these spans are looked into per round - the peer may
                // still have a block this node lacks - and the rest waits. (Should history hide there: verifying
                // does not depend on the sums.)
                if (round.background <= 0) {
                    until = Math.min(until, now + RETRY_TTL_MS);
                    continue;
                }
                round.background--;
            }
            long childUntil;
            if (child > LEAF_SPAN) {
                childUntil = descend(round, start, child);
                if (round.failed) {
                    return 0;
                }
                if (childUntil > 0) {
                    childUntil = Math.min(childUntil, now + SETTLED_TTL_MS);
                    settled.put(key, new Settled(rSum, rSize, childUntil, false));
                }
            } else {
                childUntil = fetch(round, key, i, t, rSum, rSize);
                if (round.failed) {
                    return 0;
                }
            }
            until = Math.min(until, childUntil);
        }
        return until;
    }

    /** The peer's sums of a span, asked once per round. */
    private Bytes remoteSums(Round round, long start, long span) {
        Span key = new Span(start, span);
        Bytes sums = round.sums.get(key);
        if (sums == null && !round.failed) {
            sums = requestSums(round, start, start + span);
            if (sums == null) {
                round.failed = true;
            } else {
                round.sums.put(key, sums);
            }
        }
        return sums;
    }

    /**
     * Fetches one request span from the peer. The reply follows the blocks on the same connection, so when it
     * is here everything the peer sent has been imported (or put aside to wait for a parent).
     *
     * @return until when the span is settled for this peer
     */
    private long fetch(Round round, SpanKey key, int index, long parentStart, long rSum, long rSize) {
        setSyncOldIfWaiting();
        if (!requestBlocks(round, key.start(), key.start() + LEAF_SPAN)) {
            round.failed = true;
            return 0;
        }
        round.fetched++;
        leafRequests.incrementAndGet();

        MutableBytes local = MutableBytes.create(256);
        long lSize = blockStore.loadSum(parentStart, parentStart + (LEAF_SPAN << 4), local) <= 0 ? 0
                : sizeOf(local, index);
        long now = System.currentTimeMillis();
        // Settled if this node now holds at least as much as the peer has there. If it holds less, something the
        // peer sent was not kept (waiting for a parent, or not valid here): look again in a while.
        long ttl = Long.compareUnsigned(lSize, rSize) >= 0 ? SETTLED_TTL_MS : RETRY_TTL_MS;
        settled.put(key, new Settled(rSum, rSize, now + ttl, true));
        return now + ttl;
    }

    /** "Connected, synchronising from low to high": gossip is not processed while history is being fetched. */
    private void setSyncOldIfWaiting() {
        SyncManager syncMgr = kernel.getSyncMgr();
        if (syncMgr != null && !syncMgr.isSyncOld() && !syncMgr.isSync() && !syncMgr.getSyncDone().get()) {
            log.debug("set sync old");
            setSyncOld();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // verify, recover
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Fetches the two request spans that are being written to, whatever the sums say, and sees whether the
     * blocks in them attach to this node's blocks; if they do not although the comparison found nothing to
     * fetch, {@link #recover}.
     */
    private Verdict verify(Round round) {
        long current = XdagTime.getCurrentTimestamp() & -LEAF_SPAN;
        long floor = floor();
        for (long start = current - LEAF_SPAN; start <= current; start += LEAF_SPAN) {
            if (start < 0 || start + LEAF_SPAN <= floor) {
                continue;
            }
            if (!requestBlocks(round, start, start + LEAF_SPAN)) {
                round.failed = true;
                return Verdict.UNKNOWN;
            }
        }
        if (!attached(round)) {
            if (round.more) {
                // the comparison still has spans to fetch: first those
                return Verdict.BEHIND;
            }
            recover(round, current);
            if (round.failed) {
                return Verdict.UNKNOWN;
            }
            if (!attached(round)) {
                if (!round.more) {
                    // everything from there to the present was fetched and it did not help: look again next time
                    frontiers.remove(round.peerId);
                }
                return Verdict.BEHIND;
            }
        }
        frontiers.remove(round.peerId);
        return round.evidence.newest() >= XdagTime.getCurrentTimestamp() - FRESH_AGE ? Verdict.IN_SYNC
                : Verdict.UNKNOWN;
    }

    /**
     * Whether every block the peer sent in this round that needed a parent has found it. The parents were asked
     * for when the blocks arrived; they get a moment to arrive.
     */
    private boolean attached(Round round) {
        long end = System.currentTimeMillis() + settleMs;
        while (!round.evidence.connected(kernel.getBlockchain())) {
            if (round.evidence.overflowed() || System.currentTimeMillis() >= end || !round.peer.isActive()) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * The comparison found nothing to fetch from the peer, and still its newest blocks need blocks this node
     * does not have: for some span the sums are equal (or this node holds more) although blocks are missing.
     *
     * <p>Finds, by looking at spans of the peer without importing them, a span in which the peer has blocks and
     * none that this node lacks, followed by one in which it has: first further and further back from the
     * present, then halving what lies between. The blocks of the first of the two are part of this node's
     * blocks, and with them everything they rest on - so nothing older is needed to attach what comes after -
     * and every span from the second one to the present is fetched whatever the sums say.
     *
     * @param current start of the request span that contains now
     */
    private void recover(Round round, long current) {
        Long known = frontiers.get(round.peerId);
        long from = known != null && known < current - LEAF_SPAN ? known : findFrontier(round, current);
        if (from < 0) {
            return;
        }
        frontiers.put(round.peerId, from);
        log.debug("the sums of {} hide missing blocks: fetching everything from {}", round.peer.getRemoteAddress(), from);
        for (long leaf = seek(round, 0, ROOT_SPAN, from, true); leaf >= 0 && leaf < current - LEAF_SPAN;
                leaf = seek(round, 0, ROOT_SPAN, leaf + LEAF_SPAN, true)) {
            if (round.timeIsUp()) {
                round.more = true;
                return;
            }
            long parent = leaf & -(LEAF_SPAN << 4);
            int index = (int) ((leaf - parent) / LEAF_SPAN);
            Bytes remote = remoteSums(round, parent, LEAF_SPAN << 4);
            if (remote == null) {
                return;
            }
            SpanKey key = new SpanKey(round.peerId, leaf, LEAF_SPAN);
            Settled settledSpan = settled.getIfPresent(key);
            if (settledSpan != null && settledSpan.fetched() && settledSpan.sum() == sumOf(remote, index)
                    && settledSpan.size() == sizeOf(remote, index) && settledSpan.until() > System.currentTimeMillis()) {
                // fetched from this very peer before, and it has nothing new there
                continue;
            }
            fetch(round, key, index, parent, sumOf(remote, index), sizeOf(remote, index));
            if (round.failed) {
                return;
            }
            recoveredSpans.incrementAndGet();
        }
    }

    /**
     * Where this node's blocks end as far as the peer's are concerned: the start of the first request span to
     * fetch (see {@link #recover}); -1 if the round failed or its time was up before that was found.
     */
    private long findFrontier(Round round, long current) {
        // lo: start of a span of which this node has every block the peer has (none known yet: below the floor)
        // hi: start of the oldest span known to hold blocks this node lacks (so far: the older one just fetched)
        long lo = (Math.max(floor(), 0) & -LEAF_SPAN) - LEAF_SPAN;
        long hi = current - LEAF_SPAN;
        boolean found = false;
        for (long step = LEAF_SPAN; !found && hi - step > lo; step <<= 1) {
            if (round.timeIsUp()) {
                round.more = true;
                return -1;
            }
            long leaf = seek(round, 0, ROOT_SPAN, hi - step, false);
            if (round.failed) {
                return -1;
            }
            if (leaf < 0 || leaf <= lo) {
                // the peer has nothing that far back
                break;
            }
            PeerEvidence.Peek peek = peek(round, leaf);
            if (peek == null) {
                return -1;
            }
            if (peek.blocks() > 0 && peek.unknown() == 0) {
                lo = leaf;
                found = true;
            } else {
                hi = leaf;
            }
        }
        while (true) {
            if (round.timeIsUp()) {
                round.more = true;
                return -1;
            }
            long leaf = spanBetween(round, lo, hi);
            if (round.failed) {
                return -1;
            }
            if (leaf < 0) {
                return hi;
            }
            PeerEvidence.Peek peek = peek(round, leaf);
            if (peek == null) {
                return -1;
            }
            if (peek.blocks() > 0 && peek.unknown() == 0) {
                lo = leaf;
            } else {
                hi = leaf;
            }
        }
    }

    /**
     * Start of a request span strictly between two others in which the peer has blocks by its sums, as close to
     * the middle as there is one; -1 if there is none.
     */
    private long spanBetween(Round round, long lo, long hi) {
        long middle = lo + (hi - lo) / LEAF_SPAN / 2 * LEAF_SPAN;
        long leaf = middle < 0 ? -1 : seek(round, 0, ROOT_SPAN, middle, false);
        if ((leaf < 0 || leaf <= lo) && !round.failed) {
            leaf = seek(round, 0, ROOT_SPAN, Math.max(middle, lo) + LEAF_SPAN, true);
        }
        return round.failed || leaf < 0 || leaf <= lo || leaf >= hi ? -1 : leaf;
    }

    /**
     * Start of the last request span at or before {@code bound} (backward), or of the first one at or after it
     * (forward), in which the peer has blocks by its sums; -1 if there is none within the span.
     */
    private long seek(Round round, long t, long span, long bound, boolean forward) {
        Bytes remote = remoteSums(round, t, span);
        if (remote == null) {
            return -1;
        }
        long child = span >> 4;
        for (int n = 0; n < 16; n++) {
            int i = forward ? n : 15 - n;
            if (sumOf(remote, i) == 0 && sizeOf(remote, i) == 0) {
                continue;
            }
            long start = t + i * child;
            if (forward ? start + child <= bound : start > bound) {
                continue;
            }
            if (child == LEAF_SPAN) {
                return start;
            }
            long found = seek(round, start, child, bound, forward);
            if (found >= 0 || round.failed) {
                return found;
            }
        }
        return -1;
    }

    /** Has the peer send a request span and counts the blocks in it that this node lacks; nothing is imported. */
    private PeerEvidence.Peek peek(Round round, long start) {
        boolean answered;
        PeerEvidence.Peek peek;
        round.evidence.beginPeek(start, start + LEAF_SPAN);
        try {
            answered = requestBlocks(round, start, start + LEAF_SPAN);
        } finally {
            peek = round.evidence.endPeek();
        }
        if (!answered) {
            round.failed = true;
            return null;
        }
        return peek;
    }

    // ---------------------------------------------------------------------------------------------------------
    // requests
    // ---------------------------------------------------------------------------------------------------------

    /** Waits until our own request limit for the peer lets one more request through (null: it never did). */
    private Long send(Round round, LongSupplier request) {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (!round.peer.isActive()) {
                return null;
            }
            long seq = request.getAsLong();
            if (seq >= 0) {
                return seq;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private Bytes requestSums(Round round, long start, long end) {
        // the future is registered before the request leaves: the answer may be back before this thread is
        Long seq = send(round, () -> round.peer.getP2pHandler().sendGetSums(start, end, SettableFuture.create()));
        if (seq == null) {
            return null;
        }
        SettableFuture<Bytes> sf = sumsRequestMap.get(seq);
        try {
            Bytes sums = sf == null ? null : sf.get(round.sumsWaitMs, TimeUnit.MILLISECONDS);
            return sums == null || sums.size() != 256 ? null : sums.copy();
        } catch (TimeoutException e) {
            log.debug("Peer {} did not answer a sums request in {} ms", round.peer.getRemoteAddress(), round.sumsWaitMs);
            round.peer.getP2pHandler().misbehave(SCORE_NO_ANSWER, "no answer to a sums request");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.debug("sums request failed: {}", e.toString());
            return null;
        } finally {
            sumsRequestMap.remove(seq);
        }
    }

    private boolean requestBlocks(Round round, long start, long end) {
        Long seq = send(round, () -> round.peer.getP2pHandler().sendGetBlocks(start, end, SettableFuture.create()));
        if (seq == null) {
            return false;
        }
        SettableFuture<Bytes> sf = blocksRequestMap.get(seq);
        try {
            if (sf != null) {
                sf.get(round.blocksWaitMs, TimeUnit.MILLISECONDS);
            }
            return sf != null;
        } catch (TimeoutException e) {
            log.debug("Peer {} did not answer a blocks request in {} ms", round.peer.getRemoteAddress(), round.blocksWaitMs);
            round.peer.getP2pHandler().misbehave(SCORE_NO_ANSWER, "no answer to a blocks request");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.debug("blocks request failed: {}", e.toString());
            return false;
        } finally {
            blocksRequestMap.remove(seq);
        }
    }

    public void setSyncOld() {
        Config config = kernel.getConfig();
        if (config instanceof MainnetConfig) {
            if (kernel.getXdagState() != XdagState.CONNP) {
                kernel.setXdagState(XdagState.CONNP);
            }
        } else if (config instanceof TestnetConfig) {
            if (kernel.getXdagState() != XdagState.CTSTP) {
                kernel.setXdagState(XdagState.CTSTP);
            }
        } else if (config instanceof DevnetConfig) {
            if (kernel.getXdagState() != XdagState.CDSTP) {
                kernel.setXdagState(XdagState.CDSTP);
            }
        }
    }

    /**
     * Get timestamp of latest confirmed main block
     */
    public long getLastTime() {
        long height = blockStore.getXdagStatus().nmain;
        if(height == 0) return 0;
        Block lastBlock = blockStore.getBlockByHeight(height);
        if (lastBlock != null) {
            return lastBlock.getTimestamp();
        }
        return 0;
    }

    public List<Channel> getAnyNode() {
        return channelMgr.getActiveChannels();
    }

    public enum Status {
        /**
         * Sync states
         */
        SYNCING, SYNC_DONE
    }
}
