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

import static io.xdag.utils.BasicUtils.crc32Verify;
import static org.junit.Assert.assertTrue;

import io.xdag.utils.BytesUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class CRC32Test {

    @Test
    public void testCrc() {
        String reque = "8b050002a353147e3853050000000040ffffc63c7b0100000000000000000000"
                + "c44704e82cf458076643a426a6d35cdb6ff1c168866f00e40000000000000000"
                + "c44704e82cf458076643a426a6d35cdb6ff1c168866f00e40000000000000000"
                + "a31e6283a9f5da4a4f56c3503f3ab27c776fbb2f89f67e20cd875ac0523fe613"
                + "08e2eda6e9eb9b754d96128a5676b52e6cc9f2a2e102e9896fea58864c489c6b"
                + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000097d1511ec1723d118fe19c06ab4ca825a982282a1733b508b6b56f73cc4adeb";

        byte[] uncryptData = Hex.decode(reque);
        int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
        System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
        assertTrue(crc32Verify(uncryptData, crc));
    }
}
