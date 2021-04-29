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
package io.xdag.net.libp2p.nat;

public class NatPortMapping {

    private final NetworkProtocol protocol;
    private final String internalHost;
    private final String remoteHost;
    private final int externalPort;
    private final int internalPort;
    private final NatServiceType natServiceType;

    public NatPortMapping(
            final NatServiceType natServiceType,
            final NetworkProtocol protocol,
            final String internalHost,
            final String remoteHost,
            final int externalPort,
            final int internalPort) {
        this.natServiceType = natServiceType;
        this.protocol = protocol;
        this.internalHost = internalHost;
        this.remoteHost = remoteHost;
        this.externalPort = externalPort;
        this.internalPort = internalPort;
    }

    public NatServiceType getNatServiceType() {
        return natServiceType;
    }

    public NetworkProtocol getProtocol() {
        return protocol;
    }

    public int getExternalPort() {
        return externalPort;
    }

    public int getInternalPort() {
        return internalPort;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s:%d ==> %s:%d", protocol, internalHost, internalPort, remoteHost, externalPort);
    }
}

