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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;

import io.xdag.crypto.Keys;
import io.xdag.crypto.Sign;
import io.xdag.utils.Numeric;

public class Constants {

    public static final String DEFAULT_ROOT_DIR = ".";
    public static final String CLIENT_NAME = "xdagj";
    public static final String CHAIN_DIR = "chaindata";
    public static final String CONFIG_DIR = "config";
    public static final String WALLET_DIR = "wallet";
    public static final String LOG_DIR = "log";

    public static final String CLIENT_VERSION = System.getProperty("xdagj.version");


    public static final short MAINNET_VERSION = 0;
    public static final short TESTNET_VERSION = 0;
    public static final short DEVNET_VERSION = 0;

    public static final String COINBASE_PRIVATE_KEY_STRING = "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

    /**
     * The number of epoch finaliza
     */
    public static final long EPOCH_FINALIZE_NUMBER = 16;

    /**
     * Page size of address in commands
     */
    public static final int COMMANDS_ADDRESS_TX_PAGE_SIZE = 50;

    /**
     * The number of blocks per day.
     */
    public static final long MAIN_BLOCKS_PER_DAY = (3600 * 24) / 64;

    /**
     * The number of blocks per year.
     */
    public static final long MAIN_BLOCKS_PER_YEAR = (3600 * 24 * 365) / 64;

    /**
     * The public-private key pair for signing coinbase transactions.
     */
    public static final KeyPair COINBASE_KEY;

    /**
     * Address bytes of {@link this#COINBASE_KEY}. This is stored as a cache to
     * avoid redundant h160 calls.
     */
    public static final byte[] COINBASE_ADDRESS;

    /** Standard maximum value for difficultyTarget (nBits) (Bitcoin MainNet and TestNet) */
    public static final long STANDARD_MAX_DIFFICULTY_TARGET = 0x1d00ffffL;

    /** A value for difficultyTarget (nBits) that allows (slightly less than) half of all possible hash solutions. Used in unit testing. */
    public static final long EASIEST_DIFFICULTY_TARGET = 0x207fFFFFL;

    static {
        SECPPrivateKey perivkey = SECPPrivateKey.create(Numeric.toBigInt(COINBASE_PRIVATE_KEY_STRING), Sign.CURVE_NAME);
        COINBASE_KEY = KeyPair.create(perivkey, Sign.CURVE, Sign.CURVE_NAME);
        COINBASE_ADDRESS = Keys.toBytesAddress(COINBASE_KEY);
    }

    public static void main(String[] args) {
        System.out.println();
    }

}
