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

package io.xdag.net.libp2p;

import static org.junit.Assert.assertEquals;

import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import org.junit.Test;

public class MultiaddrTest {

    @Test
    public void testGetIpAndPort() {
        Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/40002");
        String ip = Protocol.IP4.bytesToAddress(multiaddr.getFirstComponent(Protocol.IP4).getValue());
        String port = Protocol.TCP.bytesToAddress(multiaddr.getFirstComponent(Protocol.TCP).getValue());
        assertEquals("127.0.0.1", ip);
        assertEquals("40002", port);
    }

}
