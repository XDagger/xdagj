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
package io.xdag.net.handler;

import io.xdag.core.Block;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.Message;

public interface Xdag {

    void sendNewBlock(Block newBlock, int ttl);

    long sendGetBlocks(long startTime, long endTime);

    long sendGetBlock(byte[] hash);

    long sendGetSums(long startTime, long endTime);

    void dropConnection() ;

    boolean isIdle();

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

    void updateXdagStats(AbstractMessage message);
}
