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

import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.BI_REMARK;
import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;
import static io.xdag.config.Constants.REQUEST_WAIT;

import com.google.common.util.concurrent.SettableFuture;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagStats;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.net.message.MessageException;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.consensus.BlockExtRequestMessage;
import io.xdag.net.message.consensus.BlockRequestMessage;
import io.xdag.net.message.consensus.BlocksReplyMessage;
import io.xdag.net.message.consensus.BlocksRequestMessage;
import io.xdag.net.message.consensus.NewBlockMessage;
import io.xdag.net.message.consensus.SumReplyMessage;
import io.xdag.net.message.consensus.SumRequestMessage;
import io.xdag.net.message.consensus.SyncBlockMessage;
import io.xdag.net.message.consensus.SyncBlockRequestMessage;
import io.xdag.net.message.consensus.XdagMessage;
import io.xdag.utils.XdagTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;

/**
 * The XDAG protocol over one connection: block gossip, block requests and the sums-based history sync.
 *
 * <p>Nothing a peer sends is taken on trust. Messages are decoded with bounds; requests are checked for the
 * shapes the protocol produces (a sums request covers a power-of-sixteen span, a blocks request at most
 * {@link io.xdag.config.Constants#REQUEST_BLOCKS_MAX_TIME}) and rate limited, so that no peer can make this node
 * read its whole history for it; statistics a peer reports about the network are capped at what is physically
 * possible; history is only taken in answer to a request - "sync blocks" always, and news that is dated more than
 * {@link #NEWS_MAX_AGE} ago; a peer that sends what only a broken or hostile node would send is disconnected and
 * its address refused for a while.
 */
@Slf4j
public class XdagP2pHandler {

    /** Misbehaviour score at which the peer is dropped and banned. */
    static final int BAN_SCORE = 100;
    static final long BAN_TIME_MS = 10 * 60 * 1000L;
    static final int SCORE_MALFORMED = 100;
    static final int SCORE_INVALID_BLOCK = 20;
    static final int SCORE_BAD_REQUEST = 20;
    static final int SCORE_RATE = 5;
    /** Most blocks streamed for one blocks request. */
    static final int MAX_BLOCKS_PER_REQUEST = 65536;
    /** How long the answer to a request counts as one (twice what the sync waits for it: late is still an answer). */
    static final long ANSWER_WAIT_MS = 2 * REQUEST_WAIT * 1000;
    /** Blocks asked for by hash that are remembered at a time. */
    static final int MAX_ASKED_BLOCKS = 4096;
    /**
     * How old a block may be to be taken as news (about nine hours; the same span within which a node that is
     * catching up begins to process news at all).
     */
    static final long NEWS_MAX_AGE = 32 * REQUEST_BLOCKS_MAX_TIME;

    private final Channel channel;
    private final Kernel kernel;
    private final Config config;
    private final Blockchain chain;
    private final SyncManager syncMgr;
    private final MessageFactory messageFactory = new MessageFactory();

