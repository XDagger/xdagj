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
import io.libp2p.core.P2PChannel;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.Kernel;
import io.xdag.core.XdagStats;
import io.xdag.net.Channel;
import io.xdag.net.handler.MessageCodes;
import io.xdag.net.handler.Xdag03;
import io.xdag.net.handler.XdagBlockHandler;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.impl.Xdag03MessageFactory;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class Libp2pXdagProtocol implements ProtocolBinding<Libp2pXdagProtocol.Libp2pXdagController> {

    private Kernel kernel;
    private Libp2pChannel libp2pChannel;
    private Libp2pXdagController libp2PXdagController;
    private XdagBlockHandler blockHandler;
    private XdagChannelManager channelManager;

    public Libp2pXdagProtocol(Kernel kernel) {
        this.kernel = kernel;
        this.channelManager = kernel.getChannelMgr();
    }


    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor("xdagj-libp2p-protocol");
    }


    @Override
    public CompletableFuture<Libp2pXdagController> initChannel(P2PChannel p2PChannel, String s) {
        final Connection connection = ((io.libp2p.core.Stream) p2PChannel).getConnection();
        libp2pChannel = new Libp2pChannel(connection, this, kernel);
        channelManager.add(libp2pChannel);
        blockHandler = new XdagBlockHandler(libp2pChannel);
        blockHandler.setMessageFactory(new Xdag03MessageFactory());
        channelManager.onChannelActive(libp2pChannel, libp2pChannel.getNode());
        MessageCodes messageCodes = new MessageCodes();
        libp2PXdagController = new Libp2pXdagController(kernel, libp2pChannel);

        //  add handler
        p2PChannel.pushHandler(blockHandler);
        p2PChannel.pushHandler(messageCodes);
        p2PChannel.pushHandler(libp2PXdagController);

        return libp2PXdagController.activeFuture;
    }

    public static class Libp2pXdagController extends Xdag03 {

        CompletableFuture<Libp2pXdagController> activeFuture;
        XdagChannelManager channelManager;

        public Libp2pXdagController(Kernel kernel, Channel channel) {
            super(kernel, channel);
            channelManager = kernel.getChannelMgr();
            msgQueue = channel.getMessageQueue();
            activeFuture = new CompletableFuture<>();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            activeFuture.complete(this);
        }

        //libp2p节点不自动连接dnet
        @Override
        public void updateXdagStats(AbstractMessage message) {
            XdagStats remoteXdagStats = message.getXdagStats();
            kernel.getBlockchain().getXdagStats().update(remoteXdagStats);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("channelInactive:[{}] ", ctx.toString());
            killTimers();
            disconnect();
            channelManager.remove(channel);
        }
    }

}
