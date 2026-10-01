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

import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.core.ImportResult;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes32;

/**
 * What one peer sends while {@link XdagSync} holds a round with it, kept by {@link SyncManager} as the blocks
 * come in.
 *
 * <p>This is the evidence a node has of where it stands with a peer, and it is made of blocks, not of anything
 * the peer says: a block only becomes part of this node's blocks once everything it refers to is there, so
 * "the newest blocks the peer has are part of my blocks" means this node has the whole history behind them.
 * The sums of the comparison cannot say that - they are checksums that anybody can make come out equal.
 */
public final class PeerEvidence {

    /** More blocks than this waiting for a parent are not followed one by one: the node is simply behind. */
    static final int MAX_WAITING = 1024;

    /** Blocks the peer sent that refer to a block this node does not have. */
    private final Set<Bytes32> waiting = ConcurrentHashMap.newKeySet();
    private volatile boolean overflow;
    /** Timestamp of the newest block the peer sent. */
    private volatile long newest;
    /** Blocks the peer sent that this node did not have. */
    private final AtomicInteger imported = new AtomicInteger();

    // a span of which the peer's blocks are only looked at (peekEnd == 0: none)
    private volatile long peekStart;
    private volatile long peekEnd;
    private final AtomicInteger peekedBlocks = new AtomicInteger();
    private final AtomicInteger peekedUnknown = new AtomicInteger();

    /**
     * @param blocks  blocks the peer sent for the span
     * @param unknown those among them that are not part of this node's blocks
     */
    public record Peek(int blocks, int unknown) {
    }

    /** A block from the peer was put to the chain, with this result. */
    void saw(Block block, ImportResult result) {
        if (result == ImportResult.INVALID_BLOCK || result == ImportResult.ERROR) {
            return;
        }
        long time = block.getTimestamp();
        if (time > newest) {
            newest = time;
        }
        if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST
                || result == ImportResult.IMPORTED_EXTRA) {
            imported.incrementAndGet();
        }
        if (result == ImportResult.NO_PARENT) {
            if (waiting.size() < MAX_WAITING) {
                waiting.add(Bytes32.wrap(block.getHashLow().toArray()));
            } else {
                overflow = true;
            }
        }
    }

    /**
     * Whether everything the peer sent that needed a parent has found it by now, i.e. is part of this node's
     * blocks. (Blocks that were refused as invalid do not count: they never will be.)
     */
    public boolean connected(Blockchain chain) {
        if (overflow) {
            return false;
        }
        waiting.removeIf(hash -> chain.getBlockByHash(hash, false) != null);
        return waiting.isEmpty();
    }

    /** Blocks the peer sent that were new to this node and are part of its blocks now. */
    public int imported() {
        return imported.get();
    }

    /** So many blocks are waiting for a parent that they are no longer followed. */
    public boolean overflowed() {
        return overflow;
    }

    /** Timestamp of the newest block the peer sent (blocks that were refused aside), 0 if it sent none. */
    public long newest() {
        return newest;
    }

    /**
     * From now on the blocks of the span that the peer sends as history are counted, not imported: the caller
     * wants to know whether the peer has blocks there that this node lacks without taking them yet.
     */
    public void beginPeek(long start, long end) {
        peekedBlocks.set(0);
        peekedUnknown.set(0);
        peekStart = start;
        peekEnd = end;
    }

    public Peek endPeek() {
        peekEnd = 0;
        return new Peek(peekedBlocks.get(), peekedUnknown.get());
    }

    /** @return true if the block was only looked at and must not be imported */
    boolean peek(BlockWrapper wrapper, Blockchain chain) {
        long end = peekEnd;
        if (end == 0 || !wrapper.isOld()) {
            return false;
        }
        Block block = wrapper.getBlock();
        long time = block.getTimestamp();
        if (time < peekStart || time >= end) {
            return false;
        }
        peekedBlocks.incrementAndGet();
        if (chain.getBlockByHash(Bytes32.wrap(block.getHashLow().toArray()), false) == null) {
            peekedUnknown.incrementAndGet();
        }
        return true;
    }
}
