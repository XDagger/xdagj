package io.xdag.net.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.xdag.Kernel;
import io.xdag.consensus.XdagPow;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;

@Slf4j
@ChannelHandler.Sharable
public class PoolHandShakeHandler extends SimpleChannelInboundHandler<Object> {
    private WebSocketServerHandshaker handshaker;
    private final int port;
    // pool whitelist
    private final List<String> clientIPList;
    private final XdagPow xdagPow;
    private final boolean allIPAllowed;

    public PoolHandShakeHandler(Kernel kernel, List<String> poolWhiteIPList, int port) {
        this.xdagPow = kernel.getPow();
        this.clientIPList = poolWhiteIPList;
        this.allIPAllowed = clientIPList.contains("0.0.0.0");
        this.port = port;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            // Fullhttprequest for update websocket connect
            handleHttpRequest(ctx, (FullHttpRequest) msg);
            log.debug("Receive request from the pool: {} ", ctx.channel().remoteAddress());
        } else if (msg instanceof WebSocketFrame) {
            // response the other msg
            handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    /**
     * the only one http request，update to websocket connect
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        boolean isIPAllowed;
        String clientIP = ctx.channel().remoteAddress().toString();
        // Determine the mining pool whitelist. If there is 0.0.0.0 in the whitelist, the whitelist is open.
        // Any IP can connect to this node to become a pool.
        // Otherwise, determine the specific IP
        if (!allIPAllowed) {
            // No 0.0.0.0 in the whitelist, determine the specific IP
            isIPAllowed = clientIPList.contains(clientIP);
        } else {
            isIPAllowed = true;
        }
        // Upgrade to websocket, allow pool client ip in config ,filter 'get/Post'
        if (!isIPAllowed || !req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
            // if not websocket request ，create BAD_REQUEST return client
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        String uri = "ws://0.0.0.0:" + port + "/websocket";
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
    public void channelActive(ChannelHandlerContext ctx) {
        log.debug("Pool {} join in. Pool channel id {}",
                ctx.channel().remoteAddress().toString(), ctx.channel().id().toString());
        ChannelSupervise.addChannel(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Pool {} disconnect. Pool channel id {}", ctx.channel().remoteAddress().toString(),
                ctx.channel().id().toString());
        ChannelSupervise.removeChannel(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
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

        if (xdagPow != null) {
            xdagPow.getSharesFromPools().getShareInfo(((TextWebSocketFrame) frame).text());
        }
    }

    /**
     * reject illegal request, return wrong msg
     */
    private static void sendHttpResponse(ChannelHandlerContext ctx,
                                         FullHttpRequest req, DefaultFullHttpResponse res) {
        // return client
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),
                    CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        // if not Keep-Alive，close
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

}
