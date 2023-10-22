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

import static io.xdag.crypto.Keys.toBytesAddress;
import static io.xdag.utils.WalletUtils.toBase58;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.hyperledger.besu.crypto.KeyPair;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.config.Config;
import io.xdag.net.node.Node;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class PeerClient {

    private static final ThreadFactory factory = new ThreadFactory() {
        final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "client-" + cnt.getAndIncrement());
        }
    };

    private final String ip;
    private final int port;
    private final KeyPair coinbase;
    private final EventLoopGroup workerGroup;

    private final Config config;

    private final Set<InetSocketAddress> whilelist;
    private Node node;

    public PeerClient(Config config, KeyPair coinbase) {
        this.config = config;
        this.ip = config.getNodeSpec().getNodeIp();
        this.port = config.getNodeSpec().getNodePort();
        this.coinbase = coinbase;
        this.workerGroup = new NioEventLoopGroup(4, factory);
        this.whilelist = new HashSet<>();
        initWhiteIPs();
    }

    public String getPeerId() {
        return toBase58(toBytesAddress(coinbase));
    }

    public ChannelFuture connect(Node remoteNode, XdagChannelInitializer xdagChannelInitializer) {
        if (!isAcceptable(new InetSocketAddress(remoteNode.getIp(), remoteNode.getPort()))) {
            return null;
        }
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getNodeSpec().getConnectionTimeout());
        b.remoteAddress(remoteNode.toAddress());
        b.handler(xdagChannelInitializer);
        return b.connect();
    }

    public void close() {
        log.debug("Shutdown XdagClient");
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }

    public Node getNode() {
        if (node == null) {
            node = new Node(ip, port);
        }
        return node;
    }

    public boolean isAcceptable(InetSocketAddress address) {
        if (!whilelist.isEmpty()) {
            return whilelist.contains(address);
        }

        return true;
    }

    private void initWhiteIPs() {
        whilelist.addAll(config.getNodeSpec().getWhiteIPList());
    }

    public void addWhilteIP(String host, int port) {
        whilelist.add(new InetSocketAddress(host, port));
    }

}
