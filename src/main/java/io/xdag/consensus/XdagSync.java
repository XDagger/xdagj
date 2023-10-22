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

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import io.xdag.DagKernel;
import io.xdag.config.Config;
import io.xdag.core.BlockPart;
import io.xdag.core.Dagchain;
import io.xdag.core.MainBlock;
import io.xdag.core.SyncManager;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.net.Capability;
import io.xdag.net.Channel;
import io.xdag.net.ChannelManager;
import io.xdag.net.Peer;
import io.xdag.net.message.Message;
import io.xdag.net.message.ReasonCode;
import io.xdag.net.message.consensus.GetMainBlockMessage;
import io.xdag.net.message.consensus.GetMainBlockPartsMessage;
import io.xdag.net.message.consensus.MainBlockMessage;
import io.xdag.net.message.consensus.MainBlockPartsMessage;
import io.xdag.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagSync implements SyncManager {

    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "sync-" + cnt.getAndIncrement());
        }
    };

    private static final ScheduledExecutorService timer1 = Executors.newSingleThreadScheduledExecutor(factory);
    private static final ScheduledExecutorService timer2 = Executors.newSingleThreadScheduledExecutor(factory);
    private static final ScheduledExecutorService timer3 = Executors.newSingleThreadScheduledExecutor(factory);

    private final long DOWNLOAD_TIMEOUT;

    private final int MAX_QUEUED_JOBS;
    private final int MAX_PENDING_JOBS;
    private final int MAX_PENDING_BLOCKS;

    private static final Random random = new Random();

    private final Config config;

    private final Dagchain chain;
    private final ChannelManager channelManager;

    private volatile Channel channel;

    // task queues
    private final AtomicLong latestQueuedTask = new AtomicLong();

    // Blocks to download
    private final TreeSet<Long> toDownload = new TreeSet<>();

    // Blocks which were requested but haven't been received
    private final Map<Long, Long> toReceive = new HashMap<>();

    // Blocks which were received but haven't been validated
    private final TreeSet<Pair<MainBlock, Channel>> toValidate = new TreeSet<>(
            Comparator.comparingLong(o -> o.getKey().getNumber()));

    // Blocks which were validated but haven't been imported
    private final TreeMap<Long, Pair<MainBlock, Channel>> toImport = new TreeMap<>();

    private final Object lock = new Object();

    // current and target heights
    private final AtomicLong begin = new AtomicLong();
    private final AtomicLong current = new AtomicLong();
    private final AtomicLong target = new AtomicLong();
    private final AtomicLong lastObserved = new AtomicLong();

    private final AtomicLong beginningTimestamp = new AtomicLong();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // reset at the beginning of a sync task
    private final Set<String> badPeers = new HashSet<>();

    public XdagSync(DagKernel kernel) {
        this.config = kernel.getConfig();

        this.chain = kernel.getDagchain();
        this.channelManager = kernel.getChannelManager();

        this.DOWNLOAD_TIMEOUT = config.getNodeSpec().syncDownloadTimeout();
        this.MAX_QUEUED_JOBS = config.getNodeSpec().syncMaxQueuedJobs();
        this.MAX_PENDING_JOBS = config.getNodeSpec().syncMaxPendingJobs();
        this.MAX_PENDING_BLOCKS = config.getNodeSpec().syncMaxPendingBlocks();
    }

    @Override
    public void start(long begin, long current, long target, Channel channel) {
        if (isRunning.compareAndSet(false, true)) {
            this.channel = channel;
            beginningTimestamp.set(System.currentTimeMillis());

            badPeers.clear();

            log.info("Syncing started, best known block = {}, begin = {}, current = {}, target = {}, peer = {}",
                    chain.getLatestMainBlockNumber(),
                    begin,
                    current,
                    target,
                    channel.getRemotePeer());

            // [1] set up queues
            synchronized (lock) {
                toDownload.clear();
                toReceive.clear();
                toValidate.clear();
                toImport.clear();

                this.begin.set(begin);
                this.current.set(current);
                this.target.set(target);
                lastObserved.set(begin - 1);
                latestQueuedTask.set(begin - 1);
                growToDownloadQueue();
            }

            // [2] start tasks
            ScheduledFuture<?> download = timer1.scheduleAtFixedRate(this::download, 0, 500, TimeUnit.MICROSECONDS);
            ScheduledFuture<?> process = timer2.scheduleAtFixedRate(this::process, 0, 1000, TimeUnit.MICROSECONDS);
            ScheduledFuture<?> reporter = timer3.scheduleAtFixedRate(() -> {
                log.info(
                        "Syncing status: importing {} blocks per second, {} to download, {} to receive, {} to validate, {} to import",
                        (current - lastObserved.get()) / 30,
                        toDownload.size(),
                        toReceive.size(),
                        toValidate.size(),
                        toImport.size());
                lastObserved.set(current);
            }, 30, 30, TimeUnit.SECONDS);

            // [3] wait until the sync is done
            while (isRunning.get()) {
                synchronized (isRunning) {
                    try {
                        isRunning.wait(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info("Sync manager got interrupted");
                        break;
                    }
                }
            }

            // [4] cancel tasks
            download.cancel(true);
            process.cancel(false);
            reporter.cancel(true);
            this.channel = null;

            Instant end = Instant.now();
            log.info("Syncing finished, took {}",
                    TimeUtils.formatDuration(Duration.between(Instant.ofEpochMilli(beginningTimestamp.get()), end)));
        }
    }

    @Override
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            synchronized (isRunning) {
                isRunning.notifyAll();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    protected void addBlock(MainBlock block, Channel channel) {
        synchronized (lock) {
            if (toDownload.remove(block.getNumber())) {
                growToDownloadQueue();
            }
            toReceive.remove(block.getNumber());
            toValidate.add(Pair.of(block, channel));
        }
    }

    @Override
    public void onMessage(Channel channel, Message msg) {
        if (!isRunning()) {
            return;
        }

        switch (msg.getCode()) {
        case MAIN_BLOCK: {
            MainBlockMessage blockMsg = (MainBlockMessage) msg;
            MainBlock block = blockMsg.getBlock();
            addBlock(block, channel);
            break;
        }
        case MAIN_BLOCK_PARTS: {
            // try re-construct a block
            MainBlockPartsMessage blockPartsMsg = (MainBlockPartsMessage) msg;
            List<BlockPart> parts = BlockPart.decode(blockPartsMsg.getParts());
            List<byte[]> data = blockPartsMsg.getData();

            // sanity check
            if (parts.size() != data.size()) {
                log.debug("Part set and data do not match");
                break;
            }

            // parse the data
            byte[] header = null, transactions = null, results = null;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i) == BlockPart.HEADER) {
                    header = data.get(i);
                } else if (parts.get(i) == BlockPart.TRANSACTIONS) {
                    transactions = data.get(i);
                } else if (parts.get(i) == BlockPart.RESULTS) {
                    results = data.get(i);
                }
            }

            // import block
            try {
                MainBlock block = MainBlock.fromComponents(header, transactions, results, false);
                addBlock(block, channel);
            } catch (Exception e) {
                log.debug("Failed to parse a block from components", e);
            }
            break;
        }
        case MAIN_BLOCK_HEADER:
        default: {
            break;
        }
        }
    }

    private boolean isFastSyncSupported(Peer peer) {
        return Stream.of(peer.getCapabilities()).anyMatch(c -> Capability.FAST_SYNC.name().equals(c));
    }

    private void download() {
        if (!isRunning()) {
            return;
        }

        synchronized (lock) {
            // filter all expired tasks
            long now = TimeUtils.currentTimeMillis();
            Iterator<Entry<Long, Long>> itr = toReceive.entrySet().iterator();
            while (itr.hasNext()) {
                Entry<Long, Long> entry = itr.next();

                if (entry.getValue() + DOWNLOAD_TIMEOUT < now) {
                    log.debug("Failed to download block #{}, expired", entry.getKey());
                    toDownload.add(entry.getKey());
                    itr.remove();
                }
            }

            // quit if too many unfinished jobs
            if (toReceive.size() > MAX_PENDING_JOBS) {
                log.trace("Max pending jobs reached");
                return;
            }

            // quit if no more tasks
            if (toDownload.isEmpty()) {
                return;
            }
            Long task = toDownload.first();

            // quit if too many pending blocks
            int pendingBlocks = toValidate.size() + toImport.size();
            if (pendingBlocks > MAX_PENDING_BLOCKS && task > toValidate.first().getKey().getNumber()) {
                log.trace("Max pending blocks reached");
                return;
            }

            Channel c = null;
            if(channel == null) {
                // get idle channels
                List<Channel> channels = channelManager.getIdleChannels().stream()
                        .filter(channel -> {
                            Peer peer = channel.getRemotePeer();
                            // the peer has the block
                            return peer.getLatestMainBlock().getNumber() >= task
                                    // AND is not banned
                                    && !badPeers.contains(peer.getPeerId())
                                    // AND supports FAST_SYNC if we enabled this protocol
                                    && (!config.getNodeSpec().syncFastSync() || isFastSyncSupported(peer));
                        })
                        .toList();
                log.trace("Qualified idle peers = {}", channels.size());

                // quit if no idle channels.
                if (channels.isEmpty()) {
                    return;
                }
                // otherwise, pick a random channel
                c = channels.get(random.nextInt(channels.size()));
            } else {
                c = this.channel;
            }


            if (config.getNodeSpec().syncFastSync()) { // use FAST_SYNC protocol
                log.trace("Requesting block #{} from {}:{}, HEADER + TRANSACTIONS", task,
                        c.getRemoteIp(),
                        c.getRemotePort());
                c.getMsgQueue().sendMessage(new GetMainBlockPartsMessage(task,
                        BlockPart.encode(BlockPart.HEADER, BlockPart.TRANSACTIONS)));

            } else { // use old protocol
                log.trace("Requesting block #{} from {}:{}, FULL BLOCK", task, c.getRemoteIp(),
                        c.getRemotePort());
                c.getMsgQueue().sendMessage(new GetMainBlockMessage(task));
            }

            if (toDownload.remove(task)) {
                growToDownloadQueue();
            }
            toReceive.put(task, TimeUtils.currentTimeMillis());
        }
    }

    /**
     * Queue new tasks sequentially starting from
     * ${@link XdagSync#latestQueuedTask} until the size of
     * ${@link XdagSync#toDownload} queue is greater than or equal to
     * MAX_QUEUED_JOBS
     */
    private void growToDownloadQueue() {
        // To avoid overhead, this method doesn't add new tasks before the queue is less
        // than half-filled
        if (toDownload.size() >= MAX_QUEUED_JOBS / 2) {
            return;
        }

        for (long task = latestQueuedTask.get() + 1; //
                task < target.get() && toDownload.size() < MAX_QUEUED_JOBS; //
                task++) {
            latestQueuedTask.accumulateAndGet(task, (prev, next) -> Math.max(next, prev));
//            if (!chain.hasMainBlock(task)) {
                toDownload.add(task);
//            }
        }
    }

    protected void process() {
        if (!isRunning()) {
            return;
        }

        long latest = current.get();
        if (latest + 1 >= target.get()) {
            stop();
            return; // This is important because stop() only notify
        }

        long checkpoint = latest + 1;

        synchronized (lock) {
            // Move blocks from validate queue to import queue if within range
            Iterator<Pair<MainBlock, Channel>> iterator = toValidate.iterator();
            while (iterator.hasNext()) {
                Pair<MainBlock, Channel> p = iterator.next();
                long n = p.getKey().getNumber();

                if (n <= latest) {
                    iterator.remove();
                } else if (n <= checkpoint) {
                    iterator.remove();
                    toImport.put(n, p);
                } else {
                    break;
                }
            }

            if (toImport.size() >= checkpoint - latest) {
                // Validate the main block hashes
                boolean valid = validateBlockHashes(latest + 1, checkpoint);

                if (valid) {
                    for (long n = latest + 1; n <= checkpoint; n++) {
                        Pair<MainBlock, Channel> p = toImport.remove(n);
                        MainBlock mb = p.getKey();
                        AccountState parentAccountState = chain.getAccountState(mb.getParentHash(), mb.getNumber() - 1);
                        BlockState parentBlockState = chain.getBlockState(mb.getParentHash(), mb.getNumber() - 1);
                        boolean imported = chain.importBlock(p.getKey(), parentAccountState.clone(),
                                parentBlockState.clone());
                        if (!imported) {
                            handleInvalidBlock(p.getKey(), p.getValue());
                            break;
                        }

                        if (n == checkpoint) {
                            log.info("{}", p.getLeft());
                        }
                    }
                    current.getAndIncrement();
                }
            }
        }
    }

    /**
     * Validate block hashes in the toImport set.
     * Assuming that the whole block range is available in the set.
     *
     * @param from
     *            the start block number, inclusive
     * @param to
     *            the end block number, inclusive
     */
    protected boolean validateBlockHashes(long from, long to) {
        synchronized (lock) {
            for (long n = to - 1; n >= from; n--) {
                Pair<MainBlock, Channel> current = toImport.get(n);
                Pair<MainBlock, Channel> child = toImport.get(n + 1);

                if (!Arrays.equals(current.getKey().getHash(), child.getKey().getParentHash())) {
                    handleInvalidBlock(current.getKey(), current.getValue());
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Handle invalid block: Add block back to download queue. Remove block from all
     * other queues. Disconnect from the peer that sent the block.
     */
    protected void handleInvalidBlock(MainBlock block, Channel channel) {
        InetSocketAddress a = channel.getRemoteAddress();
        log.info("Invalid block, peer = {}:{}, block # = {}", a.getAddress().getHostAddress(), a.getPort(),
                block.getNumber());
        synchronized (lock) {
            // add to the request queue
            toDownload.add(block.getNumber());

            toReceive.remove(block.getNumber());
            toValidate.remove(Pair.of(block, channel));
            toImport.remove(block.getNumber());
        }

        badPeers.add(channel.getRemotePeer().getPeerId());

        if (config.getNodeSpec().syncDisconnectOnInvalidBlock()) {
            // disconnect if the peer sends us invalid block
            channel.getMsgQueue().disconnect(ReasonCode.BAD_PEER);
        }
    }

    @Override
    public XdagSyncProgress getProgress() {
        return new XdagSyncProgress(
                begin.get(),
                current.get(),
                target.get(),
                Duration.between(Instant.ofEpochMilli(beginningTimestamp.get()), Instant.now()));
    }

    public static class XdagSyncProgress implements Progress {

        final long startingHeight;

        final long currentHeight;

        final long targetHeight;

        final Duration duration;

        public XdagSyncProgress(long startingHeight, long currentHeight, long targetHeight, Duration duration) {
            this.startingHeight = startingHeight;
            this.currentHeight = currentHeight;
            this.targetHeight = targetHeight;
            this.duration = duration;
        }

        @Override
        public long getStartingHeight() {
            return startingHeight;
        }

        @Override
        public long getCurrentHeight() {
            return currentHeight;
        }

        @Override
        public long getTargetHeight() {
            return targetHeight;
        }

        @Override
        public Duration getSyncEstimation() {
            long durationInSeconds = duration.toSeconds();
            long imported = currentHeight - startingHeight;
            long remaining = targetHeight - currentHeight;

            if (imported == 0) {
                return null;
            } else {
                return Duration.ofSeconds(remaining * durationInSeconds / imported);
            }
        }
    }
}
