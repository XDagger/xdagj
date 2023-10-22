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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.xdag.DagKernel;
import io.xdag.net.message.MessageQueue;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;

/**
 * @author wawa
 */
@Getter
@Setter
public class Channel {

    private NioSocketChannel socket;
    private boolean isInbound;
    private InetSocketAddress remoteAddress;
    private Peer remotePeer;
    private MessageQueue msgQueue;
    private boolean isActive;

    /**
     * Creates a new channel instance.
     */
    public Channel(NioSocketChannel socket) {
        this.socket = socket;
    }

    /**
     * Initializes this channel.
     */
    public void init(ChannelPipeline pipe, boolean isInbound, InetSocketAddress remoteAddress, DagKernel kernel) {
        this.isInbound = isInbound;
        this.remoteAddress = remoteAddress;
        this.remotePeer = null;

        this.msgQueue = new MessageQueue(kernel.getConfig());

        // register channel handlers
        if (isInbound) {
            pipe.addLast("inboundLimitHandler",
                    new ConnectionLimitHandler(kernel.getConfig().getNodeSpec().getNetMaxInboundConnectionsPerIp()));
        }
        pipe.addLast("readTimeoutHandler", new ReadTimeoutHandler(kernel.getConfig().getNodeSpec().getNetChannelIdleTimeout(), TimeUnit.MILLISECONDS));
        pipe.addLast("xdagFrameHandler", new XdagFrameHandler(kernel.getConfig()));
        pipe.addLast("xdagMessageHandler", new XdagMessageHandler(kernel.getConfig()));
        pipe.addLast("xdagP2pHandler", new XdagP2pHandler(this, kernel));
    }

    public void close() {
        socket.close();
    }

    public boolean isInbound() {
        return isInbound;
    }

    public boolean isOutbound() {
        return !isInbound();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(Peer remotePeer) {
        this.remotePeer = remotePeer;
        this.isActive = true;
    }

    public void setInactive() {
        this.isActive = false;
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
