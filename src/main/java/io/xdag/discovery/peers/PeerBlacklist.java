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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
//todo
public class PeerBlacklist {
    private static final int DEFAULT_BLACKLIST_CAP = 500;



    private final int blacklistCap;
    private final Set<BytesValue> blacklistedNodeIds =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(
                            new LinkedHashMap<BytesValue, Boolean>(20, 0.75f, true) {
                                @Override
                                protected boolean removeEldestEntry(final Map.Entry<BytesValue, Boolean> eldest) {
                                    return size() > blacklistCap;
                                }
                            }));

    /** These nodes are always banned for the life of this list. They are not subject to rollover. */
    private final Set<BytesValue> bannedNodeIds;

    public PeerBlacklist(final int blacklistCap, final Set<BytesValue> bannedNodeIds) {
        this.blacklistCap = blacklistCap;
        this.bannedNodeIds = bannedNodeIds;
    }

    public PeerBlacklist(final int blacklistCap) {
        this(blacklistCap, Collections.emptySet());
    }

    public PeerBlacklist(final Set<BytesValue> bannedNodeIds) {
        this(DEFAULT_BLACKLIST_CAP, bannedNodeIds);
    }

    public PeerBlacklist() {
        this(DEFAULT_BLACKLIST_CAP, Collections.emptySet());
    }

    private boolean contains(final BytesValue nodeId) {
        return blacklistedNodeIds.contains(nodeId) || bannedNodeIds.contains(nodeId);
    }


    public boolean contains(final Peer peer) {
        return contains(peer.getId());
    }

    public void add(final Peer peer) {
        add(peer.getId());
    }

    public void add(final BytesValue peerId) {
        blacklistedNodeIds.add(peerId);
    }


}