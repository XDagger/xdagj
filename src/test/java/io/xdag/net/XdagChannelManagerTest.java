package io.xdag.net;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.net.manager.XdagChannelManager;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class XdagChannelManagerTest {


    Config config = new DevnetConfig();
    Kernel kernel;

    @Before
    public void setUp() throws Exception {
        String[] list = new String[]{"127.0.0.1:1001","127.0.0.1:1002"};
        config.getNodeSpec().setWhiteIPList(Arrays.asList(list));
        kernel = new Kernel(config);
    }

    @Test
    public void testIp() {
        XdagChannelManager channelManager = new XdagChannelManager(kernel);
        InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1",1001);
        assertTrue(channelManager.isAcceptable(inetSocketAddress));
    }
}
