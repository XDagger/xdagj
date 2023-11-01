package io.xdag.net.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PoolHandShakeHandler extends SimpleChannelInboundHandler<Object> {
    private WebSocketServerHandshaker handshaker;
    private final int port;
    private final String ClientIP;
    private final String ClientTap;

    public PoolHandShakeHandler(String clienthost,String tag, int port) {
        this.ClientIP = clienthost;
        this.ClientTap = tag;
        this.port = port;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest ) {
            log.debug("recv: "+ msg);
            //Fullhttprequest for update websocket connect
            handleHttpRequest(ctx, (FullHttpRequest) msg);
            log.debug("handshake with pool: {} ", ctx.channel().remoteAddress());
        }else if (msg instanceof  WebSocketFrame){
            //response the other msg
            handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    /**
     * the only one http request，update to websocket connect
     * */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        String clientIP = ctx.channel().remoteAddress().toString();
        //Upgrade to websocket, allow pool client ip in config ,filter 'get/Post'
        if ((!clientIP.contains(ClientIP))
                || !req.decoderResult().isSuccess()
                || (!"websocket".equals(req.headers().get("Upgrade")))) {
        //if not websocket request ，create BAD_REQUEST return client
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        String uri = "ws://localhost:" + port + "/websocket";
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                uri, null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        log.debug("pool {} join in.",ctx.channel());
        if (ctx.channel().remoteAddress().toString().contains(ClientIP)){
            ChannelSupervise.addChannel(ctx.channel(), ClientTap);
        }
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("pool {} disconnect.",ctx.channel());
        if (ctx.channel().remoteAddress().toString().contains(ClientIP)) {
            ChannelSupervise.removeChannel(ctx.channel(), ClientTap);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame){
        //  close command
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        // ping msg
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        // support text msg
        if (!(frame instanceof TextWebSocketFrame)) {
            log.debug("Unsupported msg type ");
            throw new UnsupportedOperationException(String.format(
                    "%s frame types not supported", frame.getClass().getName()));
        }
        // 返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        log.debug("server recv：" + request);
    }

    /**
     * reject illegal request, return wrong msg
     * */
    private static void sendHttpResponse(ChannelHandlerContext ctx,
                                         FullHttpRequest req, DefaultFullHttpResponse res) {
        //response client
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),
                    CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        // if not Keep-Alive,close
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
