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

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.junit.Test;

import io.xdag.crypto.Hash;

public class BytesTest {

    @Test
    public void testSetMutableBytes32() {
        MutableBytes32 hashlow = MutableBytes32.create();
        Bytes32 hash = Hash.hashTwice(Bytes.wrap("123".getBytes(StandardCharsets.UTF_8)));
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", hashlow.toHexString());
        assertEquals("0x5a77d1e9612d350b3734f6282259b7ff0a3f87d62cfef5f35e91a5604c0490a3", hash.toHexString());
        hashlow.set(8, hash.slice(8, 24));
        assertEquals("0x00000000000000003734f6282259b7ff0a3f87d62cfef5f35e91a5604c0490a3", hashlow.toHexString());
    }

}
