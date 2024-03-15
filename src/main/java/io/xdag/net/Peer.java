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

import io.xdag.Network;
import lombok.Getter;

@Getter
public class Peer {

    private final Network network;
    private final short networkVersion;
    private final String peerId;
    private final String ip;
    private final int port;
    private final String clientId;
    private final String[] capabilities;
    private long latestBlockNumber;
    private long latency;

    public Peer(Network network, short networkVersion, String peerId, String ip, int port, String clientId,
            String[] capabilities, long latestBlockNumber) {
        this.network = network;
        this.ip = ip;
        this.port = port;
        this.peerId = peerId;
        this.networkVersion = networkVersion;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.latestBlockNumber = latestBlockNumber;
    }

    public void setLatestBlockNumber(long number) {
        this.latestBlockNumber = number;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    @Override
    public String toString() {
        return getPeerId() + "@" + ip + ":" + port;
    }
}