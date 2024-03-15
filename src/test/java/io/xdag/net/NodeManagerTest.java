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

import static org.junit.Assert.assertTrue;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;

import java.net.InetSocketAddress;
import java.util.List;

import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class NodeManagerTest {

    Config config = new DevnetConfig();

    @Before
    public void setUp() throws Exception {
        String[] list = new String[]{"127.0.0.1:1001", "127.0.0.1:1002"};
        List<InetSocketAddress> addressList = Lists.newArrayList();
        for (String address : list) {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(address.split(":")[0],Integer.parseInt(address.split(":")[1]));
            addressList.add(inetSocketAddress);
        }
        config.getNodeSpec().setWhiteIPList(addressList);
    }

    @Test
    public void testWhiteList() {
        NetDBManager netDBMgr = new NetDBManager(this.config);
        netDBMgr.init();
        assertTrue(netDBMgr.canAccept(new InetSocketAddress("127.0.0.1",1001)));
        assertTrue(netDBMgr.canAccept(new InetSocketAddress("127.0.0.1",1002)));
    }

}
