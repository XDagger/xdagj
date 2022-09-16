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
package io.xdag.mine.handler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectionLimitHandlerTest {
    public static final String TEST_IP_STR = "192.168.1.1";

    @Test
    public void testInboundExceedingLimit() throws Exception {
        final int limit = 10, loops = 100;

        // create connections
        for (int i = 1; i <= loops; i++) {
            ConnectionLimitHandler handler = new ConnectionLimitHandler(limit);
            ChannelHandlerContext channelHandlerContext = mockChannelContext(TEST_IP_STR, 1024 + i);
            handler.channelActive(channelHandlerContext);

            if (i > limit) {
                verify(channelHandlerContext).close();
            } else {
                verify(channelHandlerContext, never()).close();
            }
        }
        assertEquals(loops, ConnectionLimitHandler.getConnectionsCount(InetAddress.getByName(TEST_IP_STR)));
        assertTrue(ConnectionLimitHandler.containsAddress(InetAddress.getByName(TEST_IP_STR)));

        // close connections
        for (int i = 1; i <= loops; i++) {
            ConnectionLimitHandler handler = new ConnectionLimitHandler(limit);
            ChannelHandlerContext channelHandlerContext = mockChannelContext(TEST_IP_STR, 1024 + i);
            handler.channelInactive(channelHandlerContext);
        }
        assertEquals(0, ConnectionLimitHandler.getConnectionsCount(InetAddress.getByName(TEST_IP_STR)));

        // ensure that the address has been removed from the hash map
        assertFalse(ConnectionLimitHandler.containsAddress(InetAddress.getByName(TEST_IP_STR)));
    }

    @Test
    public void testInboundExceedingLimitAsync() throws Exception {
        final int limit = 8, loops = 100;

        ExecutorService executorServiceActive = Executors.newFixedThreadPool(100);
        ExecutorService executorServiceInactive = Executors.newFixedThreadPool(100);

        // create connections
        for (int i = 1; i <= loops; i++) {
            final int j = i;
            executorServiceActive.submit(new Thread(() -> {
                ConnectionLimitHandler handler = new ConnectionLimitHandler(limit);
                ChannelHandlerContext channelHandlerContext = mockChannelContext(TEST_IP_STR, 1024 + j);
                try {
                    handler.channelActive(channelHandlerContext);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }
        executorServiceActive.shutdown();
        executorServiceActive.awaitTermination(5, TimeUnit.SECONDS);

        // close connections
        for (int i = 1; i <= loops - limit; i++) {
            final int j = i;
            final ConnectionLimitHandler handler = new ConnectionLimitHandler(limit);
            executorServiceInactive.submit(new Thread(() -> {
                ChannelHandlerContext channelHandlerContext = mockChannelContext(TEST_IP_STR, 1024 + j);
                try {
                    handler.channelInactive(channelHandlerContext);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }
        executorServiceInactive.shutdown();
        executorServiceInactive.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(limit, ConnectionLimitHandler.getConnectionsCount(InetAddress.getByName(TEST_IP_STR)));
    }

    private ChannelHandlerContext mockChannelContext(String ip, int port) {
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress(ip, port));
        when(channelHandlerContext.channel()).thenReturn(channel);
        return channelHandlerContext;
    }

    @After
    public void tearDown() {
        ConnectionLimitHandler.reset();
    }

}
