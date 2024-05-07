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
 * The Node Specifications
 */
public interface NodeSpec {

    Network getNetwork();

    short getNetworkVersion();

    String getNodeTag();

    int getWaitEpoch();

//    int getNetMaxMessageQueueSize();

    int getNetHandshakeExpiry();

    Set<MessageCode> getNetPrioritizedMessages();

    int getNetMaxInboundConnectionsPerIp();

    int getNetMaxInboundConnections();

    int getNetChannelIdleTimeout();

    String getNodeIp();

    int getNodePort();

    int getMaxConnections();

    int getMaxInboundConnectionsPerIp();

    int getConnectionReadTimeout();

    int getConnectionTimeout();

    int getTTL();
    int getAwardEpoch();

    List<InetSocketAddress> getWhiteIPList();

    void setWhiteIPList(List<InetSocketAddress> list);

    String getStoreDir();

    // Store
    void setStoreDir(String dir);

    String getStoreBackupDir();

    void setStoreBackupDir(String dir);

    String getWhiteListDir();

    String getNetDBDir();

    int getStoreMaxOpenFiles();

    int getStoreMaxThreads();

    boolean isStoreFromBackup();

    int getNetMaxFrameBodySize();

    int getNetMaxPacketSize();

    // White List
    String getWhitelistUrl();
    //reject transaction address;
    String getRejectAddress();
    boolean enableRefresh();
    double getNodeRation();

}
