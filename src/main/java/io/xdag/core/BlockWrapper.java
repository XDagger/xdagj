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

import io.xdag.net.Peer;
import lombok.Getter;
import lombok.Setter;

/**
 * Wrapper class for Block that includes additional metadata for block processing
 */
@Getter
@Setter
public class BlockWrapper implements Cloneable {

    // The actual block
    private Block block;
    // Time to live counter
    private int ttl;
    /**
     * The peer node from which this block was received
     */
    private Peer remotePeer;
    // Timestamp for tracking NO_PARENT waiting time
    private long time;
    
    // Flag indicating if this is an old block
    private boolean isOld;

    /**
     * Constructor with all fields
     * @param block The block to wrap
     * @param ttl Time to live value
     * @param remotePeer The peer that sent this block
     * @param isOld Whether this is an old block
     */
    public BlockWrapper(Block block, int ttl, Peer remotePeer, boolean isOld) {
        this.block = block;
        this.ttl = ttl;
        this.remotePeer = remotePeer;
        this.isOld = isOld;
    }

    /**
     * Constructor with only block and ttl
     * @param block The block to wrap
     * @param ttl Time to live value
     */
    public BlockWrapper(Block block, int ttl) {
        this.block = block;
        this.ttl = ttl;
    }

    @Override
    public BlockWrapper clone() {
        try {
            return (BlockWrapper) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
