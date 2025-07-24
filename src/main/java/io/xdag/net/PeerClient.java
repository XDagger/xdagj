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

package io.xdag.net;

import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static io.xdag.utils.WalletUtils.toBase58;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.config.Config;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.net.node.Node;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Client implementation for peer-to-peer network communication
 */
@Slf4j
@Getter
@Setter
public class PeerClient {

    // Thread factory for client worker threads
    private static final ThreadFactory factory = new BasicThreadFactory.Builder()
            .namingPattern("XdagClient-thread-%d")
            .daemon(true)
            .build();

    private final String ip;
    private final int port;
    private final ECKeyPair coinbase;
    private final EventLoopGroup workerGroup;
    private final Config config;
    private final Set<InetSocketAddress> whitelist;
    private Node node;

    /**
     * Constructor for PeerClient
     * @param config Network configuration
     * @param coinbase Keypair for node identity
     */
    public PeerClient(Config config, ECKeyPair coinbase) {
        this.config = config;
        this.ip = config.getNodeSpec().getNodeIp();
        this.port = config.getNodeSpec().getNodePort();
        this.coinbase = coinbase;
        this.workerGroup = new MultiThreadIoEventLoopGroup(0, factory, NioIoHandler.newFactory());
        this.whitelist = new HashSet<>();
        initWhiteIPs();
    }

    /**
     * Get peer ID derived from coinbase key
     */
    public String getPeerId() {
        return toBase58(toBytesAddress(coinbase));
    }

    /**
     * Connect to a remote node
     * @param remoteNode Target node to connect to
     * @param xdagChannelInitializer Channel initializer
     * @return ChannelFuture for the connection
     */
    public ChannelFuture connect(Node remoteNode, XdagChannelInitializer xdagChannelInitializer) {
        if (!isAcceptable(new InetSocketAddress(remoteNode.getIp(), remoteNode.getPort()))) {
            return null;
        }
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getNodeSpec().getConnectionTimeout());
        b.remoteAddress(remoteNode.toAddress());
        b.handler(xdagChannelInitializer);
        return b.connect();
    }

    /**
     * Gracefully shutdown the client
     */
    public void close() {
        log.debug("Shutdown XdagClient");
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }

    /**
     * Get or create the local node instance
     */
    public Node getNode() {
        if (node == null) {
            node = new Node(ip, port);
        }
        return node;
    }

    /**
     * Check if an address is acceptable based on whitelist
     * @param address Address to check
     * @return true if address is acceptable
     */
    public boolean isAcceptable(InetSocketAddress address) {
        if (!whitelist.isEmpty()) {
            return whitelist.contains(address);
        }
        return true;
    }

    /**
     * Initialize whitelist from config
     */
    private void initWhiteIPs() {
        whitelist.addAll(config.getNodeSpec().getWhiteIPList());
    }

    /**
     * Add an IP to the whitelist
     * @param host Host address
     * @param port Port number
     */
    public void addWhilteIP(String host, int port) {
        whitelist.add(new InetSocketAddress(host, port));
    }

}
