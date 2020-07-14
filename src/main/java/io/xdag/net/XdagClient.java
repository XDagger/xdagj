package io.xdag.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.config.Config;
import io.xdag.net.handler.XdagChannelInitializer;
import io.xdag.net.node.Node;
import java.io.IOException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XdagClient {
    private static final Logger logger = LoggerFactory.getLogger("net");

    private static final ThreadFactory factory = new ThreadFactory() {
        AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "XdagJMinerWorker-" + cnt.getAndIncrement());
        }
    };

    private final EventLoopGroup workerGroup;
    private final int port;
    private Config config;
    private ChannelFuture f;
    private String ip;
    private Node node;

    public XdagClient(Config config) {
        this.config = config;
        this.ip = config.getNodeIp();
        this.port = config.getNodePort();
        this.workerGroup = new NioEventLoopGroup(0, factory);
        logger.debug("XdagClient nodeId:" + getNode().getHexId());
    }

    // public Node getNode(){
    // return new Node(ip,port);
    // }

    /** Connects to the node and returns only upon connection close */
    public void connect(String host, int port, XdagChannelInitializer xdagChannelInitializer) {
        try {
            f = connectAsync(host, port, xdagChannelInitializer);
            f.sync();
        } catch (Exception e) {
            if (e instanceof IOException) {
                logger.debug(
                        "XdagClient: Can't connect to " + host + ":" + port + " (" + e.getMessage() + ")");
                logger.debug("XdagClient.connect(" + host + ":" + port + ") exception:");
            } else {
                logger.error("Exception:", e);
            }
        }
    }

    public ChannelFuture connectAsync(
            String host, int port, XdagChannelInitializer xdagChannelInitializer) {
        // xdagListener.trace("Connecting to: " + host + ":" + port);
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        // b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout());
        b.remoteAddress(host, port);

        b.handler(xdagChannelInitializer);

        // Start the client.
        return b.connect();
    }

    public void close() {
        logger.debug("Shutdown XdagClient");
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }

    public Node getNode() {
        if (node == null) {
            node = new Node(ip, port);
        }
        return node;
    }
}