    /** Requests that cost this node database work (blocks / sums / single blocks). */
    private final TokenBucket requestLimit = new TokenBucket(32, 16);
    /** Blocks the peer pushes as news (gossip); what it sends in answer to our sync requests is bounded by bandwidth. */
    private final TokenBucket gossipLimit = new TokenBucket(5_000, 1_000);
    private final TokenBucket syncBlockLimit = new TokenBucket(20_000, 4_000);
    /**
     * Requests we send to this peer, kept below what a node of this version accepts ({@link #requestLimit}) so
     * that a flood of orphan blocks from somebody else cannot make this node pester its peers until they ban it.
     */
    private final TokenBucket outboundRequestLimit = new TokenBucket(24, 12);
    /** Blocks requests sent to the peer that it has not answered yet, by request id: start, end, until when. */
    private final Map<Long, long[]> askedSpans = new ConcurrentHashMap<>();
    /** Blocks the peer was asked for by hash, and until when the answer counts. */
    private final Map<Bytes32, Long> askedBlocks = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Bytes32, Long> eldest) {
            return size() > MAX_ASKED_BLOCKS;
        }
    });
    @Getter
    private volatile int misbehaviourScore;

    public XdagP2pHandler(Channel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.chain = kernel.getBlockchain();
        this.syncMgr = kernel.getSyncMgr();
    }

    // ---------------------------------------------------------------------------------------------------------
    // inbound
    // ---------------------------------------------------------------------------------------------------------

    /** A message from the peer as the P2P layer delivers it: {@code [code | body]}. */
    public void onMessage(Bytes data) {
        if (!channel.isActive()) {
            return;
        }
        if (data == null || data.isEmpty()) {
            misbehave(SCORE_MALFORMED, "empty message");
            return;
        }
        Message msg;
        try {
            msg = messageFactory.create(data.get(0), data.slice(1).toArray());
        } catch (MessageException e) {
            misbehave(SCORE_MALFORMED, "malformed message: " + e.getMessage());
            return;
        }
        if (msg == null) {
            misbehave(SCORE_MALFORMED, "unknown message code " + data.get(0));
            return;
        }
        log.trace("Received message: {} from {}", msg, channel.getRemoteAddress());
        try {
            switch (msg.getCode()) {
                case NEW_BLOCK -> processNewBlock((NewBlockMessage) msg);
                case SYNC_BLOCK -> processSyncBlock((SyncBlockMessage) msg);
                case BLOCK_REQUEST -> processBlockRequest((BlockRequestMessage) msg);
                case SYNCBLOCK_REQUEST -> processSyncBlockRequest((SyncBlockRequestMessage) msg);
                case BLOCKS_REQUEST -> processBlocksRequest((BlocksRequestMessage) msg);
                case BLOCKS_REPLY -> processBlocksReply((BlocksReplyMessage) msg);
                case SUMS_REQUEST -> processSumsRequest((SumRequestMessage) msg);
                case SUMS_REPLY -> processSumsReply((SumReplyMessage) msg);
                case BLOCKEXT_REQUEST -> processBlockExtRequest((BlockExtRequestMessage) msg);
                case BLOCKEXT_REPLY -> {
                    // not used
                }
            }
        } catch (RuntimeException e) {
            // a failure while handling one message must not take the connection or the node down
            log.warn("Handling {} from {} failed: {}", msg.getCode(), channel.getRemoteAddress(), e.toString());
            log.debug("Handling failed", e);
        }
    }

    /**
     * Notes that the peer did something no correct node does. Beyond {@link #BAN_SCORE} it is dropped and its
     * address refused for {@link #BAN_TIME_MS}.
     */
    public void misbehave(int score, String why) {
        misbehaviourScore += score;
        log.debug("Peer {} misbehaves ({}): {}, score {}", channel.getRemoteAddress(), why, score, misbehaviourScore);
        if (misbehaviourScore >= BAN_SCORE) {
            log.info("Disconnecting peer {}: {}", channel.getRemoteAddress(), why);
            channel.ban(BAN_TIME_MS);
        }
    }

    private boolean tooFast(TokenBucket bucket, String what) {
        if (bucket.tryAcquire(1)) {
            return false;
        }
        misbehave(SCORE_RATE, "too many " + what);
        return true;
    }

    /** The hops a relayed block may still travel: what the peer said, minus one, never more than our own TTL. */
    private int relayTtl(int received) {
        return Math.max(0, Math.min(received - 1, config.getNodeSpec().getTTL()));
    }

    protected void processNewBlock(NewBlockMessage msg) {
        if (tooFast(gossipLimit, "new blocks")) {
            return;
        }
        Block block = msg.getBlock();
        if (syncMgr.isSyncOld()) {
            return;
        }
        if (block.getTimestamp() < XdagTime.getCurrentTimestamp() - NEWS_MAX_AGE && !askedFor(block)) {
            // News is new. A block dated long ago that nobody asked for is not taken, and so not passed on
            // either: otherwise anybody could add blocks to any part of the past of every node, and every node
            // that compares its history with a peer would have to fetch those parts again. (A block that is
            // needed after all - something refers to it - is asked for by its hash, and then it is welcome.)
            log.debug("Ignoring new block {} from {}: dated {} and not asked for", block.getHashLow(),
                    channel.getRemoteAddress(), block.getTimestamp());
            return;
        }
        log.debug("processNewBlock:{} from node {}", block.getHashLow(), channel.getRemoteAddress());
        BlockWrapper bw = new BlockWrapper(block, relayTtl(msg.getTtl()), channel.getRemotePeer(), false);
        noteImport(syncMgr.validateAndAddNewBlock(bw));
    }

    protected void processSyncBlock(SyncBlockMessage msg) {
        if (tooFast(syncBlockLimit, "sync blocks")) {
            return;
        }
        Block block = msg.getBlock();
        if (!askedFor(block)) {
            // History is sent in answer to a request and in no other way. A node that took whatever is pushed at
            // it as history would let anybody fill its past with blocks, thousands a second.
            log.debug("Ignoring sync block {} from {}: nobody asked for it", block.getHashLow(), channel.getRemoteAddress());
            return;
        }
        // What the peer says about the execution of the block is only a hint (ignored under the hardened
        // rules: a node reconstructs execution itself).
        chain.putSyncTxStatus(block.getHashLow(), msg.getExecutionState());
        log.debug("processSyncBlock:{}  from node {}", block.getHashLow(), channel.getRemoteAddress());
        BlockWrapper bw = new BlockWrapper(block, relayTtl(msg.getTtl()), channel.getRemotePeer(), true);
        noteImport(syncMgr.validateAndAddNewBlock(bw));
    }

    /** Whether a block the peer sends answers a request of this node: for the block itself, or for its span. */
    private boolean askedFor(Block block) {
        long now = System.currentTimeMillis();
        Long until = askedBlocks.remove(Bytes32.wrap(block.getHashLow().toArray()));
        if (until != null && until > now) {
            return true;
        }
        long time = block.getTimestamp();
        for (long[] span : askedSpans.values()) {
            if (span[2] > now && time >= span[0] && time < span[1]) {
                return true;
            }
        }
        return false;
    }

    private void noteImport(ImportResult result) {
        if (result != null && result.isMisbehavior()) {
            misbehave(SCORE_INVALID_BLOCK, "invalid block: " + result.getErrorInfo());
        }
    }

    protected void processBlocksRequest(BlocksRequestMessage msg) {
        if (tooFast(requestLimit, "requests")) {
            return;
        }
        updateXdagStats(msg);
        long startTime = msg.getStarttime();
        long endTime = msg.getEndtime();
        long random = msg.getRandom();

        // A correct node asks for one span of at most REQUEST_BLOCKS_MAX_TIME at a time (XdagSync). Anything
        // wider is not answered: it would have this node read and send an unbounded part of its history.
        if (startTime < 0 || endTime < startTime || endTime - startTime > REQUEST_BLOCKS_MAX_TIME) {
            misbehave(SCORE_BAD_REQUEST, "blocks request out of bounds");
            return;
        }

        log.debug("Send blocks between {} and {} to node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getRemoteAddress());
        List<Block> blocks = chain.getBlocksByTime(startTime, endTime);
        int sent = 0;
        for (Block block : blocks) {
            if (sent++ >= MAX_BLOCKS_PER_REQUEST || !channel.isActive()) {
                break;
            }
            sendMessage(new SyncBlockMessage(block, 1, executionStateOf(block)));
        }
        sendMessage(new BlocksReplyMessage(startTime, endTime, random, chain.getXdagStats()));
    }

    private byte executionStateOf(Block block) {
        byte executionState = 0;
        if (chain.isTxBlock(block)) {
            int flag = block.getInfo().getFlags() & ~(BI_OURS | BI_REMARK);
            if (flag == (BI_REF | BI_MAIN_REF | BI_APPLIED)) {
                executionState = 1;
            } else if (flag == (BI_REF | BI_MAIN_REF)) {
                executionState = 2;
            }
        }
        return executionState;
    }

    protected void processBlocksReply(BlocksReplyMessage msg) {
        updateXdagStats(msg);
        askedSpans.remove(msg.getRandom());
        SettableFuture<Bytes> sf = kernel.getSync().getBlocksRequestMap().get(msg.getRandom());
        if (sf != null) {
            sf.set(Bytes.wrap(new byte[]{0}));
        }
    }

    protected void processSumsRequest(SumRequestMessage msg) {
        if (tooFast(requestLimit, "requests")) {
            return;
        }
        updateXdagStats(msg);
        long start = msg.getStarttime();
        long span = msg.getEndtime() - msg.getStarttime();
        // The sync asks for spans of 16^n * REQUEST_BLOCKS_MAX_TIME up to 2^48; the store only answers powers
        // of two anyway. (A span of 2^63 used to send the store into an endless loop.)
        if (start < 0 || span < REQUEST_BLOCKS_MAX_TIME || span > (1L << 48) || Long.bitCount(span) != 1
                || Long.numberOfTrailingZeros(span) % 4 != 0) {
            misbehave(SCORE_BAD_REQUEST, "sums request out of bounds");
            return;
        }
        MutableBytes sums = MutableBytes.create(256);
        kernel.getBlockStore().loadSum(msg.getStarttime(), msg.getEndtime(), sums);
        sendMessage(new SumReplyMessage(msg.getEndtime(), msg.getRandom(), chain.getXdagStats(), sums));
    }

    protected void processSumsReply(SumReplyMessage msg) {
        updateXdagStats(msg);
        SettableFuture<Bytes> sf = kernel.getSync().getSumsRequestMap().get(msg.getRandom());
        if (sf != null) {
            sf.set(msg.getSum());
        }
    }

    protected void processBlockExtRequest(BlockExtRequestMessage msg) {
    }

    protected void processBlockRequest(BlockRequestMessage msg) {
        if (tooFast(requestLimit, "requests")) {
            return;
        }
        Bytes32 hash = msg.getHash();
        Block block = chain.getBlockByHash(hash, true);
        if (block != null) {
            log.debug("processBlockRequest: findBlock{}", hash.toHexString());
            sendMessage(new NewBlockMessage(block, config.getNodeSpec().getTTL()));
        }
    }

    private void processSyncBlockRequest(SyncBlockRequestMessage msg) {
        if (tooFast(requestLimit, "requests")) {
            return;
        }
        Bytes32 hash = msg.getHash();
        Block block = chain.getBlockByHash(hash, true);
        if (block != null) {
            log.debug("processSyncBlockRequest, findBlock: {}, to node: {}", hash.toHexString(), channel.getRemoteAddress());
            sendMessage(new SyncBlockMessage(block, 1, executionStateOf(block)));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // outbound
    // ---------------------------------------------------------------------------------------------------------

    public void sendNewBlock(Block newBlock, int ttl) {
        log.debug("send block:{} to node:{}", newBlock.getHashLow(), channel.getRemoteAddress());
        sendMessage(new NewBlockMessage(newBlock, ttl));
    }

    /** Whether one more request may be sent to this peer right now. */
    public boolean mayRequest() {
        return outboundRequestLimit.tryAcquire(1);
    }

    public long sendGetBlocks(long startTime, long endTime) {
        return sendGetBlocks(startTime, endTime, null);
    }

    /**
     * Asks the peer for the blocks of a span.
     *
     * @param reply completed when the peer's reply arrives; registered before the request is sent, so that a
     *              reply that is back before the caller continues is not lost (null: nobody waits)
     * @return the id of the request, or -1 if no request may be sent to this peer right now
     */
    public long sendGetBlocks(long startTime, long endTime, SettableFuture<Bytes> reply) {
        if (!mayRequest()) {
            return -1;
        }
        log.debug("Request blocks between {} and {} from node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getRemoteAddress());
        BlocksRequestMessage msg = new BlocksRequestMessage(startTime, endTime, chain.getXdagStats());
        if (reply != null) {
            kernel.getSync().getBlocksRequestMap().put(msg.getRandom(), reply);
        }
        long now = System.currentTimeMillis();
        if (askedSpans.size() >= MAX_ASKED_BLOCKS) {
            askedSpans.values().removeIf(span -> span[2] <= now);
        }
        askedSpans.put(msg.getRandom(), new long[]{startTime, endTime, now + ANSWER_WAIT_MS});
        sendMessage(msg);
        return msg.getRandom();
    }

    public long sendGetBlock(MutableBytes32 hash, boolean isOld) {
        if (!mayRequest()) {
            log.debug("Not asking {} for {}: too many requests; it is asked again later", channel.getRemoteAddress(), hash);
            return -1;
        }
        XdagMessage msg = isOld ? new SyncBlockRequestMessage(hash, chain.getXdagStats())
                : new BlockRequestMessage(hash, chain.getXdagStats());
        log.debug("Request block {} isold: {} from node {}", hash, isOld, channel.getRemoteAddress());
        askedBlocks.put(Bytes32.wrap(hash.toArray()), System.currentTimeMillis() + ANSWER_WAIT_MS);
        sendMessage(msg);
        return msg.getRandom();
    }

    public long sendGetSums(long startTime, long endTime) {
        return sendGetSums(startTime, endTime, null);
    }

    /**
     * Asks the peer for the sums of a span; see {@link #sendGetBlocks(long, long, SettableFuture)} for
     * {@code reply} and the result.
     */
    public long sendGetSums(long startTime, long endTime, SettableFuture<Bytes> reply) {
        if (!mayRequest()) {
            return -1;
        }
        SumRequestMessage msg = new SumRequestMessage(startTime, endTime, chain.getXdagStats());
        if (reply != null) {
            kernel.getSync().getSumsRequestMap().put(msg.getRandom(), reply);
        }
        sendMessage(msg);
        log.debug("Request sums from startTime:{} ,endTime:{}", startTime, endTime);
        return msg.getRandom();
    }

    public void sendMessage(Message message) {
        if (!channel.isActive()) {
            return;
        }
        byte[] body = message.getBody() == null ? new byte[0] : message.getBody();
        byte[] wire = new byte[1 + body.length];
        wire[0] = message.getCode().toByte();
        System.arraycopy(body, 0, wire, 1, body.length);
        channel.getTransport().send(Bytes.wrap(wire));
    }

    /**
     * Takes note of what the peer says about the network. A claim that cannot be true - more main blocks than
     * there have been epochs - is a lie and is ignored; the rest only ever raises the totals, as before.
     */
    public void updateXdagStats(XdagMessage message) {
        XdagStats remote = message.getXdagStats();
        if (remote == null) {
            return;
        }
        long maxPossibleMain = XdagTime.getCurrentEpoch() - XdagTime.getEpoch(config.getXdagEra()) + 2;
        if (remote.totalnmain < 0 || remote.totalnmain > maxPossibleMain || remote.totalnblocks < 0
                || (remote.maxdifficulty != null && remote.maxdifficulty.signum() < 0)) {
            misbehave(SCORE_BAD_REQUEST, "impossible network statistics");
            return;
        }
        // Confirm that the remote stats has been updated, used to check local state.
        syncMgr.getIsUpdateXdagStats().compareAndSet(false, true);
        chain.getXdagStats().update(remote);
    }

    /** A token bucket: {@code rate} tokens per second, at most {@code burst} saved up. */
    static final class TokenBucket {
        private final double burst;
        private final double rate;
        private double tokens;
        private long last = System.nanoTime();

        TokenBucket(double burst, double rate) {
            this.burst = burst;
            this.rate = rate;
            this.tokens = burst;
        }

        synchronized boolean tryAcquire(double n) {
            long now = System.nanoTime();
            tokens = Math.min(burst, tokens + (now - last) / 1e9 * rate);
            last = now;
            if (tokens >= n) {
                tokens -= n;
                return true;
            }
            return false;
        }
    }
}
