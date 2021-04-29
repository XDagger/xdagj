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


/** Keys generated for unit testing purposes. */
public class SampleKeys {

    public static final String PRIVATE_KEY_STRING =
            "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
    public static final String PUBLIC_KEY_STRING =
            "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aab"
                    + "a645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";
    public static final String ADDRESS = "0xa15339b55796b3585127c180fd4cbc54612122cf";
    public static final String ADDRESS_NO_PREFIX = Numeric.cleanHexPrefix(ADDRESS);

    public static final String PASSWORD = "Insecure Pa55w0rd";
    public static final String MNEMONIC =
            "scatter major grant return flee easy female jungle"
                    + " vivid movie bicycle absent weather inspire carry";

    public static final BigInteger PRIVATE_KEY = Numeric.toBigInt(PRIVATE_KEY_STRING);
    public static final BigInteger PUBLIC_KEY = Numeric.toBigInt(PUBLIC_KEY_STRING);

    public static final ECKeyPair KEY_PAIR = new ECKeyPair(PRIVATE_KEY, PUBLIC_KEY);

    public static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);

    private SampleKeys() {}
}

