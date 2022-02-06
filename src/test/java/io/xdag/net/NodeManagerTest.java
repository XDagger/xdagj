package io.xdag.net;

import static org.junit.Assert.assertTrue;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.node.NodeManager;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class NodeManagerTest {

    Config config = new DevnetConfig();
    Kernel kernel;

    @Before
    public void setUp() throws Exception {
        String[] list = new String[]{"127.0.0.1:1001", "127.0.0.1:1002"};
        List<InetSocketAddress> addressList = new ArrayList<>();
        for (String address : list) {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(address.split(":")[0],Integer.parseInt(address.split(":")[1]));
            addressList.add(inetSocketAddress);
        }
        config.getNodeSpec().setWhiteIPList(addressList);
        kernel = new Kernel(config);
    }

    @Test
    public void testWhiteList() {
        NetDBManager netDBMgr = new NetDBManager(this.config);
        netDBMgr.init();
        assertTrue(netDBMgr.canAccept(new InetSocketAddress("127.0.0.1",1001)));
        assertTrue(netDBMgr.canAccept(new InetSocketAddress("127.0.0.1",1002)));
    }

}
