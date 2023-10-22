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

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Before;
import org.junit.Test;

import io.xdag.TestUtils;
import io.xdag.core.XUnit;

public class MainnetConfigTest {

    private Config config;
    private String whitelistUrl;

    @Before
    public void setUp() {
        config = new MainnetConfig(Constants.DEFAULT_ROOT_DIR);
        whitelistUrl = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white.txt";
    }

    @Test
    public void testBlockReward() {
        assertEquals("1024.00", config.getDagSpec().getMainBlockReward(1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("1024.00", config.getDagSpec().getMainBlockReward(1_017_323L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("1024.00", TestUtils.getOldReward(1_017_323L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("128.00", config.getDagSpec().getMainBlockReward(1_017_323L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("128.00", TestUtils.getOldReward(1_017_323L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("128.00", config.getDagSpec().getMainBlockReward(2_097_152L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("128.00", TestUtils.getOldReward(2_097_152L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("64.00", config.getDagSpec().getMainBlockReward(2_097_152L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("64.00", TestUtils.getOldReward(2_097_152L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("64.00",  config.getDagSpec().getMainBlockReward(4_194_304L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("64.00", TestUtils.getOldReward(4_194_304L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("32.00",  config.getDagSpec().getMainBlockReward(4_194_304L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("32.00", TestUtils.getOldReward(4_194_304L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("32.00",  config.getDagSpec().getMainBlockReward(6_291_456L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("32.00", TestUtils.getOldReward(6_291_456L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("16.00",  config.getDagSpec().getMainBlockReward(6_291_456L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("16.00", TestUtils.getOldReward(6_291_456L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("16.00",  config.getDagSpec().getMainBlockReward(8_388_608L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("16.00", TestUtils.getOldReward(8_388_608L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("8.00",  config.getDagSpec().getMainBlockReward(8_388_608L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("8.00", TestUtils.getOldReward(8_388_608L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("8.00",  config.getDagSpec().getMainBlockReward(10_485_760L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("8.00", TestUtils.getOldReward(10_485_760L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("4.00",  config.getDagSpec().getMainBlockReward(10_485_760L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("4.00", TestUtils.getOldReward(10_485_760L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("4.00",  config.getDagSpec().getMainBlockReward(12_582_912L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("4.00", TestUtils.getOldReward(12_582_912L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("2.00",  config.getDagSpec().getMainBlockReward(12_582_912L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("2.00", TestUtils.getOldReward(12_582_912L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("2.00",  config.getDagSpec().getMainBlockReward(14_680_064L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("2.00", TestUtils.getOldReward(14_680_064L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("1.00",  config.getDagSpec().getMainBlockReward(14_680_064L).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("1.00", TestUtils.getOldReward(14_680_064L).toDecimal(2, XUnit.XDAG).toPlainString());

        assertEquals("1.00",  config.getDagSpec().getMainBlockReward(16_777_216L - 1).toDecimal(2, XUnit.XDAG).toPlainString());
        assertEquals("1.00", TestUtils.getOldReward(16_777_216L - 1).toDecimal(2, XUnit.XDAG).toPlainString());

    }

    @Test
    public void testParams() {
        assertEquals(".", config.rootDir().getName());
        assertEquals(Constants.DEFAULT_ROOT_DIR + File.separator + Constants.CHAIN_DIR + File.separator + "mainnet", config.chainDir().getPath());
        assertEquals(Constants.CONFIG_DIR, config.configDir().getName());
        assertEquals(Constants.WALLET_DIR, config.walletDir().getName());
        assertEquals(Constants.LOG_DIR, config.logDir().getName());
    }

}
