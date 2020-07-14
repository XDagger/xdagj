package io.xdag.net.node;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagClient;
import io.xdag.net.handler.XdagChannelInitializer;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.NetDB;
import lombok.extern.slf4j.Slf4j;

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
    private Kernel kernel;
    private XdagClient client;
    private XdagChannelManager channelMgr;
    private NetDBManager netDBManager;
    private NetDB netDB;
    private Config config;
    private volatile boolean isRunning;
    private ScheduledFuture<?> connectFuture;
    private ScheduledFuture<?> fetchFuture;

    public NodeManager(Kernel kernel) {
        this.kernel = kernel;
        this.client = kernel.getClient();
        this.channelMgr = kernel.getChannelManager();
        this.netDB = kernel.getNetDB();
        this.exec = new ScheduledThreadPoolExecutor(1, factory);
        this.config = kernel.getConfig();
        this.netDBManager = kernel.getNetDBMgr();
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

            isRunning = true;
            log.debug("Node manager started");
        }
    }

    public synchronized void stop() {
        if (isRunning) {
            connectFuture.cancel(true);
            fetchFuture.cancel(false);

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
        if (config.isEnableRefresh()) {
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
        while ((node = deque.pollFirst()) != null && channelMgr.size() < config.getMAX_CHANNELS()) {
            Long lastCon = lastConnect.getIfPresent(node);
            long now = System.currentTimeMillis();

            if (!client.getNode().equals(node)
                    && !(Objects.equals(node.getHost(), client.getNode().getHost())
                            && node.getPort() == client.getNode().getPort())
                    && !activeAddress.contains(node.getAddress())
                    && (lastCon == null || lastCon + RECONNECT_WAIT < now)) {
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
            XdagChannelInitializer initializer = new XdagChannelInitializer(kernel, false, remotenode);
            client.connect(ip, port, initializer);
        }
    }

    public Set<Node> getNewNode() {
        return netDB.getIPList();
    }

    public Map<Node, Long> getActiveNode() {
        Map<Node, Long> nodes = new HashMap<>();
        List<XdagChannel> activeAddress = channelMgr.getActiveChannels();
        for (XdagChannel address : activeAddress) {
            Node node = address.getNode();
            Long time = lastConnect.getIfPresent(node);
            nodes.put(node, time);
        }
        return nodes;
    }
}
