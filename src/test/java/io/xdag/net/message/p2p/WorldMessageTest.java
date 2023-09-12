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
package io.xdag.net.message.p2p;

import static io.xdag.utils.WalletUtils.toBase58;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Test;

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.SecureRandomUtils;
import io.xdag.crypto.Sign;
import io.xdag.net.CapabilityTreeSet;
import io.xdag.net.Peer;

public class WorldMessageTest {

    @Test
    public void testCodec() {
        Config config = new DevnetConfig();

        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        String peerId = toBase58(Keys.toBytesAddress(key));
        WorldMessage msg = new WorldMessage(config.getNodeSpec().getNetwork(), config.getNodeSpec().getNetworkVersion(), peerId, 8001,
                config.getClientId(), config.getClientCapabilities().toArray(), 2,
                SecureRandomUtils.secureRandom().generateSeed(InitMessage.SECRET_LENGTH), key);
        assertTrue(msg.validate(config));

        msg = new WorldMessage(msg.getBody());
        assertTrue(msg.validate(config));

        String ip = "127.0.0.2";
        Peer peer = msg.getPeer(ip);
        assertEquals(config.getNodeSpec().getNetwork(), peer.getNetwork());
        assertEquals(config.getNodeSpec().getNetworkVersion(), peer.getNetworkVersion());
        assertEquals(toBase58(Keys.toBytesAddress(key)), peer.getPeerId());
        assertEquals(ip, peer.getIp());
        assertEquals(config.getNodeSpec().getNodePort(), peer.getPort());
        assertEquals(config.getClientId(), peer.getClientId());
        assertEquals(config.getClientCapabilities(), CapabilityTreeSet.of(peer.getCapabilities()));
        assertEquals(2, peer.getLatestBlockNumber());
    }
}
