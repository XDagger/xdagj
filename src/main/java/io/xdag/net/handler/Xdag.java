package io.xdag.net.handler;

import com.google.common.util.concurrent.ListenableFuture;
import io.xdag.core.Block;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.impl.SumReplyMessage;
import java.math.BigInteger;

public interface Xdag {
    void sendNewBlock(Block newBlock, int TTL);

    void sendGetblocks(long starttime, long endtime);

    void sendGetblock(byte[] hash);

    ListenableFuture<SumReplyMessage> sendGetsums(long starttime, long endtime);

    void dropConnection();

    boolean isIdle();

    BigInteger getTotalDifficulty();

    void activate();

    XdagVersion getVersion();

    /** Disables pending block processing */
    void disableBlocks();

    /** Enables pending block processing */
    void enableBlocks();

    /**
     * Fires inner logic related to long sync done or undone event
     *
     * @param done
     *            true notifies that long sync is finished, false notifies that it's
     *            enabled again
     */
    void onSyncDone(boolean done);

    void sendMessage(Message message);
}
