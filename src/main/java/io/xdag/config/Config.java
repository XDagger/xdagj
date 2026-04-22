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

import io.xdag.config.spec.*;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.net.CapabilityTreeSet;

import java.util.List;

/**
 * Interface for XDAG blockchain configurations.
 * Provides methods to access various configuration settings and specifications.
 */
public interface Config {

    /**
     * Get the configuration file name
     */
    String getConfigName();

    /**
     * Get the client identifier
     */
    String getClientId();

    /**
     * Get the client capabilities tree set
     */
    CapabilityTreeSet getClientCapabilities();

    /**
     * Get the root directory path for configuration
     */
    String getRootDir();

    /**
     * Get the node specification configuration
     */
    NodeSpec getNodeSpec();

    /**
     * Get the admin specification configuration
     */
    AdminSpec getAdminSpec();

    /**
     * Get the wallet specification configuration
     */
    WalletSpec getWalletSpec();

    /**
     * Get the initial amount for main network
     */
    XAmount getMainStartAmount();

    /**
     * Get the XDAG era value
     */
    long getXdagEra();

    /**
     * Get the Apollo fork block height
     */
    long getApolloForkHeight();

    /**
     * Get the Apollo fork amount
     */
    XAmount getApolloForkAmount();

    /**
     * Get the XDAG field header type
     */
    XdagField.FieldType getXdagFieldHeader();

    /**
     * Change parameters based on command line arguments
     */
    void changePara(String[] args);

    /**
     * Set the directory paths
     */
    void setDir();

    /**
     * Get the RPC specification configuration
     */
    RPCSpec getRPCSpec();

    /**
     * Get the snapshot specification configuration
     */
    SnapshotSpec getSnapshotSpec();

    /**
     * Get the RandomX specification configuration
     */
    RandomxSpec getRandomxSpec();

    /**
     * Check if transaction history is enabled
     */
    boolean getEnableTxHistory();

    /**
     * Check if block generation is enabled
     */
    boolean getEnableGenerateBlock();

    /**
     * Get the transaction page size limit
     */
    long getTxPageSizeLimit();

    /**
     * Get the pool whitelist IP addresses
     */
    List<String> getPoolWhiteIPList();

    /**
     * Get the websocket server port
     */
    int getWebsocketServerPort();

    /**
     * Get the fund specification configuration
     */
    FundSpec getFundSpec();

    String getNodeTag();

}
