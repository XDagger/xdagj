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
package io.xdag.net;

import static org.awaitility.Awaitility.await;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.xdag.KernelMock;
import io.xdag.net.node.Node;
import io.xdag.rules.KernelRule;

public class PeerClientTest {

    private PeerServerMock server1;
    private PeerServerMock server2;

    @Rule
    public KernelRule kernelRule1 = new KernelRule(8001);

    @Rule
    public KernelRule kernelRule2 = new KernelRule(8002);

    @Before
    public void setUp() {
        server1 = new PeerServerMock(kernelRule1.getKernel());
        server1.start();
    }

    @After
    public void tearDown() {
        if (server1 != null) {
            server1.stop();
        }
        if (server2 != null) {
            server2.stop();
        }
    }

    @Test
    public void testConnect() throws InterruptedException {
        server2 = new PeerServerMock(kernelRule2.getKernel());
        server2.start();

        Node remoteNode = new Node(kernelRule1.getKernel().getConfig().getNodeSpec().getNodeIp(),
                kernelRule1.getKernel().getConfig().getNodeSpec().getNodePort());

        KernelMock kernel2 = kernelRule2.getKernel();
        XdagChannelInitializer ci = new XdagChannelInitializer(kernelRule2.getKernel(), remoteNode);
        PeerClient client = kernelRule2.getKernel().getClient();
        client.connect(remoteNode, ci).sync();

        // waiting for the HELLO message to be sent
        await().until(() -> 1 == kernel2.getChannelManager().getActivePeers().size());

        client.close();
        await().until(() -> 0 == kernel2.getChannelManager().getActivePeers().size());
    }
}
