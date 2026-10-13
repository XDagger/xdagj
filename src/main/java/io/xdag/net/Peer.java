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
package io.xdag.net;

import io.xdag.Network;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a peer node in the XDAG network
 */
@Getter
public class Peer {

    // Network instance this peer belongs to
    private final Network network;
    
    // Protocol version used by this peer
    private final short networkVersion;
    
    // Unique identifier for this peer
    private final String peerId;
    
    // IP address of the peer
    private final String ip;
    
    // Port number used by the peer
    private final int port;
    
    // Client software identifier
    private final String clientId;
    
    // Supported capabilities/features
    private final String[] capabilities;
    
    // Latest block number known by this peer
    @Setter
    private long latestBlockNumber;
    
    // Network latency to this peer in milliseconds
    @Setter
    private long latency;

    private final boolean isGenerateBlock;

    private final String nodeTag;

    /**
     * Creates a new Peer instance
     *
     * @param network Network instance
     * @param networkVersion Protocol version
     * @param peerId Unique peer identifier
     * @param ip IP address
     * @param port Port number
     * @param clientId Client software identifier
     * @param capabilities Supported capabilities
     * @param latestBlockNumber Latest known block number
     */
    public Peer(
            Network network,
            short networkVersion,
            String peerId,
            String ip,
            int port,
            String clientId,
            String[] capabilities,
            long latestBlockNumber,
            boolean isGenerateBlock,
            String nodeTag
    ) {
        this.network = network;
        this.ip = ip;
        this.port = port;
        this.peerId = peerId;
        this.networkVersion = networkVersion;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.latestBlockNumber = latestBlockNumber;
        this.isGenerateBlock = isGenerateBlock;
        this.nodeTag = nodeTag;
    }

    /**
     * Returns string representation of peer in format: peerId@ip:port
     */
    @Override
    public String toString() {
        return getPeerId() + "@" + ip + ":" + port + ", NodeTag = " + this.nodeTag + ", GenerateBlock = " +
                this.isGenerateBlock;
    }
}