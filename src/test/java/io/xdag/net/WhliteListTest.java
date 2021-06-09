package io.xdag.net;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

public class WhliteListTest {
    Kernel kernel;
    @Before
    public void setup(){
        Config config = new DevnetConfig();
        kernel = new Kernel(config);
    }

    @Test
    public void WhileList() {
        XdagClient client = new XdagClient(kernel.getConfig());
        client.addWhilteIP("127.0.0.1",8882);
        //白名单有的节点
        boolean ans = client.isAcceptable(new InetSocketAddress("127.0.0.1",8882));
        assert ans;
        //白名单无的节点
        boolean ans1 = client.isAcceptable(new InetSocketAddress("127.0.0.1",8883));
        assert !ans1;
    }
}
