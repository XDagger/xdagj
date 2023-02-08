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

package io.xdag.net.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.xdag.Kernel;
import io.xdag.core.BlockWrapper;
import io.xdag.net.Channel;
import io.xdag.net.node.Node;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagChannelManager {

    private final Kernel kernel;
    /**
     * Queue with new blocks from other peers
     */
    private final BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
    // 广播区块
    private final Thread blockDistributeThread;
    private final Set<InetSocketAddress> addressSet = new HashSet<>();
    protected ConcurrentHashMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, Channel> activeChannels = new ConcurrentHashMap<>();

    private static final int LRU_CACHE_SIZE = 1024;

    @Getter
    private final Cache<InetSocketAddress, Long> channelLastConnect = Caffeine.newBuilder().maximumSize(LRU_CACHE_SIZE).build();


    public XdagChannelManager(Kernel kernel) {
        this.kernel = kernel;
        // Resending new blocks to network in loop
        this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
        initWhiteIPs();
    }

    public void start() {
        blockDistributeThread.start();
    }

    public void add(Channel ch) {
        log.debug("xdag channel manager->Channel added: remoteAddress = {}", ch.getInetSocketAddress());
        channels.put(ch.getInetSocketAddress(), ch);
//        channelLastConnect.put(ch.getInetSocketAddress(),System.currentTimeMillis());
    }

    public void notifyDisconnect(Channel channel) {
        log.debug("xdag channel manager-> node {}: notifies about disconnect", channel.getInetSocketAddress());
        remove(channel);
        channel.onDisconnect();
    }

    public Set<InetSocketAddress> getActiveAddresses() {
        Set<InetSocketAddress> set = new HashSet<>();
        for (Channel c : activeChannels.values()) {
            Node p = c.getNode();
            set.add(new InetSocketAddress(p.getHost(), p.getPort()));
        }
        return set;
    }

    public List<Channel> getActiveChannels() {
        return new ArrayList<>(activeChannels.values());
    }

    /**
     * Processing new blocks received from other peers from queue
     */
    private void newBlocksDistributeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            BlockWrapper wrapper = null;
            try {
                wrapper = newForeignBlocks.take();
                log.debug("no problem..");
                sendNewBlock(wrapper);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                if (wrapper != null) {
                    log.error("Block dump: {}", wrapper.getBlock(),e);
                } else {
                    log.error("Error broadcasting unknown block", e);
                }
            }
        }
    }

    // TODO:怎么发送 目前是发给除receive的节点
    public void sendNewBlock(BlockWrapper blockWrapper) {
        Node receive;
        // 说明是自己产生的
        if (blockWrapper.getRemoteNode() == null
                || blockWrapper.getRemoteNode().equals(kernel.getClient().getNode())) {
            receive = kernel.getClient().getNode();
        } else {
            Channel receiveChannel = activeChannels.get(blockWrapper.getRemoteNode().getHexId());
            receive = receiveChannel != null ? receiveChannel.getNode() : null;
        }
        for (Channel channel : activeChannels.values()) {
            if (receive != null && channel.getNode().getHexId().equals(receive.getHexId())) {
                log.debug("not send to sender node");
                continue;
            }
            channel.sendNewBlock(blockWrapper);
        }
    }

    public void onChannelActive(Channel channel, Node node) {
        channel.setActive(true);
        activeChannels.put(node.getHexId(), channel);
        channelLastConnect.put(node.getAddress(),System.currentTimeMillis()); // use node to get address(conclude hostname)
        log.debug("activeChannel size:" + activeChannels.size());
    }

    public void onNewForeignBlock(BlockWrapper blockWrapper) {
        newForeignBlocks.add(blockWrapper);
    }

    public boolean containsNode(Node node) {
        return activeChannels.containsKey(node.getHexId());
    }

    public int size() {
        return channels.size();
    }

    public void remove(Channel ch) {
        log.debug("Channel removed: remoteAddress = {}", ch.getInetSocketAddress());
        channels.remove(ch.getInetSocketAddress());
        if (ch.isActive()) {
            activeChannels.remove(ch.getNode().getHexId());
            ch.setActive(false);
        }
    }

    public boolean isAcceptable(InetSocketAddress address) {

        //对于进来的连接，只判断ip，不判断port
        if (addressSet.size() != 0) {
            for (InetSocketAddress inetSocketAddress : addressSet) {
                // 不连接自己
                if (!isSelfAddress(address)&&inetSocketAddress.getAddress().equals(address.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void initWhiteIPs() {
        addressSet.addAll(kernel.getConfig().getNodeSpec().getWhiteIPList());
    }

    // use for ipv4
    private boolean isSelfAddress(InetSocketAddress address) {
        String inIP = address.getAddress().toString();
        inIP = inIP.substring(inIP.lastIndexOf("/") + 1);
        return inIP.equals(kernel.getConfig().getNodeSpec().getNodeIp()) && (address.getPort() == kernel.getConfig()
                .getNodeSpec().getNodePort());
    }

    public void stop() {
        log.debug("Channel Manager stop...");
        if (blockDistributeThread != null) {
            // 中断
            blockDistributeThread.interrupt();
        }
        // 关闭所有连接
        for (Channel channel : activeChannels.values()) {
            channel.dropConnection();
        }
    }
}
