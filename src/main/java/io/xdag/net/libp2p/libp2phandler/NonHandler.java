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
package io.xdag.net.libp2p.libp2phandler;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import io.xdag.net.manager.PeerManager;
import io.xdag.net.libp2p.peer.Libp2pNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Slf4j
@ChannelHandler.Sharable
public class NonHandler implements ProtocolBinding<NonHandler.Controller> {

    PeerManager peerManager;
    public NonHandler(PeerManager peerManager) {
        this.peerManager = peerManager;
    }

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return  new ProtocolDescriptor("xdagj");
    }
    @NotNull
    @Override
    public CompletableFuture<Controller> initChannel(@NotNull P2PChannel p2PChannel, @NotNull String s) {
        final Connection connection = ((Stream) p2PChannel).getConnection();
        final NodeId nodeId = new Libp2pNodeId(connection.secureSession().getRemoteId());
        peerManager.handleConnection(connection);
        Controller controller = new Controller(nodeId, p2PChannel);
        p2PChannel.pushHandler(controller);
        return controller.activeFuture;
    }



    /**
     * SimpleChannelInboundHandler<> 括号是接受的对象
     */
    static class Controller extends SimpleChannelInboundHandler<ByteBuf> {

        final NodeId nodeid;
        final P2PChannel p2pChannel;
        protected final CompletableFuture<Controller> activeFuture = new CompletableFuture<>();

        public Controller(NodeId nodeid, P2PChannel p2pChannel) {
            this.nodeid = nodeid;
            this.p2pChannel = p2pChannel;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String msg = "A message";
            byte[] bytes = msg.getBytes(CharsetUtil.UTF_8);
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            ctx.writeAndFlush(buf);
            activeFuture.complete(this);
        }



        //ByteBuf 是接受的对象
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf buf) throws Exception {
            String s = buf.toString(CharsetUtil.UTF_8);
//            System.out.println(s);
        }
    }
}

