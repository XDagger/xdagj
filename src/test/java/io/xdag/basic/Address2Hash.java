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
package io.xdag.basic;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.hash2Address;
import static org.junit.Assert.assertTrue;

public class Address2Hash {

    @Test
    public void TestHash2Address() {
        String news = "42cLWCMWZDKPZM8WJfpmI7Lbe3p83U2l";
        String originhash = "4aa1ab5742feb010a54ddd7c7a7bdbb22366fa2516cf648f32641623580b67e3";
        byte[] hash1 = Hex.decode(originhash);
        assertTrue(hash2Address(hash1).equals(news));
    }

    @Test
    public void TestAddress2Hash() {
        String news = "42cLWCMWZDKPZM8WJfpmI7Lbe3p83U2l";
        String originhashlow = "0000000000000000a54ddd7c7a7bdbb22366fa2516cf648f32641623580b67e3";
        byte[] hashlow = address2Hash(news);
        assertTrue(Hex.toHexString(hashlow).equals(originhashlow));
    }
}
