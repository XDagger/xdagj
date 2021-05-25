package io.xdag.rpc.netty;

import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.xdag.rpc.jsonrpc.JsonRpcIdentifiableMessage;
import io.xdag.rpc.jsonrpc.JsonRpcResultOrError;
import io.xdag.rpc.modules.XdagJsonRpcRequest;
import io.xdag.rpc.modules.XdagJsonRpcRequestVisitor;
import io.xdag.rpc.serialize.JsonRpcSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class XdagJsonRpcHandler extends SimpleChannelInboundHandler<ByteBufHolder>
        implements XdagJsonRpcRequestVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(XdagJsonRpcHandler.class);

    private final JsonRpcSerializer serializer;

    public XdagJsonRpcHandler(JsonRpcSerializer serializer) {
        this.serializer = serializer;
    }
//    public XdagJsonRpcHandler(EthSubscriptionNotificationEmitter emitter, JsonRpcSerializer serializer) {
//        this.emitter = emitter;
//        this.serializer = serializer;
//    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBufHolder msg) {
        try {
            XdagJsonRpcRequest request = serializer.deserializeRequest(
                    new ByteBufInputStream(msg.copy().content())
            );

            // TODO(mc) we should support the ModuleDescription method filters
            JsonRpcResultOrError resultOrError = request.accept(this, ctx);
            JsonRpcIdentifiableMessage response = resultOrError.responseFor(request.getId());
            ctx.writeAndFlush(new TextWebSocketFrame(serializer.serializeMessage(response)));
            return;
        } catch (IOException e) {
            LOGGER.trace("Not a known or valid JsonRpcRequest", e);
        }

        // delegate to the next handler if the message can't be matched to a known JSON-RPC request
        ctx.fireChannelRead(msg.retain());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        emitter.unsubscribe(ctx.channel());
        super.channelInactive(ctx);
    }

//    @Override
//    public JsonRpcResultOrError visit(XDAGUnsubscribeRequest request, ChannelHandlerContext ctx) {
////        boolean unsubscribed = emitter.unsubscribe(request.getParams().getSubscriptionId());
////        return new JsonRpcBooleanResult(unsubscribed);
//        return null;
//    }
//
//    @Override
//    public JsonRpcResultOrError visit(XDAGSubscribeRequest request, ChannelHandlerContext ctx) {
////        return request.getParams().accept(emitter, ctx.channel());
//        return null;
//    }
}
