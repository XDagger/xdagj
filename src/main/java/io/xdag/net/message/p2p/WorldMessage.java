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

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.KeyPair;

import io.xdag.Network;
import io.xdag.core.MainBlock;
import io.xdag.net.message.MessageCode;

public class WorldMessage extends HandshakeMessage {

    public WorldMessage(Network network, short networkVersion, String peerId, int port,
            String clientId, String[] capabilities, MainBlock latestMainBlock,
            byte[] secret, KeyPair coinbase) {
        super(MessageCode.HANDSHAKE_WORLD, null, network, networkVersion, peerId, port, clientId,
                capabilities, latestMainBlock, secret, coinbase);
    }

    public WorldMessage(byte[] encoded) {
        super(MessageCode.HANDSHAKE_WORLD, null, encoded);
    }

    @Override
    public String toString() {
        return "WorldMessage{" +
                "network=" + network +
                ", networkVersion=" + networkVersion +
                ", peerId='" + peerId + '\'' +
                ", port=" + port +
                ", clientId='" + clientId + '\'' +
                ", capabilities=" + Arrays.toString(capabilities) +
                ", latestBlockNumber=" + latestMainBlock.getNumber() +
                ", secret=" + Bytes.wrap(secret).toHexString() +
                ", timestamp=" + timestamp +
                '}';
    }
}