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

package io.xdag.rpc.modules;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.rpc.modules.xdag.XdagModuleChain;
import io.xdag.rpc.modules.xdag.XdagModuleTransaction;
import io.xdag.rpc.modules.xdag.XdagModuleWallet;
import org.junit.Test;

public class XdagModuleTest {

    @Test
    public void chainId() {
        XdagModule xdagModule = new XdagModule(
                (byte) 0x21,
                mock(XdagModuleWallet.class),
                mock(XdagModuleTransaction.class),
                mock(XdagModuleChain.class)

        );
        assertEquals("0x21", xdagModule.chainId());
    }

    @Test
    public void sendRawTransaction() {

    }
}
