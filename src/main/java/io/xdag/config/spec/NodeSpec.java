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

import io.xdag.crypto.DnetKeys;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * The Node Specifications
 */
public interface NodeSpec {

    // dnet
    String getNodeIp();

    int getNodePort();

    int getMaxConnections();

    int getMaxInboundConnectionsPerIp();

    int getConnectionReadTimeout();

    byte[] getDnetKeyBytes();

    void setDnetKeyBytes(byte[] dnetKeyBytes);

    DnetKeys getXKeys();

    int getTTL();

    List<InetSocketAddress> getWhiteIPList();

    void setWhiteIPList(List<InetSocketAddress> list);

    // libp2p
    boolean isBootnode();

    List<String> getBootnodes();

    int getLibp2pPort();

    String getLibp2pPrivkey();

    String getDnetKeyFile();

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

    /**
     * 用于测试加载已有区块数据 从C版本生成的数据 请将所需要的数据放在该目录下
     */
    String getOriginStoreDir();

    // White List
    String getWhitelistUrl();

    boolean enableRefresh();

}
