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
package io.xdag.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.xdag.config.Config;
import lombok.extern.slf4j.Slf4j;

/**
 * An in-memory structure holding all the activated forks.
 */
@Slf4j
public class ActivatedForks {

    private final Dagchain chain;
    private final Config config;

    /**
     * Activated forks at current height.
     */
    private final Map<Fork, Fork.Activation> activatedForks;

    /**
     * Cache of <code>(fork, height) -> activated blocks</code>.
     */
    private final Cache<ImmutablePair<Fork, Long>, ForkActivationMemory> cache = Caffeine
            .newBuilder()
            .maximumSize(1024)
            .build();

    /**
     * Creates an activated fork set.
     */
    public ActivatedForks(Dagchain chain, Config config, Map<Fork, Fork.Activation> activatedForks) {
        this.chain = chain;
        this.config = config;
        this.activatedForks = new ConcurrentHashMap<>(activatedForks);
    }

    /**
     * Tries to activate a fork.
     */
    public boolean activateFork(Fork fork) {
        long[] period = config.getDagSpec().getForkSignalingPeriod(fork);

        long number = chain.getLatestMainBlockNumber();
        if (number >= period[0]
                && number <= period[1]
                && !isActivated(fork, number)
                && isActivated(fork, number + 1)) {
            activatedForks.put(fork, new Fork.Activation(fork, number + 1));
            log.info("Fork {} has been activated and will be effective from #{}", fork, number + 1);
            return true;
        }

        return false;
    }

    /**
     * Checks if a fork is activated at a certain height of this blockchain.
     *
     * @param fork
     *            An instance of ${@link Fork} to check.
     * @param height
     *            A blockchain height to check.
     */
    public boolean isActivated(Fork fork, final long height) {
        assert (fork.blocksRequired() > 0);
        assert (fork.blocksToCheck() > 0);

        // checks whether the fork has been activated and recorded in database
        if (activatedForks.containsKey(fork)) {
            return height >= activatedForks.get(fork).effectiveFrom;
        }

        // checks whether the local blockchain has reached the fork activation
        // checkpoint
        if (config.manuallyActivatedForks().containsKey(fork)) {
            return height >= config.manuallyActivatedForks().get(fork);
        }

        // do not search if it's not within the range
        long[] period = config.getDagSpec().getForkSignalingPeriod(fork);
        if (height - 1 < period[0] || height - 1 > period[1]) {
            return false;
        }

        // returns memoized result of fork activation lookup at current height
        ForkActivationMemory current = cache.getIfPresent(ImmutablePair.of(fork, height));
        if (current != null) {
            return current.activatedBlocks >= fork.blocksRequired();
        }

        // block range to search:
        // from (number - 1)
        // to (number - fork.blocksToCheck)
        long higherBound = Math.max(0, height - 1);
        long lowerBound = Math.max(0, height - fork.blocksToCheck());
        long activatedBlocks = 0;

        ForkActivationMemory previous = cache.getIfPresent(ImmutablePair.of(fork, height - 1));
        if (previous != null) {
            // O(1) dynamic-programming lookup
            activatedBlocks = previous.activatedBlocks
                    - (lowerBound > 0 && previous.lowerBoundActivated ? 1 : 0)
                    + (chain.getBlockHeader(higherBound).getDecodedData().parseForkSignals().contains(fork) ? 1 : 0);
        } else {
            // O(m) traversal lookup
            for (long i = higherBound; i >= lowerBound; i--) {
                activatedBlocks += chain.getBlockHeader(i).getDecodedData().parseForkSignals().contains(fork) ? 1 : 0;
            }
        }

        log.trace("number = {}, higher bound = {}, lower bound = {}", height, higherBound, lowerBound);

        // memorizes
        cache.put(ImmutablePair.of(fork, height),
                new ForkActivationMemory(
                        chain.getBlockHeader(lowerBound).getDecodedData().parseForkSignals().contains(fork),
                        activatedBlocks));

        // returns
        boolean activated = activatedBlocks >= fork.blocksRequired();
        if (activatedBlocks > 0) {
            log.debug("Fork: name = {}, requirement = {} / {}, progress = {}",
                    fork.name(), fork.blocksRequired(), fork.blocksToCheck(), activatedBlocks);
        }

        return activated;
    }

    /**
     * Returns all the activate forks.
     */
    public Map<Fork, Fork.Activation> getActivatedForks() {
        return new HashMap<>(activatedForks);
    }

    /**
     * <code>
     * ForkActivationMemory[height].lowerBoundActivated =
     * forkActivated(height - ${@link Fork#blocksToCheck()})
     * <p>
     * ForkActivationMemory[height].activatedBlocks =
     * ForkActivationMemory[height - 1].activatedBlocks -
     * ForkActivationMemory[height - 1].lowerBoundActivated ? 1 : 0 +
     * forkActivated(height - 1) ? 1 : 0
     * </code>
     *
     * @param lowerBoundActivated Whether the fork is activated at height
     *                            <code>(current height -{@link Fork#blocksToCheck()})</code>.
     * @param activatedBlocks     The number of activated blocks at the memorized height.
     */
        private record ForkActivationMemory(boolean lowerBoundActivated, long activatedBlocks) {

    }
}
