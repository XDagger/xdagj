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

import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import org.apache.tuweni.units.bigints.UInt64;

public class Constants {

    public static final long MAIN_CHAIN_PERIOD = 64 << 10;

    /**
     * Flag set when block is marked as main block
     */
    public static final byte BI_MAIN = 0x01;
    /**
     * Similar to BI_MAIN but indicates block is not yet confirmed as main block
     */
    public static final byte BI_MAIN_CHAIN = 0x02;
    /**
     * Flag set after block is applied (may be set even if block has issues but was referenced)
     */
    public static final byte BI_APPLIED = 0x04;
    /**
     * Flag set after block is applied
     */
    public static final byte BI_MAIN_REF = 0x08;
    /**
     * Flag set when orphan block is removed after being linked by another block
     */
    public static final byte BI_REF = 0x10;
    /**
     * Flag set when block signature can be verified with own public key
     */
    public static final byte BI_OURS = 0x20;
    /**
     * Flag for candidate main block not yet persisted
     */
    public static final byte BI_EXTRA = 0x40;
    public static final byte BI_REMARK = (byte) 0x80;
    public static final Long SEND_PERIOD = 10L;

    public static final long REQUEST_BLOCKS_MAX_TIME = UInt64.valueOf(1L << 20).toLong();
    public static final long REQUEST_WAIT = 64;
    public static final long MAX_ALLOWED_EXTRA = 65536;
    /**
     * Number of confirmations per round is 16
     */
    public static final int CONFIRMATIONS_COUNT = 16;
    public static final int MAIN_BIG_PERIOD_LOG = 21;

    public static final String WALLET_FILE_NAME = "wallet.data";

    public static final String CLIENT_NAME = "xdagj";

    public static final String CLIENT_VERSION = System.getProperty("xdagj.version");

    /**
     * Fork height for sync issue resolution
     */
    public static final Long SYNC_FIX_HEIGHT = 0L;

    public static final int HASH_RATE_LAST_MAX_TIME = 32;

    public enum MessageType {
        UPDATE,
        PRE_TOP,
        NEW_LINK
    }

    public static final short MAINNET_VERSION = 0;
    public static final short TESTNET_VERSION = 0;
    public static final short DEVNET_VERSION = 0;

    public static final XAmount MIN_GAS = XAmount.of(100, XUnit.MILLI_XDAG);

}
