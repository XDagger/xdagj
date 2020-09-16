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
package io.xdag.net.handler;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.node.Node;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    private final Node remoteNode;
    protected Kernel kernel;
    private XdagChannelManager channelMgr;
    private boolean isServer = false;

    public XdagChannelInitializer(Kernel kernel, boolean isServer, Node remoteNode) {
        this.kernel = kernel;
        this.isServer = isServer;
        this.remoteNode = remoteNode;
        this.channelMgr = kernel.getChannelMgr();
    }

    @Override
    protected void initChannel(NioSocketChannel ch) {
        try {
            // log.debug("new input channel");
            InetSocketAddress address = isServer ? ch.remoteAddress() : remoteNode.getAddress();

            // 判断进来的是不是白名单上的节点
            if (isServer && !channelMgr.isAcceptable(address)) {
                log.debug("Disallowed inbound connection: {}", address.toString());
                ch.disconnect();
                return;
            }

            XdagChannel channel = new XdagChannel(ch);
            channel.init(ch.pipeline(), kernel, isServer, address);

//            ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(512 * 1024));
            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
//            ch.config().setOption(ChannelOption.SO_RCVBUF, 512 * 1024);
//            ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

            channelMgr.add(channel);

            ch.closeFuture().addListener((ChannelFutureListener) future -> {
                channelMgr.notifyDisconnect(channel);
            });

        } catch (Exception e) {
            log.error("Unexpected error: [{}]", e.getMessage(), e);
        }
    }
}
