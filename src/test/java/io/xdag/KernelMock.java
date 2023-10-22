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
package io.xdag;

import org.hyperledger.besu.crypto.KeyPair;

import io.xdag.config.Config;
import io.xdag.consensus.XdagPow;
import io.xdag.core.Dagchain;
import io.xdag.core.Genesis;
import io.xdag.core.PendingManager;
import io.xdag.consensus.XdagSync;
import io.xdag.net.ChannelManager;
import io.xdag.net.PeerClient;
import io.xdag.net.node.NodeManager;

/**
 * This kernel mock extends the {@link DagKernel} by adding a bunch of setters of
 * the components.
 */
public class KernelMock extends DagKernel {

    /**
     * Creates a kernel mock with the given configuration, wallet and coinbase.
     */
    public KernelMock(Config config, Genesis genesis, Wallet wallet) {
        super(config, genesis, wallet, wallet.getDefKey());
    }

    /**
     * Sets the blockchain instance.
     */
    public void setDagchain(Dagchain dagchain) {
        this.dagchain = dagchain;
    }

    /**
     * Sets the peer client instance.
     */
    public void setClient(PeerClient client) {
        this.client = client;
    }

    /**
     * Sets the pending manager instance.
     */
    public void setPendingManager(PendingManager pendingManager) {
        this.pendingManager = pendingManager;
    }

    /**
     * Sets the channel manager instance.
     */
    public void setChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    /**
     * Sets the node manager instance.
     */
    public void setNodeManager(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    /**
     * Sets the sync manager instance.
     */
    public void setSyncManager(XdagSync xdagSync) {
        this.xdagSync = xdagSync;
    }

    /**
     * Sets the pow manager instance.
     */
    public void setPowManager(XdagPow xdagPow) {
        this.xdagPow = xdagPow;
    }

    /**
     * Sets the configuration instance.
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    /**
     * Sets the coinbase.
     */
    public void setCoinbase(KeyPair coinbase) {
        this.coinbase = coinbase;
    }

}
