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

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.spec.NodeSpec;
import io.xdag.core.AbstractXdagLifecycle;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.net.node.Node;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.P2pService;
import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;

/**
 * The node's connections, on top of xdagj-p2p.
 *
 * <p>Who may connect is not a list of addresses any more. The network is <em>open</em> - anybody may connect,
 * nodes are found by discovery - once the open-network hardening fork is in force on this node's chain
 * ({@link Blockchain#isOpenNetLatched()}); until then the node is <em>closed</em> and only talks to the
 * configured seed and trusted peers, which is what the former whitelist did. The switch is watched at run
 * time, so a node that reaches the fork while running opens up by itself. Seeds are entry points, nothing
 * more: they get no say that other peers do not have.
 */
@Slf4j
public class ChannelManager extends AbstractXdagLifecycle {

    private final Kernel kernel;
    private final Config config;
    private final BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
    // Thread for block distribution
    private final Thread blockDistributeThread;
    protected final ConcurrentHashMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Channel> activeChannels = new ConcurrentHashMap<>();

    @Getter
    private final P2pConfig p2pConfig;
    @Getter
    private final P2pService p2pService;
    @Getter
    private final ECKeyPair nodeKey;
    private final ScheduledExecutorService housekeeping = Executors.newSingleThreadScheduledExecutor(
            BasicThreadFactory.builder().namingPattern("net-housekeeping-%d").daemon(true).build());
    private Node selfNode;

