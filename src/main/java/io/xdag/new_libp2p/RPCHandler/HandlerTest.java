package io.xdag.new_libp2p.RPCHandler;

import io.libp2p.core.P2PChannel;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import io.xdag.new_libp2p.peer.NodeId;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Slf4j
@ChannelHandler.Sharable
public class HandlerTest implements ProtocolBinding<HandlerTest.Controller> {

    private String announce;
    private Controller controller;
    final CompletableFuture<HandlerTest> activeFuture = new CompletableFuture<>();
    public HandlerTest(String announce, Controller controller) {
        this.announce = announce;
        this.controller = controller;
    }

    public HandlerTest() {
    }

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return  new ProtocolDescriptor("xdagj");
    }
    //teku
    @NotNull
    @Override
    public CompletableFuture<Controller> initChannel(@NotNull P2PChannel p2PChannel, @NotNull String s) {
        System.out.println("initChannel212121");
        p2PChannel.pushHandler(controller);
        return controller.activeFuture;
    }



    //Xdag03
    //SimpleChannelInboundHandler<> 括号是接受的对象
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
            System.out.println("channelRead0");
            String s = buf.toString(CharsetUtil.UTF_8);
            System.out.println(s);
        }
    }
}

