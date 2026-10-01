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

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD;

import org.apache.tuweni.units.bigints.UInt64;

import io.xdag.Network;
import io.xdag.core.XAmount;

/**
 * Configuration class for the main network (mainnet)
 * Extends AbstractConfig to provide mainnet-specific settings
 */
public class MainnetConfig extends AbstractConfig {

    /**
     * Activation epoch of the open-network hardening fork on mainnet.
     * <p>
     * Not scheduled yet: until the maintainers publish a release with an agreed epoch, mainnet keeps the 0.8.x
     * consensus rules and the node only talks to the peers listed in its configuration (see docs/OPEN_NETWORK.md).
     */
    public static final long OPEN_NET_FORK_EPOCH = Constants.OPEN_NET_FORK_NOT_SCHEDULED;

    /**
     * Constructor initializes mainnet configuration with specific parameters:
     * - Network type: MAINNET
     * - Version: MAINNET_VERSION
     * - XDAG era: 0x16940000000L
     * - Main start amount: 2^42 XDAG
     * - Apollo fork height: 1017323
     * - Apollo fork amount: 2^39 XDAG
     * - Field header type: XDAG_FIELD_HEAD
     * - Wallet file paths for mainnet
     */
    public MainnetConfig() {
        super("mainnet", "xdag-mainnet", Network.MAINNET, Constants.MAINNET_VERSION);
        this.network = Network.MAINNET;
        this.xdagEra = 0x16940000000L;
        this.mainStartAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 42).toLong());
        this.apolloForkHeight = 1017323;
        this.apolloForkAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 39).toLong());
        this.xdagFieldHeader = XDAG_FIELD_HEAD;
        this.walletKeyFile = this.rootDir + "/wallet.dat";
        this.walletFilePath = this.rootDir + "/wallet/" + Constants.WALLET_FILE_NAME;
        // A consensus parameter: it ships with the release and cannot be set per node.
        this.openNetForkEpoch = OPEN_NET_FORK_EPOCH;
    }

}
