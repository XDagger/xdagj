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
package io.xdag.discovery.peers;

import io.xdag.utils.discoveryutils.PeerDiscoveryStatus;
import io.xdag.utils.discoveryutils.bytes.BytesValue;
import com.google.common.hash.BloomFilter;
import io.xdag.utils.discoveryutils.cryto.Hash;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static io.xdag.discovery.peers.PeerDistanceCalculator.distance;

@Slf4j
public class PeerTable {
    private static final int N_BUCKETS = 256;
    private static final int DEFAULT_BUCKET_SIZE = 16;
    private static final int BLOOM_FILTER_REGENERATION_THRESHOLD = 50; // evictions

    private final Bucket[] table;
    private final BytesValue keccak256;
    private final int maxEntriesCnt;
    private final Map<BytesValue, Integer> distanceCache;
    private int evictionCnt = 0;

    /**
     * Builds a new peer table, where distance is calculated using the provided nodeId as a baseline.
     *
     * @param nodeId The ID of the node where this peer table is stored.
     * @param bucketSize The maximum length of each k-bucket.
     */
    public PeerTable(final BytesValue nodeId, final int bucketSize) throws IOException {
        this.keccak256 = Hash.keccak256(nodeId);
        this.table =
                Stream.generate(() -> new Bucket(DEFAULT_BUCKET_SIZE))
                        .limit(N_BUCKETS + 1)
                        .toArray(Bucket[]::new);
        this.distanceCache = new ConcurrentHashMap<>();
        this.maxEntriesCnt = N_BUCKETS * bucketSize;

        // A bloom filter with 4096 expected insertions of 64-byte keys with a 0.1% false positive
        // probability yields a memory footprint of ~7.5kb.
        //buildBloomFilter();
    }

    public PeerTable(final BytesValue nodeId) throws IOException {
        this(nodeId, DEFAULT_BUCKET_SIZE);
    }

    /**
     * Returns the table's representation of a peer, if it exists.
     *
     * @param peer The peer to query.
     * @return The stored representation.
     */
    public Optional<DiscoveryPeer> get(final PeerId peer) throws IOException {
        final int distance = distanceFrom(peer);
        return table[distance].getAndTouch(peer.getId());
    }

    /**
     * Attempts to add the provided peer to the peer table, and returns an {@link AddResult}
     * signalling one of three outcomes.
     *
     * <h3>Possible outcomes:</h3>
     *
     * <ul>
     *   <li>the operation succeeded and the peer was added to the corresponding k-bucket.
     *   <li>the operation failed because the k-bucket was full, in which case a candidate is proposed
     *       for eviction.
     *   <li>the operation failed because the peer already existed.
     * </ul>
     *
     * @see AddResult.Outcome
     * @param peer The peer to add.
     * @return An object indicating the outcome of the operation.
     */
    public AddResult tryAdd(final DiscoveryPeer peer) throws IOException {

        final BytesValue id = peer.getId();
        final int distance = distanceFrom(peer);

        // Safeguard against adding ourselves to the peer table.
        if (distance == 0) {
            return AddResult.self();
        }

        final Bucket bucket = table[distance];
        // We add the peer, and two things can happen: (1) either we get an empty optional (peer was
        // added successfully,
        // or it was already there), or (2) we get a filled optional, in which case the bucket is full
        // and an eviction
        // candidate is proposed. The Bucket#add method will raise an exception if the peer already
        // existed.
        final Optional<DiscoveryPeer> res;
        try {
            res = bucket.add(peer);
        } catch (final IllegalArgumentException ex) {
            return AddResult.existed();
        }

        if (!res.isPresent()) {
            distanceCache.put(id, distance);
            log.info("distanceCache.size = {}",distanceCache.size());
            log.info("add suss");
            return AddResult.added();
        }

        return res.map(AddResult::bucketFull).get();
    }

    /**
     * Evicts a peer from the underlying table.
     *
     * @param peer The peer to evict.
     */
    public void evict(final PeerId peer) throws IOException {
        final BytesValue id = peer.getId();
        final int distance = distanceFrom(peer);
        distanceCache.remove(id);

        final boolean evicted = table[distance].evict(peer);
        evictionCnt += evicted ? 1 : 0;

        // Trigger the bloom filter regeneration if needed.
        if (evictionCnt >= BLOOM_FILTER_REGENERATION_THRESHOLD) {
            ForkJoinPool.commonPool().execute(this::buildBloomFilter);
        }

    }
    //BloomFilter判断id是否存在
    private void buildBloomFilter() {
        final BloomFilter<BytesValue> bf =
                BloomFilter.create((id, val) -> val.putBytes(id.extractArray()), maxEntriesCnt, 0.001);
        getAllPeers().stream().map(Peer::getId).forEach(bf::put);
        this.evictionCnt = 0;
    }

    /**
     * Returns the <code>limit</code> peers (at most) closest to the provided target, based on the XOR
     * distance between the keccak-256 hash of the ID and the keccak-256 hash of the target.
     *
     * @param target The target node ID.
     * @param limit The amount of results to return.
     * @return The <code>limit</code> closest peers, at most.
     */
    public List<DiscoveryPeer> nearestPeers(final BytesValue target, final int limit) throws IOException {
        final BytesValue keccak256 = Hash.keccak256(target);
        return getAllPeers()
                .stream()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
                .sorted(comparingInt((peer) -> {
                    try {
                        return distance(peer.keccak256(), keccak256);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return 0;
                }))
                .limit(limit)
                .collect(toList());
    }

    public Collection<DiscoveryPeer> getAllPeers() {
        return Arrays.stream(table).flatMap(e -> e.peers().stream()).collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    /**
     * Calculates the XOR distance between the keccak-256 hashes of our node ID and the provided
     * {@link DiscoveryPeer}.
     *
     * @param peer The target peer.
     * @return The distance.
     */
    private int distanceFrom(final PeerId peer) throws IOException {
        final Integer distance = distanceCache.get(peer.getId());
        return distance == null ? distance(keccak256, peer.keccak256()) : distance;
    }

    /** A class that encapsulates the result of a peer addition to the table. */
    public static class AddResult {
        /** The outcome of the operation. */
        public enum Outcome {

            /** The peer was added successfully to its corresponding k-bucket. */
            ADDED,

            /** The bucket for this peer was full. An eviction candidate must be proposed. */
            BUCKET_FULL,

            /** The peer already existed, hence it was not overwritten. */
            ALREADY_EXISTED,

            /** The caller requested to add ourselves. */
            SELF
        }

        private final Outcome outcome;
        private final Peer evictionCandidate;

        private AddResult(final Outcome outcome, final Peer evictionCandidate) {
            this.outcome = outcome;
            this.evictionCandidate = evictionCandidate;
        }

        static AddResult added() {
            return new AddResult(Outcome.ADDED, null);
        }
        //bucket满的时候去除最后一位
        static AddResult bucketFull(final Peer evictionCandidate) {
            return new AddResult(Outcome.BUCKET_FULL, evictionCandidate);
        }

        static AddResult existed() {
            return new AddResult(Outcome.ALREADY_EXISTED, null);
        }

        static AddResult self() {
            return new AddResult(Outcome.SELF, null);
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public Peer getEvictionCandidate() {
            return evictionCandidate;
        }
    }
}
