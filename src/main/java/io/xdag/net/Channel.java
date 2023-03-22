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

import java.net.InetSocketAddress;

import io.netty.channel.socket.SocketChannel;
import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.handler.Xdag;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.node.Node;
import lombok.Getter;
import lombok.Setter;

/**
 * @author wawa
 */
@Getter
@Setter
public abstract class Channel {

    protected SocketChannel socketChannel;
    protected InetSocketAddress inetSocketAddress;
    protected Node node;
    protected MessageQueue messageQueue;
    protected Kernel kernel;
    protected boolean isActive;
    protected boolean isDisconnected = false;

    public abstract InetSocketAddress getInetSocketAddress();

    public abstract boolean isActive();

    public abstract void setActive(boolean b);

    public abstract Node getNode();

    public abstract void sendNewBlock(BlockWrapper blockWrapper);

    public abstract void onDisconnect();

    public abstract void dropConnection();

    public abstract Xdag getXdag();

    public abstract boolean isDisconnected();

    public abstract MessageQueue getMessageQueue();

    public abstract Kernel getKernel();
}