    public ChannelManager(Kernel kernel) {
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.nodeKey = loadNodeKey(config.getNodeSpec());
        this.p2pConfig = buildP2pConfig(config, nodeKey);
        this.p2pService = new P2pService(p2pConfig);
        try {
            p2pConfig.addP2pEventHandle(new Handler());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        // Resending new blocks to network in loop
        this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
        this.blockDistributeThread.setDaemon(true);
    }

    private static ECKeyPair loadNodeKey(NodeSpec spec) {
        try {
            return NodeKeyStore.loadOrCreate(Paths.get(spec.getNodeKeyFile()));
        } catch (IOException e) {
            throw new IllegalStateException("cannot load the node key from " + spec.getNodeKeyFile(), e);
        }
    }

    private P2pConfig buildP2pConfig(Config config, ECKeyPair nodeKey) {
        NodeSpec spec = config.getNodeSpec();
        P2pConfig p2p = new P2pConfig();
        p2p.setNodeKey(nodeKey);
        p2p.setNetworkId(spec.getNetwork().id());
        p2p.setNetworkVersion(spec.getNetworkVersion());
        p2p.setPort(spec.getNodePort());
        p2p.setBindIp(spec.getNodeBindIp());
        if (spec.getNodeIp() != null && !spec.getNodeIp().isBlank()) {
            p2p.setIpV4(spec.getNodeIp());
        }
        p2p.setClientId(config.getClientId());
        p2p.setNodeTag(spec.getNodeTag());
        p2p.setEnableGenerateBlock(config.getEnableGenerateBlock());
        p2p.setCapabilities(new String[]{"FULL_NODE"});
        p2p.setDataDir(config.getRootDir() + "/p2p");
        p2p.setDiscoverEnable(spec.isDiscoveryEnabled());
        p2p.setAllowPrivateAddresses(spec.isAllowPrivateAddresses());
        p2p.setMinConnections(spec.getMinConnections());
        p2p.setMaxConnections(spec.getMaxConnections());
        p2p.setMaxInboundConnections(spec.getMaxInboundConnections());
        p2p.setMaxConnectionsPerIp(spec.getMaxInboundConnectionsPerIp());
        p2p.setNetMaxFrameBodySize(spec.getNetMaxFrameBodySize());
        p2p.setNetMaxPacketSize(spec.getNetMaxPacketSize());
        p2p.setNetHandshakeExpiry(spec.getNetHandshakeExpiry());
        p2p.setLatestBlockNumberSupplier(() -> {
            Blockchain chain = kernel.getBlockchain();
            return chain == null ? 0 : chain.getLatestMainBlockNumber();
        });
        // closed until the chain says otherwise (see applyOpenness)
        p2p.setPermissionless(false);
        for (InetSocketAddress seed : spec.getSeedNodes()) {
            p2p.getSeedNodes().add(seed);
        }
        for (InetSocketAddress trusted : spec.getTrustedNodes()) {
            p2p.getActiveNodes().add(trusted);
            if (trusted.getAddress() != null) {
                p2p.getTrustNodes().add(trusted.getAddress());
            }
        }
        return p2p;
    }

    @Override
    protected void doStart() {
        // The mode the service starts in. (Only the setting: there is nothing to open or close in a service that
        // is not running yet.)
        Blockchain chain = kernel.getBlockchain();
        p2pConfig.setPermissionless(chain != null && chain.isOpenNetLatched());
        p2pService.start();
        blockDistributeThread.start();
        housekeeping.scheduleWithFixedDelay(this::applyOpenness, 10, 10, TimeUnit.SECONDS);
        log.info("P2P started on port {} as node {} ({} mode; seeds {}, trusted {})", p2pConfig.getPort(),
                nodeKey.toBase58Address(), p2pConfig.isPermissionless() ? "open" : "closed",
                p2pConfig.getSeedNodes(), p2pConfig.getActiveNodes());
    }

    /** Opens or closes the network according to the chain; may be called at any time. */
    public void applyOpenness() {
        try {
            Blockchain chain = kernel.getBlockchain();
            boolean open = chain != null && chain.isOpenNetLatched();
            if (open != p2pConfig.isPermissionless()) {
                log.info("Open-network fork {} on this chain: the network is now {}", open ? "in force" : "not in force",
                        open ? "open (anybody may connect)" : "closed (configured peers only)");
                p2pService.setPermissionless(open);
            }
        } catch (RuntimeException e) {
            log.warn("Cannot apply the network mode: {}", e.toString());
        }
    }

    public boolean isOpen() {
        return p2pConfig.isPermissionless();
    }

    @Override
    protected void doStop() {
        log.debug("Channel Manager stop...");
        housekeeping.shutdownNow();
        if (blockDistributeThread != null) {
            blockDistributeThread.interrupt();
        }
        for (Channel channel : activeChannels.values()) {
            channel.close();
        }
        p2pService.stop();
    }

    /** This node as its peers see it (address and port it listens on). */
    public Node getSelfNode() {
        if (selfNode == null) {
            selfNode = new Node(config.getNodeSpec().getNodeIp(), config.getNodeSpec().getNodePort());
        }
        return selfNode;
    }

    /** Dials a peer on request of the operator; the peer is treated as configured from then on. */
    public void connect(String host, int port) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.getAddress() == null) {
            log.warn("Cannot resolve {}", host);
            return;
        }
        InetAddress ip = address.getAddress();
        if (!p2pConfig.getActiveNodes().contains(address)) {
            p2pConfig.getActiveNodes().add(address);
        }
        if (!p2pConfig.getTrustNodes().contains(ip)) {
            p2pConfig.getTrustNodes().add(ip);
        }
        p2pService.connect(address);
    }

    public int size() {
        return channels.size();
    }

    public Set<InetSocketAddress> getActiveAddresses() {
        Set<InetSocketAddress> set = new java.util.HashSet<>();
        for (Channel c : activeChannels.values()) {
            Peer p = c.getRemotePeer();
            set.add(new InetSocketAddress(p.getIp(), p.getPort()));
        }
        return set;
    }

    public List<Channel> getActiveChannels() {
        return new ArrayList<>(activeChannels.values());
    }

    private void newBlocksDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            BlockWrapper wrapper = null;
            try {
                wrapper = newForeignBlocks.take();
                sendNewBlock(wrapper);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (wrapper != null) {
                    log.error("Block dump: {}", wrapper.getBlock(), e);
                } else {
                    log.error("Error broadcasting unknown block", e);
                }
            }
        }
    }

    public void sendNewBlock(BlockWrapper blockWrapper) {
        for (Channel channel : activeChannels.values()) {
            Peer from = blockWrapper.getRemotePeer();
            if (from != null && from.getPeerId() != null && from.getPeerId().equals(channel.getRemotePeer().getPeerId())) {
                // not back to where it came from
                continue;
            }
            channel.getP2pHandler().sendNewBlock(blockWrapper.getBlock(), blockWrapper.getTtl());
        }
    }

    public void onNewForeignBlock(BlockWrapper blockWrapper) {
        newForeignBlocks.add(blockWrapper);
    }

    /** What the P2P layer tells us about connections and messages. */
    private final class Handler extends P2pEventHandler {

        @Override
        public void onConnect(io.xdag.p2p.channel.Channel transport) {
            if (transport.getPeer() == null || transport.getRemoteAddress() == null) {
                transport.closeWithoutBan();
                return;
            }
            Peer peer = Peer.of(transport.getPeer());
            if (peer.getNetwork() != config.getNodeSpec().getNetwork()
                    || peer.getNetworkVersion() != config.getNodeSpec().getNetworkVersion()) {
                log.debug("Peer {} is on another network ({} v{})", transport.getRemoteAddress(), peer.getNetwork(),
                        peer.getNetworkVersion());
                transport.closeWithoutBan();
                return;
            }
            if (kernel.getSyncMgr() == null) {
                // not ready to talk yet
                transport.closeWithoutBan();
                return;
            }
            Channel channel = new Channel(transport, peer, kernel);
            channels.put(channel.getRemoteAddress(), channel);
            Channel previous = activeChannels.put(peer.getPeerId(), channel);
            if (previous != null && previous != channel) {
                previous.setInactive();
            }
            log.info("Peer connected: {} ({}), peers now {}", peer, channel.isInbound() ? "inbound" : "outbound",
                    activeChannels.size());
        }

        @Override
        public void onDisconnect(io.xdag.p2p.channel.Channel transport) {
            Channel channel = transport.getRemoteAddress() == null ? null : channels.remove(transport.getRemoteAddress());
            if (channel == null) {
                return;
            }
            channel.setInactive();
            activeChannels.remove(channel.getRemotePeer().getPeerId(), channel);
            log.info("Peer disconnected: {}, peers now {}", channel.getRemotePeer(), activeChannels.size());
        }

        @Override
        public void onMessage(io.xdag.p2p.channel.Channel transport, Bytes data) {
            Channel channel = transport.getRemoteAddress() == null ? null : channels.get(transport.getRemoteAddress());
            if (channel == null) {
                return;
            }
            channel.getP2pHandler().onMessage(data);
        }
    }
}
