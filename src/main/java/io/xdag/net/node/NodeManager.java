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
package io.xdag.net.node;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.net.Channel;
import io.xdag.net.discovery.DiscoveryController;
import io.xdag.net.discovery.DiscoveryPeer;

import io.xdag.net.XdagClient;
import io.xdag.net.handler.XdagChannelInitializer;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.NetDB;
import lombok.extern.slf4j.Slf4j;

import static io.xdag.net.libp2p.peer.DiscoveryPeerConverter.discoveryPeerToDailId;


@Slf4j
public class NodeManager {
    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "node-" + cnt.getAndIncrement());
        }
    };
    private static final long MAX_QUEUE_SIZE = 1024;
    private static final int LRU_CACHE_SIZE = 1024;
    private static final long RECONNECT_WAIT = 60L * 1000L;
    private final Deque<Node> deque = new ConcurrentLinkedDeque<>();
    /** 记录对应节点节点最后一次连接的时间 */
    private final Cache<Node, Long> lastConnect = Caffeine.newBuilder().maximumSize(LRU_CACHE_SIZE).build();
    /** 定时处理 */
    private final ScheduledExecutorService exec;
    private final Kernel kernel;
    private final XdagClient client;
    private final XdagChannelManager channelMgr;
    private final NetDBManager netDBManager;
    private final NetDB netDB;
    private final Config config;
    private volatile boolean isRunning;
    private ScheduledFuture<?> connectFuture;
    private ScheduledFuture<?> fetchFuture;
    private ScheduledFuture<?> connectlibp2PFuture;
    private final DiscoveryController discoveryController;
    private Set<Node> hadConnected;
    private Node myself;
    private Set<InetSocketAddress> whilelist;

    public NodeManager(Kernel kernel) {
        this.kernel = kernel;
        this.client = kernel.getClient();
        this.channelMgr = kernel.getChannelMgr();
        this.netDB = kernel.getNetDB();
        this.exec = new ScheduledThreadPoolExecutor(1, factory);
        this.config = kernel.getConfig();
        this.netDBManager = kernel.getNetDBMgr();
        this.discoveryController = kernel.getDiscoveryController();
        myself = new Node(kernel.getConfig().getNodeSpec().getNodeIp(),kernel.getConfig().getNodeSpec().getLibp2pPort());
        whilelist =new HashSet<>();
    }

    /** start the node manager */
    public synchronized void start() {
        if (!isRunning) {
            // addNodes(getSeedNodes(config.getWhiteListDir()));
            addNodes(getSeedNodes(netDBManager.getWhiteDB()));

            // every 0.5 seconds, delayed by 1 seconds (kernel boot up)
            connectFuture = exec.scheduleAtFixedRate(this::doConnect, 1000, 500, TimeUnit.MILLISECONDS);
            // every 100 seconds, delayed by 5 seconds (public IP lookup)
            fetchFuture = exec.scheduleAtFixedRate(this::doFetch, 5, 100, TimeUnit.SECONDS);

            connectlibp2PFuture = exec.scheduleAtFixedRate(this::doConnectlibp2p, 10, 10, TimeUnit.SECONDS);
            hadConnected = new HashSet<>();
            isRunning = true;
            log.debug("Node manager started");
            initWhiteIPs();
        }
    }

    public synchronized void stop() {
        if (isRunning) {
            connectFuture.cancel(true);
            fetchFuture.cancel(false);
            connectlibp2PFuture.cancel(true);
            isRunning = false;
            exec.shutdown();
            log.debug("Node manager stop...");
        }
    }

    public int queueSize() {
        return deque.size();
    }

    public void addNodes(Collection<Node> nodes) {
        if (nodes == null || nodes.size() == 0) {
            return;
        }
        for (Node node : nodes) {
            addNode(node);
        }
    }

    public void addNode(Node node) {
        if (deque.contains(node)) {
            return;
        }
        deque.addFirst(node);
        while (queueSize() > MAX_QUEUE_SIZE) {
            deque.removeLast();
        }
    }



    /** from net update seed nodes */
    protected void doFetch() {
        log.debug("Do fetch");
        if (config.getNodeSpec().enableRefresh()) {
            netDBManager.refresh();
        }
        // 从白名单获得新节点
        addNodes(getSeedNodes(netDBManager.getWhiteDB()));
        // 从netdb获取新节点
        addNodes(getSeedNodes(netDBManager.getNetDB()));


        log.debug("node size:" + deque.size());
    }

    public Set<Node> getSeedNodes(NetDB netDB) {
        if (netDB != null && netDB.getSize() != 0) {
            return netDB.getIPList();
        } else {
            return null;
        }
    }
    public void doConnect() {

        Set<InetSocketAddress> activeAddress = channelMgr.getActiveAddresses();
        Node node;
        while ((node = deque.pollFirst()) != null && channelMgr.size() < config.getNodeSpec().getMaxConnections()) {
            Long lastCon = lastConnect.getIfPresent(node);
            long now = System.currentTimeMillis();

            if (!client.getNode().equals(node)
                    && !(Objects.equals(node.getHost(), client.getNode().getHost())
                            && node.getPort() == client.getNode().getPort())
                    && !activeAddress.contains(node.getAddress())
                    && (lastCon == null || lastCon + RECONNECT_WAIT < now )) {
                if(!isAcceptable(new InetSocketAddress(node.getHost(), node.getPort()))){
                    return ;
                }
                XdagChannelInitializer initializer = new XdagChannelInitializer(kernel, false, node);
                client.connect(node.getHost(), node.getPort(), initializer);
                lastConnect.put(node, now);
                break;
            }
        }

    }

    public void doConnect(String ip, int port) {
        Node remotenode = new Node(ip, port);
        if (!client.getNode().equals(remotenode) && !channelMgr.containsNode(remotenode)) {
            if(!isAcceptable(new InetSocketAddress(ip, port))){
                return ;
            }
            XdagChannelInitializer initializer = new XdagChannelInitializer(kernel, false, remotenode);
            client.connect(ip, port, initializer);
        }
    }
    //todo：发现之后的节点只能自动连接一次
    public void doConnectlibp2p(){
        Set<InetSocketAddress> activeAddress = channelMgr.getActiveAddresses();
        List<DiscoveryPeer> discoveryPeerList =
                discoveryController.getDiscV5Service().streamKnownPeers().collect(Collectors.toList());
        for (DiscoveryPeer p :discoveryPeerList){
            Node node = new Node(p.getNodeAddress().getHostName(),p.getNodeAddress().getPort());
            if(!myself.equals(node)&&!activeAddress.contains(p.getNodeAddress())&&!hadConnected.contains(node)){
                kernel.getLibp2pNetwork().dail(discoveryPeerToDailId(p));
                hadConnected.add(node);
            }
        }
    }

    public Set<Node> getNewNode() {
        return netDB.getIPList();
    }

    public Map<Node, Long> getActiveNode() {
        Map<Node, Long> nodes = new HashMap<>();
        List<Channel> activeAddress = channelMgr.getActiveChannels();
        for (Channel address : activeAddress) {
            Node node = address.getNode();
            Long time = lastConnect.getIfPresent(node);
            nodes.put(node, time);
        }
        return nodes;
    }

    public boolean isAcceptable(InetSocketAddress address) {
        //TODO res = netDBManager.canAccept(address);

        // 默认空为允许所有连接
        if (whilelist.size() != 0) {
            if (!whilelist.contains(address)) {
                return false;
            }
        }

        return true;
    }

    private void initWhiteIPs() {
        List<String> ipList = kernel.getConfig().getNodeSpec().getWhiteIPList();
        for(String ip : ipList){
            String [] ips = ip.split(":");
            whilelist.add(new InetSocketAddress(ips[0],Integer.parseInt(ips[1])));
        }
    }

}
