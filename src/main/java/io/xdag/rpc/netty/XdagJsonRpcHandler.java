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
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
