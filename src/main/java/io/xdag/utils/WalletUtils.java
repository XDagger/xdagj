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

import io.xdag.crypto.exception.AddressFormatException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import io.xdag.crypto.encoding.Base58;

/**
 * Utility class for wallet operations including BIP44 key generation, address encoding/decoding and validation
 */
@Slf4j
public class WalletUtils {

    /**
     * Prompt message for wallet password input
     */
    public static final String WALLET_PASSWORD_PROMPT = "Please Enter Wallet Password: ";

    /**
     * Decodes a Base58 string to byte array
     *
     * @param base58 Base58 encoded string
     * @return Decoded byte array
     * @throws AddressFormatException if the input is invalid
     */
    public static Bytes fromBase58(String base58) throws AddressFormatException {
      Bytes bytes = Base58.decodeCheck(base58);

      if (bytes.size() != 20)
            throw new AddressFormatException("Wrong number of bytes: " + bytes.size());
        return bytes;
    }

    /**
     * Validates a Base58 encoded address
     *
     * @param base58 Base58 encoded address
     * @return true if valid, false otherwise
     */
    public static boolean checkAddress(String base58) {
        try {
            Base58.decodeCheck(base58);
        } catch (AddressFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * Validates an address using its hash
     *
     * @param hashlow Lower 32 bytes of address hash
     * @return true if valid, false otherwise
     */
    public static boolean checkAddress(Bytes32 hashlow) {
        return hashlow.slice(28, 4).toInt() == 0;
    }
}