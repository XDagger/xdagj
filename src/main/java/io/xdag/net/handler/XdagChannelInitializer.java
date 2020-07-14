package io.xdag.net.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.Kernel;
import io.xdag.net.XdagChannel;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.node.Node;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XdagChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger("net");
    private final Node remoteNode;
    protected Kernel kernel;
    private XdagChannelManager channelMgr;
    private boolean isServer = false;

    public XdagChannelInitializer(Kernel kernel, boolean isServer, Node remoteNode) {
        this.kernel = kernel;
        this.isServer = isServer;
        this.remoteNode = remoteNode;
        this.channelMgr = kernel.getChannelManager();
    }

    @Override
    protected void initChannel(NioSocketChannel ch) {
        try {
            // logger.debug("new input channel");
            InetSocketAddress address = isServer ? ch.remoteAddress() : remoteNode.getAddress();

            // 判断进来的是不是白名单上的节点
            if (isServer && !channelMgr.isAcceptable(address)) {
                logger.debug("Disallowed inbound connection: {}", address.toString());
                ch.disconnect();
                return;
            }

            XdagChannel channel = new XdagChannel(ch);
            // 把管道注册到channel上
            channel.init(ch.pipeline(), kernel, isServer, address);

            ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
            ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
            ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

            channelMgr.add(channel);

            ch.closeFuture()
                    .addListener(
                            (ChannelFutureListener) future -> {
                                channelMgr.notifyDisconnect(channel); // 关闭后通知
                            });

        } catch (Exception e) {
            logger.error("Unexpected error: ", e);
        }
    }
}
