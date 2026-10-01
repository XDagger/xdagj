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

package io.xdag.config.spec;

import io.xdag.Network;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Interface for node configuration specifications
 * Defines methods to access node parameters and network settings
 */
public interface NodeSpec {

    // Network related methods
    Network getNetwork();
    short getNetworkVersion();
    String getNodeTag();
    int getWaitEpoch();

    // Commented out as appears deprecated
    // int getNetMaxMessageQueueSize();

    // Network handshake and messaging
    int getNetHandshakeExpiry();
    int getNetMaxInboundConnectionsPerIp();
    int getNetMaxInboundConnections();
    int getNetChannelIdleTimeout();

    // Node connection settings
    String getNodeIp();
    int getNodePort();
    int getMaxConnections();
    int getMaxInboundConnectionsPerIp();
    int getConnectionReadTimeout();
    int getConnectionTimeout();

    // Node operation parameters
    int getTTL();
    int getAwardEpoch();

    // Peers (see docs/OPEN_NETWORK.md)

    /** Entry points into the network ({@code node.seeds}): dialled to find peers, no other privilege. */
    List<InetSocketAddress> getSeedNodes();

    /**
     * Peers this node always stays connected to and never bans ({@code node.trustedPeers}). While the network
     * is closed (open-network fork not yet in force), seeds and trusted peers are the only peers.
     */
    List<InetSocketAddress> getTrustedNodes();

    /** Whether nodes are looked for by discovery (UDP Kademlia) once the network is open. */
    boolean isDiscoveryEnabled();

    /** Whether peers on private / loopback addresses learnt from others may be contacted (test networks). */
    boolean isAllowPrivateAddresses();

    /** Local address to listen on (empty: all interfaces). */
    String getNodeBindIp();

    /** File that holds the node's network key (created on first start). */
    String getNodeKeyFile();

    int getMinConnections();

    int getMaxInboundConnections();

    // Storage configuration
    String getStoreDir();
    void setStoreDir(String dir);
    String getStoreBackupDir();
    void setStoreBackupDir(String dir);
    int getStoreMaxOpenFiles();
    int getStoreMaxThreads();
    boolean isStoreFromBackup();

    // Network packet settings
    int getNetMaxFrameBodySize();
    int getNetMaxPacketSize();

    String getRejectAddress(); // Address for rejected transactions
    
    // There appears to be a typo in method name - should be "getNodeRatio"
    double getNodeRation();

}
