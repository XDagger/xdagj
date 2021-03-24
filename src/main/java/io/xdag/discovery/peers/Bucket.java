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

import io.xdag.utils.discoveryutils.bytes.BytesValue;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.System.arraycopy;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;
import static java.util.Collections.unmodifiableList;

@Slf4j
public class Bucket {
    private final DiscoveryPeer[] kBucket;
    private final int bucketSize;
    private int tailIndex = -1;

    public Bucket(int bucketSize) {
        this.bucketSize = bucketSize;
        kBucket = new DiscoveryPeer[bucketSize];
    }

    synchronized Optional<DiscoveryPeer> getAndTouch(final BytesValue id) {
        for (int i = 0; i <= tailIndex; i++) {
            final DiscoveryPeer p = kBucket[i];
            if (id.equals(p.getId())) {
                arraycopy(kBucket, 0, kBucket, 1, i);
                kBucket[0] = p;
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    synchronized Optional<DiscoveryPeer> add(final DiscoveryPeer peer)
            throws IllegalArgumentException {
        assert tailIndex >= -1 && tailIndex < bucketSize;

        // Avoid duplicating the peer if it already exists in the bucket.
        for (int i = 0; i <= tailIndex; i++) {
            if (peer.equals(kBucket[i])) {
                throw new IllegalArgumentException(
                        String.format("Tried to add duplicate peer to k-bucket: %s", peer.getId()));
            }
        }
        if (tailIndex == bucketSize - 1) {
            return Optional.of(kBucket[tailIndex]);
        }
        arraycopy(kBucket, 0, kBucket, 1, ++tailIndex);
        kBucket[0] = peer;
        return Optional.empty();
    }

    synchronized boolean evict(final PeerId peer) {
        // If the bucket is empty, there's nothing to evict.
        if (tailIndex < 0) {
            return false;
        }
        // If found, shift all subsequent elements to the left, and decrement tailIndex.
        for (int i = 0; i <= tailIndex; i++) {
            if (peer.equals(kBucket[i])) {
                arraycopy(kBucket, i + 1, kBucket, i, tailIndex - i);
                kBucket[tailIndex--] = null;
                return true;
            }
        }
        return false;
    }

    synchronized List<DiscoveryPeer> peers() {
        return unmodifiableList(asList(copyOf(kBucket, tailIndex + 1)));

    }

    @Override
    public String toString() {
        return "peer.Bucket{" +
                "kBucket=" + Arrays.toString(kBucket) +
                ", bucketSize=" + bucketSize +
                ", tailIndex=" + tailIndex +
                '}';
    }
}
