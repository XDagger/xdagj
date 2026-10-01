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
package io.xdag.net.node;

import io.xdag.Kernel;
import io.xdag.core.AbstractXdagLifecycle;
import lombok.extern.slf4j.Slf4j;

/**
 * Operator-driven connections. Finding and keeping peers is the job of the P2P layer (seeds, discovery,
 * reconnection); what is left here is the {@code connect} command.
 */
@Slf4j
public class NodeManager extends AbstractXdagLifecycle {

    private final Kernel kernel;

    public NodeManager(Kernel kernel) {
        this.kernel = kernel;
    }

    @Override
    protected synchronized void doStart() {
        log.debug("Node manager started");
    }

    @Override
    protected synchronized void doStop() {
        log.debug("Node manager stop...");
    }

    public void doConnect(String ip, int port) {
        kernel.getChannelMgr().connect(ip, port);
    }
}
