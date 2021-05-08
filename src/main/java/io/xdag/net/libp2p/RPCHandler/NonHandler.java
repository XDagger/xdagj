package io.xdag.net.libp2p.RPCHandler;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import io.xdag.net.libp2p.manager.PeerManager;
import io.xdag.net.libp2p.peer.LibP2PNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

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
        final NodeId nodeId = new LibP2PNodeId(connection.secureSession().getRemoteId());
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
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf buf) {
            String s = buf.toString(CharsetUtil.UTF_8);
//            System.out.println(s);
        }
    }
}
