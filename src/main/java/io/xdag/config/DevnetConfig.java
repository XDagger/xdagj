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

import com.google.common.primitives.UnsignedLong;
import org.apache.commons.lang3.StringUtils;

public class DevnetConfig extends AbstractConfig {

    public DevnetConfig() {
        super("devnet", (byte)1, "xdag-devnet.config");
        this.whitelistUrl = StringUtils.EMPTY;

        this.waitEpoch = 1;

        this.xdagEra = 0x16900000000L;
        this.mainStartAmount = UnsignedLong.fromLongBits(1L << 42).longValue();

        this.apolloForkHeight = 1000;
        this.apolloForkAmount = UnsignedLong.fromLongBits(1L << 39).longValue();
        this.xdagFieldHeader = XDAG_FIELD_HEAD_TEST;

        this.dnetKeyFile = this.rootDir + "/dnet_keys.bin";
        this.walletKeyFile = this.rootDir + "/wallet-testnet.dat";

        this.walletFilePath = this.rootDir + "/wallet/" + Constants.WALLET_FILE_NAME;
    }

}
