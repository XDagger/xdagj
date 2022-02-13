package io.xdag.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.mine.handler.ConnectionLimitHandler;

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
