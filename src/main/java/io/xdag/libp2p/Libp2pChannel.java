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
package io.xdag.libp2p;

import io.libp2p.core.Connection;
import io.xdag.core.BlockWrapper;
import io.xdag.libp2p.RPCHandler.RPCHandler;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class Libp2pChannel {
    private final Connection connection;
    private boolean isActive;
    private boolean isDisconnected = false;
    private Node node;
    private final RPCHandler handler;

    public Libp2pChannel(Connection connection, RPCHandler handler) {
        this.connection = connection;
        this.handler = handler;
    }

    //存放连接的节点地址
    public void init(){
        log.info( "init libp2pChannel");
        String[] ipcompont= connection.remoteAddress().toString().split("/");
        node = new Node(ipcompont[2],Integer.parseInt(ipcompont[4]));
//        this.messageQueue = new MessageQueueLib(this);
    }
    public void sendNewBlock(BlockWrapper blockWrapper) {
        log.debug("send a block hash is {}", Hex.toHexString(blockWrapper.getBlock().getHashLow()));
        log.debug("ttl:" + blockWrapper.getTtl());
        handler.getController().sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
    }
    //获取对方的ip
    public String getIp(){
        return connection.remoteAddress().toString();
    }

    public void onDisconnect() {
        isDisconnected = true;
    }

    public boolean isDisconnected() {
        return isDisconnected;
    }

    public Node getNode(){
        return node;
    }

    public RPCHandler getHandler(){
        return handler;
    }

    public void setActive(boolean b) {
        isActive = b;
    }

    public boolean isActive() {
        return isActive;
    }

    public void dropConnection() {
    }
}
