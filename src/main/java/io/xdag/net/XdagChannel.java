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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.handler.MessageCodes;
import io.xdag.net.handler.Xdag;
import io.xdag.net.handler.XdagAdapter;
import io.xdag.net.handler.XdagBlockHandler;
import io.xdag.net.handler.XdagHandler;
import io.xdag.net.handler.XdagHandlerFactory;
import io.xdag.net.handler.XdagHandlerFactoryImpl;
import io.xdag.net.handler.XdagHandshakeHandler;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.impl.Xdag03MessageFactory;
import io.xdag.net.node.Node;
import java.net.InetSocketAddress;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Getter
@Setter
public class XdagChannel extends Channel {

    /**
     * 握手 密钥
     */
    private XdagHandshakeHandler handshakeHandler;
    /**
     * 信息编码处理
     */
    private MessageCodes messageCodec;
    /**
     * 处理区块
     */
    private XdagBlockHandler blockHandler;
    /**
     * 获取xdag03handler
     */
    private Xdag xdag = new XdagAdapter();
    /**
     * 用来创建xdag03handler处理message 实际的逻辑操作
     */
    private XdagHandlerFactory xdagHandlerFactory;

    // TODO：记录该连接发送错误消息的次数，一旦超过某个值将断开连接
    private int failTimes;

    public XdagChannel(NioSocketChannel socketChannel) {
        this.socket = socketChannel;
    }

    public void init(
            ChannelPipeline pipeline,
            Kernel kernel,
            boolean isServer,
            InetSocketAddress inetSocketAddress) {

        this.kernel = kernel;
        this.inetSocketAddress = inetSocketAddress;
        this.handshakeHandler = new XdagHandshakeHandler(kernel, this);
        handshakeHandler.setServer(isServer);
        pipeline.addLast("handshakeHandler", handshakeHandler);
        this.messageQueue = new MessageQueue(this);
        this.messageCodec = new MessageCodes();
        this.blockHandler = new XdagBlockHandler(this);
        this.xdagHandlerFactory = new XdagHandlerFactoryImpl(kernel, this);
    }

    public void initWithNode(final String host, final int port) {
        node = new Node(host, port);
        log.debug("Initwith Node host:" + host + " port:" + port + " node:" + node.getHexId());
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return this.inetSocketAddress;
    }

    @Override
    public boolean isActive() {
        return this.isActive;
    }

    @Override
    public void setActive(boolean b) {
        this.isActive = b;
    }

    @Override
    public Node getNode() {
        return this.node;
    }

    @Override
    public void onDisconnect() {
        isDisconnected = true;
    }

    @Override
    public boolean isDisconnected() {
        return isDisconnected;
    }

    @Override
    public MessageQueue getMessageQueue() {
        return this.messageQueue;
    }

    @Override
    public Kernel getKernel() {
        return this.kernel;
    }

    public void sendPubkey(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buffer = ctx.alloc().buffer(1024);
        buffer.writeBytes(kernel.getConfig().getNodeSpec().getXKeys().pub);
        ctx.writeAndFlush(buffer).sync();
        node.getStat().Outbound.add(2);
    }

    public void sendPassword(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buffer = ctx.alloc().buffer(512);
        buffer.writeBytes(kernel.getConfig().getNodeSpec().getXKeys().sect0_encoded);
        ctx.writeAndFlush(buffer).sync();
        node.getStat().Outbound.add(1);
    }

    @Override
    public void sendNewBlock(BlockWrapper blockWrapper) {
        log.debug("send a block hash is {}", blockWrapper.getBlock().getHashLow().toHexString());
        log.debug("ttl:" + blockWrapper.getTtl());
        xdag.sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
    }

    /**
     * 激活xdaghandler
     */
    public void activateXdag(ChannelHandlerContext ctx, XdagVersion version) {
        XdagHandler handler = xdagHandlerFactory.create(version);
        MessageFactory messageFactory = createXdagMessageFactory(version);
        blockHandler.setMessageFactory(messageFactory);
        ctx.pipeline().addLast("blockHandler", blockHandler);
        ctx.pipeline().addLast("messageCodec", messageCodec);
        // 注册进消息队列 用来收发消息
        handler.setMsgQueue(messageQueue);
        ctx.pipeline().addLast("xdag", handler);
        handler.setChannel(this);
        xdag = handler;
        handler.activate();
    }

    private MessageFactory createXdagMessageFactory(XdagVersion version) {
        if (version == XdagVersion.V03) {
            return new Xdag03MessageFactory();
        }
        throw new IllegalArgumentException("Xdag" + version + " is not supported");
    }

    @Override
    public String toString() {
        String format = "new channel";
        if (node != null) {
            format = String.format("%s:%s", node.getHost(), node.getPort());
        }
        return format;
    }

    @Override
    public Xdag getXdag() {
        return xdag;
    }

    @Override
    public void dropConnection() {
        xdag.dropConnection();
    }
}
