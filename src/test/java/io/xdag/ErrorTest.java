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
package io.xdag;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class ErrorTest {
    @Test
    public void BlockErrorTest() {
        String block = "00000000000000003853050000000040ffffe14e7b0100000000000000000000"
                + "9d6454080878ab3f95494c9eca65df18fb5f97389dde3cdb0000000000000000"
                + "bfd32b0da1b55f82ab08b33fc4b2155ec13f69d4d111d6c00000000000000000"
                + "3210785bcb09fcf17cb689824eb9f6d489e15b94ab8021c2cd30ed39a4b27bb7"
                + "209bce4173da897576890ef8b582c13c845dda5adb653ff5648b4977ab8c94b6"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "f52f741028c02234c4e67299a45a04f9f49633263f55b59797c47f39e0ffb5a0";

        Block b = new Block(new XdagBlock(Hex.decode(block)));
        System.out.println(Hex.toHexString(b.getHash()));
        // 0000000cfa69cb843f542fd77321ecd411833c70cdca517c81723b2c3a4e7ee5
    }
}
