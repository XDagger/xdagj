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

package io.xdag.net.handler;

import io.xdag.Kernel;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;

public class XdagHandlerFactoryImpl implements XdagHandlerFactory {

    protected XdagChannel channel;
    protected Kernel kernel;

    public XdagHandlerFactoryImpl(Kernel kernel, XdagChannel channel) {
        this.kernel = kernel;
        this.channel = channel;
    }

    @Override
    public XdagHandler create(XdagVersion version) {
        if (version == XdagVersion.V03) {
            return new Xdag03(kernel, channel);
        }
        throw new IllegalArgumentException("Xdag " + version + " is not supported");
    }
}
