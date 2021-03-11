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