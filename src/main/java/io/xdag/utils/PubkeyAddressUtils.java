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
package io.xdag.utils;

import io.xdag.crypto.Base58;
import io.xdag.utils.exception.AddressFormatException;

public class PubkeyAddressUtils {

    public static String toBase58(byte[] hash160) {
        return Base58.encodeChecked(hash160);
    }

    public static byte[] fromBase58(String base58) throws AddressFormatException {
        byte[] bytes = Base58.decodeChecked(base58);
        if (bytes.length != 20)
            throw new AddressFormatException.InvalidDataLength("Wrong number of bytes: " + bytes.length);
        return bytes;
    }

    public static boolean checkAddress(String base58) {
        return Base58.checkAddress(base58);
    }

    public static boolean checkBytes24(byte[] data) {
        return Base58.checkBytes24(data);
    }

}
