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
import io.xdag.net.message.MessageCode;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

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
    Set<MessageCode> getNetPrioritizedMessages();
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

    // Whitelist management
    List<InetSocketAddress> getWhiteIPList();
    void setWhiteIPList(List<InetSocketAddress> list);

    // Storage configuration
    String getStoreDir();
    void setStoreDir(String dir);
    String getStoreBackupDir();
    void setStoreBackupDir(String dir);
    String getWhiteListDir();
    String getNetDBDir();
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
