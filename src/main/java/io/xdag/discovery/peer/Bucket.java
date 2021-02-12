package io.xdag.discovery.peer;


import io.xdag.discovery.Utils.bytes.BytesValue;
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
