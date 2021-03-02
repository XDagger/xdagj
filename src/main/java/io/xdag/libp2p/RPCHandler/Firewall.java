package io.xdag.libp2p.RPCHandler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class Firewall extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LogManager.getLogger();

    private final Duration writeTimeout;

    public Firewall(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().config().setWriteBufferWaterMark(new WriteBufferWaterMark(100, 1024));
        ctx.pipeline().addLast(new WriteTimeoutHandler(writeTimeout.toMillis(), TimeUnit.MILLISECONDS));
        ctx.pipeline().addLast(new FirewallExceptionHandler());
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        ctx.channel().config().setAutoRead(ctx.channel().isWritable());
        ctx.fireChannelWritabilityChanged();
    }

    class FirewallExceptionHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause instanceof WriteTimeoutException) {
                LOG.debug("Firewall closed channel by write timeout. No writes during " + writeTimeout);
            } else {
                LOG.debug("Error in Firewall, disconnecting" + cause);
               }
        }
    }
}
