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
package io.xdag.net.libp2p.RPCHandler;

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
import io.xdag.net.libp2p.Libp2pChannel;
import io.xdag.net.libp2p.manager.PeerManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.impl.Xdag03MessageFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class RPCHandler implements ProtocolBinding<RPCHandler.Controller> {
    public Controller controller;
    Kernel kernel;
    Libp2pChannel libp2pChannel;
    XdagBlockHandler blockHandler;
    XdagChannelManager channelManager;
    PeerManager peerManager;
    public RPCHandler(Kernel kernel, PeerManager peerManager) {
        this.kernel = kernel;
        this.peerManager = peerManager;
        this.channelManager = kernel.getChannelMgr();
    }

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return  new ProtocolDescriptor("xdagj");
    }

    @NotNull
    @Override
    public CompletableFuture<Controller> initChannel(@NotNull P2PChannel p2PChannel, @NotNull String s) {
        final Connection connection = ((io.libp2p.core.Stream) p2PChannel).getConnection();
        peerManager.handleConnection(connection);
        libp2pChannel = new Libp2pChannel(connection,this);
        libp2pChannel.init(kernel);
        channelManager.add(libp2pChannel);
        blockHandler = new XdagBlockHandler(libp2pChannel);
        blockHandler.setMessageFactory(new Xdag03MessageFactory());
        channelManager.onChannelActive(libp2pChannel,libp2pChannel.getNode());
        MessageCodes messageCodes = new MessageCodes();
        controller = new Controller(kernel,libp2pChannel);

        //  add handler
        p2PChannel.pushHandler(blockHandler);
        p2PChannel.pushHandler(messageCodes);
        p2PChannel.pushHandler(controller);
        return controller.activeFuture;
    }
    public static class Controller extends Xdag03 {

        public CompletableFuture<Controller> activeFuture = new CompletableFuture<>();
        public XdagChannelManager channelManager;
        public Controller(Kernel kernel, Channel channel) {
            super(kernel, channel);
            channelManager = kernel.getChannelMgr();
            msgQueue = channel.getmessageQueue();
        }
        @Override
        public void channelActive(ChannelHandlerContext ctx){
            activeFuture.complete(this);
        }
        //libp2p节点不自动连接dnet
        @Override
        public void updateXdagStats(AbstractMessage message){
            XdagStats remoteXdagStats = message.getXdagStats();
            kernel.getBlockchain().getXdagStats().update(remoteXdagStats);
        }
    }

}
