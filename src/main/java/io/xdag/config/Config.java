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

package io.xdag.config;

import io.xdag.config.spec.AdminSpec;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.PoolSpec;
import io.xdag.config.spec.RPCSpec;
import io.xdag.config.spec.RandomxSpec;
import io.xdag.config.spec.SnapshotSpec;
import io.xdag.config.spec.WalletSpec;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;

/**
 * The Xdag blockchain configurations.
 */
public interface Config {

    /**
     * Config File Name.
     */
    String getConfigName();

    /**
     * Config Root Dir.
     */
    String getRootDir();

    /**
     * Pool Specification.
     */
    PoolSpec getPoolSpec();

    /**
     * Node Specification.
     */
    NodeSpec getNodeSpec();

    /**
     * Admin Specification.
     */
    AdminSpec getAdminSpec();

    /**
     * Wallet Specification.
     */
    WalletSpec getWalletSpec();

    XAmount getMainStartAmount();

    long getXdagEra();

    long getApolloForkHeight();

    XAmount getApolloForkAmount();

    XdagField.FieldType getXdagFieldHeader();

    void changePara(String[] args);

    void setDir();

    void initKeys() throws Exception;

    // rpc
    RPCSpec getRPCSpec();

    // snapshot
    SnapshotSpec getSnapshotSpec();

    RandomxSpec getRandomxSpec();
}
