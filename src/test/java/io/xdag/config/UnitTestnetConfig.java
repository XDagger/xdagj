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


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.units.bigints.UInt64;

import io.xdag.Network;
import io.xdag.core.XAmount;
import io.xdag.core.Fork;

public class UnitTestnetConfig extends AbstractConfig {
    public UnitTestnetConfig(String dataDir) {
        super(dataDir,  Network.DEVNET, Constants.DEVNET_VERSION);
        this.whitelistUrl = StringUtils.EMPTY;
//        this.waitEpoch = 1;
//        this.xdagEra = 0x16900000000L;
//        this.mainStartAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 42).toLong());
//        this.apolloForkHeight = 1000;
//        this.apolloForkAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 39).toLong());
//        this.walletKeyFile = this.rootDir + "/wallet-unittest.dat";
//        this.walletFilePath = this.rootDir + "/wallet/" + Constants.WALLET_FILE_NAME;
    }

    @Override
    public Map<Long, byte[]> checkpoints() {
        return Collections.emptyMap();
    }

    @Override
    public Map<Fork, Long> manuallyActivatedForks() {
        Map<Fork, Long> forks = new HashMap<>();
        forks.put(Fork.APOLLO_FORK, 100L);

        return forks;
    }
}
