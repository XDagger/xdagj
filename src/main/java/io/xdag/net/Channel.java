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

import io.xdag.Kernel;
import java.net.InetSocketAddress;
import lombok.Getter;

/**
 * One connection to another node, as the rest of xdagj sees it: the peer, its address, and the XDAG protocol
 * that runs over it ({@link #getP2pHandler()}). The transport underneath is xdagj-p2p's
 * {@link io.xdag.p2p.channel.Channel}.
 */
@Getter
public class Channel {

    private final io.xdag.p2p.channel.Channel transport;
    private final boolean isInbound;
    private final InetSocketAddress remoteAddress;
    private final Peer remotePeer;
    private final XdagP2pHandler p2pHandler;
    private volatile boolean isActive;

    public Channel(io.xdag.p2p.channel.Channel transport, Peer remotePeer, Kernel kernel) {
        this.transport = transport;
        this.isInbound = !transport.isActive();
        this.remoteAddress = transport.getRemoteAddress();
        this.remotePeer = remotePeer;
        this.p2pHandler = new XdagP2pHandler(this, kernel);
        this.isActive = true;
    }

    public void close() {
        isActive = false;
        transport.closeWithoutBan();
    }

    /** Closes the connection and refuses the address for a while. */
    public void ban(long banTimeMs) {
        isActive = false;
        transport.close(banTimeMs);
    }

    public boolean isInbound() {
        return isInbound;
    }

    public boolean isOutbound() {
        return !isInbound;
    }

    public boolean isActive() {
        return isActive && !transport.isDisconnect();
    }

    public void setInactive() {
        this.isActive = false;
    }

    /** Whether more data can be queued for the peer right now (see {@link io.xdag.p2p.channel.Channel#isWritable()}). */
    public boolean isWritable() {
        return transport.isWritable();
    }

    public String getRemoteIp() {
        return remoteAddress.getAddress().getHostAddress();
    }

    public int getRemotePort() {
        return remoteAddress.getPort();
    }

    @Override
    public String toString() {
        return "Channel [" + (isInbound ? "Inbound" : "Outbound") + ", remoteIp = " + getRemoteIp() + ", remotePeer = "
                + remotePeer + "]";
    }
}
