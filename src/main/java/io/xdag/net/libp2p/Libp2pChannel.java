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

import io.libp2p.core.Connection;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.Channel;
import io.xdag.net.handler.Xdag;
import io.xdag.net.libp2p.libp2phandler.Libp2pChannelHandler;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @author wawa
 */
@Slf4j
public class Libp2pChannel extends Channel {
    protected NioSocketChannel socket;
    protected boolean isActive;
    protected boolean isDisconnected = false;
    private final Connection connection;
    private final Libp2pChannelHandler handler;
    protected MessageQueue messageQueue;
    public int port;
    public String ip;
    public Libp2pChannel(Connection connection, Libp2pChannelHandler handler) {
        this.connection = connection;
        this.handler = handler;
    }
    public void init() {
        String[] ipString = connection.remoteAddress().toString().split("/");
        ip = ipString[2];
        port = Integer.parseInt(ipString[4]);
        inetSocketAddress = new InetSocketAddress(ip,port);
        node = new Node(connection.secureSession().getRemoteId().getBytes(),ip,port);
        this.messageQueue = new MessageQueue(this);
        log.debug("Initwith Node host:" + ip + " port:" + port + " node:" + node.getHexId());
    }
    @Override
    public void sendNewBlock(BlockWrapper blockWrapper) {
        handler.controller.sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
    }

    @Override
    public void onDisconnect() {
        isDisconnected = true;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public boolean isDisconnected() {
        return isDisconnected;
    }

    @Override
    public MessageQueue getmessageQueue() {
        return messageQueue;
    }


    public Libp2pChannelHandler getHandler(){
        return handler;
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }

    @Override
    public void setActive(boolean b) {
        isActive = b;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public String getIp() {
        return node.getHexId();
    }

    @Override
    public void dropConnection() {
        connection.close();
    }

    @Override
    public Xdag getXdag() {
        return handler.controller;
    }
}
