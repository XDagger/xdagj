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
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.keys.AddressUtils;
import io.xdag.crypto.exception.AddressFormatException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import io.xdag.crypto.keys.ECKeyPair;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class PubkeyAddressUtilsTest {

    @Test
    public void pulkeyAddressTest()
        throws AddressFormatException {
        ECKeyPair key = ECKeyPair.generate();
        Bytes hash160 = AddressUtils.toBytesAddress(key);
        String base58 = Base58.encodeCheck(hash160);
        assertEquals(WalletUtils.fromBase58(base58), hash160);
    }

    @Test(expected = AddressFormatException.class)
    public void testAddressFormatException() throws AddressFormatException {
        //the correct base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C"
        String base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2a";
        WalletUtils.fromBase58(base58);
    }

    @Test
    public void testCheckAddress() {
        assertTrue(WalletUtils.checkAddress("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C"));
        assertFalse(WalletUtils.checkAddress("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2a"));
    }

    @Test
    public void testToByte32() throws AddressFormatException {
        String addressStr = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C";
        byte[] addressbyte = WalletUtils.fromBase58(addressStr).toArray();
        MutableBytes32 address= BytesUtils.arrayToByte32(addressbyte);
        String res = Base58.encodeCheck(BytesUtils.byte32ToArray(address));
        Assert.assertEquals(addressStr, res);
    }
}
