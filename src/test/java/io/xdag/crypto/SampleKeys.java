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

package io.xdag.crypto;

import io.xdag.utils.Numeric;
import java.math.BigInteger;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;

/**
 * Keys generated for unit testing purposes.
 */
public class SampleKeys {

    public static final String PRIVATE_KEY_STRING =
            "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
    public static final String PUBLIC_KEY_STRING =
            "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aab"
                    + "a645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";

    public static final String PUBLIC_KEY_COMPRESS_STRING = "02506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba";
    public static final String ADDRESS = "0xb731bf10ed204f4ebc3d32ac88b7aa61b993fd59";
    public static final String ADDRESS_NO_PREFIX = Numeric.cleanHexPrefix(ADDRESS);

    public static final String PASSWORD = "Insecure Pa55w0rd";
    public static final String MNEMONIC =
            "scatter major grant return flee easy female jungle"
                    + " vivid movie bicycle absent weather inspire carry";

    public static final BigInteger PRIVATE_KEY = Numeric.toBigInt(PRIVATE_KEY_STRING);
    public static final BigInteger PUBLIC_KEY = Numeric.toBigInt(PUBLIC_KEY_STRING);

    public static final SECPPrivateKey SRIVATE_KEY = SECPPrivateKey.create(PRIVATE_KEY, Sign.CURVE_NAME);
    public static final SECPPublicKey SPUBLIC_KEY = SECPPublicKey.create(PUBLIC_KEY, Sign.CURVE_NAME);

    public static final KeyPair KEY_PAIR = new KeyPair(SRIVATE_KEY, SPUBLIC_KEY);

    private SampleKeys() {
    }
}

