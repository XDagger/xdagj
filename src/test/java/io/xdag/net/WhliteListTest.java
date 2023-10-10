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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Keys;

import java.net.InetSocketAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class WhliteListTest {

    Config config = new DevnetConfig(Constants.DEFAULT_ROOT_DIR);

    @Before
    public void setup() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // 初始化白名单
        String[] list = new String[]{"124.34.34.1:1001", "127.0.0.1:1002"};
        List<InetSocketAddress> addressList = Lists.newArrayList();
        for (String address : list) {
            addressList.add(new InetSocketAddress(address.split(":")[0],Integer.parseInt(address.split(":")[1])));
        }
        config.getNodeSpec().setWhiteIPList(addressList);
    }

    @Test
    public void whileList()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        PeerClient client = new PeerClient(config, Keys.createEcKeyPair());
        // 新增白名单节点
        client.addWhilteIP("127.0.0.1", 8882);
        //白名单有的节点
        assertTrue(client.isAcceptable(new InetSocketAddress("124.34.34.1", 1001)));
        assertTrue(client.isAcceptable(new InetSocketAddress("127.0.0.1", 1002)));
        assertTrue(client.isAcceptable(new InetSocketAddress("127.0.0.1", 8882)));
        assertFalse(client.isAcceptable(new InetSocketAddress("127.0.0.1", 8883)));

    }
}
