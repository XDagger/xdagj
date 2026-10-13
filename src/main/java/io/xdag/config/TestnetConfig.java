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

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;

import org.apache.tuweni.units.bigints.UInt64;

import io.xdag.Network;
import io.xdag.core.XAmount;

/**
 * Configuration class for the test network (testnet)
 * Extends AbstractConfig to provide testnet-specific settings
 */
public class TestnetConfig extends AbstractConfig {

    /**
     * Constructor initializes testnet configuration with specific parameters:
     * - Network type: TESTNET
     * - Version: TESTNET_VERSION
     * - Wait epoch: 1
     * - XDAG era: 0x16900000000L
     * - Main start amount: 2^42 XDAG
     * - Apollo fork height: 196250
     * - Apollo fork amount: 2^39 XDAG
     * - Field header type: XDAG_FIELD_HEAD_TEST
     * - Wallet file paths for testnet
     */
    public TestnetConfig() {
        super("testnet", "xdag-testnet", Network.TESTNET, Constants.TESTNET_VERSION);
        this.waitEpoch = 1;
        this.xdagEra = 0x16900000000L;
        this.mainStartAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 42).toLong());
        this.apolloForkHeight = 196250;
        this.apolloForkAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 39).toLong());
        this.xdagFieldHeader = XDAG_FIELD_HEAD_TEST;
        this.walletKeyFile = this.rootDir + "/wallet-testnet.dat";
        this.walletFilePath = this.rootDir + "/wallet/" + Constants.WALLET_FILE_NAME;
    }

}
