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
package io.xdag.net.message;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.xdag.KernelMock;
import io.xdag.net.Channel;
import io.xdag.net.PeerClient;
import io.xdag.net.PeerServerMock;
import io.xdag.net.XdagChannelInitializer;
import io.xdag.net.message.p2p.PingMessage;
import io.xdag.net.message.p2p.PongMessage;
import io.xdag.net.node.Node;
import io.xdag.rules.KernelRule;
import io.xdag.utils.TimeUtils;

public class MessageQueueTest {

    private PeerServerMock server1;
    private PeerServerMock server2;

    @Rule
    public KernelRule kernelRule1 = new KernelRule(18001);

    @Rule
    public KernelRule kernelRule2 = new KernelRule(18002);

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

    private Channel connect() throws InterruptedException {
        server2 = new PeerServerMock(kernelRule2.getKernel());
        server2.start();

        Node remoteNode = new Node(kernelRule1.getKernel().getConfig().getNodeSpec().getNodeIp(),
                kernelRule1.getKernel().getConfig().getNodeSpec().getNodePort());

        KernelMock kernel2 = kernelRule2.getKernel();
        XdagChannelInitializer ci = new XdagChannelInitializer(kernelRule2.getKernel(), remoteNode);
        PeerClient client = kernelRule2.getKernel().getClient();
        client.connect(remoteNode, ci).sync();

        long maxWaitTime = 30_000;
        long startTime = TimeUtils.currentTimeMillis();
        while (kernel2.getChannelManager().getActiveChannels().isEmpty()) {
            Thread.sleep(100);
            if (TimeUtils.currentTimeMillis() - startTime > maxWaitTime) {
                fail("Took too long to connect peers");
            }
        }
        return kernel2.getChannelManager().getActiveChannels().get(0);
    }

    @Test
    public void testQueueOverflow() throws InterruptedException {
        Channel ch = connect();

        PingMessage msg = new PingMessage();
        assertTrue(ch.getMsgQueue().sendMessage(msg));
        for (int i = 0; i < server1.getKernel().getConfig().getNodeSpec().getNetMaxMessageQueueSize() * 2; i++) {
            ch.getMsgQueue().sendMessage(msg);
        }
        assertFalse(ch.getMsgQueue().sendMessage(msg));

        Thread.sleep(200);
        assertFalse(ch.isActive());
    }

    @Test
    public void testSendRequest() throws InterruptedException {
        Channel ch = connect();

        PingMessage msg = new PingMessage();
        ch.getMsgQueue().sendMessage(msg);

        Thread.sleep(200);
        assertTrue(ch.getMsgQueue().isIdle());
        assertTrue(ch.isActive());
    }

    @Test
    public void testSendResponse() throws InterruptedException {
        Channel ch = connect();

        PongMessage msg = new PongMessage();
        ch.getMsgQueue().sendMessage(msg);

        Thread.sleep(200);
        assertTrue(ch.getMsgQueue().isIdle());
        assertTrue(ch.isActive());
    }
}
