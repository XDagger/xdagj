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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.xdag.DagKernel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelManager {

    private final DagKernel kernel;
    /**
     * Queue with new blocks from other peers
     */
//    private final BlockingQueue<BlockWrapper> newForeignBlocks = new LinkedBlockingQueue<>();
    // 广播区块
//    private final Thread blockDistributeThread;
    private final Set<InetSocketAddress> addressSet = new HashSet<>();
    protected ConcurrentHashMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, Channel> activeChannels = new ConcurrentHashMap<>();

    private static final int LRU_CACHE_SIZE = 1024;

    @Getter
    private final Cache<InetSocketAddress, Long> channelLastConnect = Caffeine.newBuilder().maximumSize(LRU_CACHE_SIZE).build();


    public ChannelManager(DagKernel kernel) {
        this.kernel = kernel;
        // Resending new blocks to network in loop
//        this.blockDistributeThread = new Thread(this::newBlocksDistributeLoop, "NewSyncThreadBlocks");
        initWhiteIPs();
    }

//    public void start() {
//        blockDistributeThread.start();
//    }

    public boolean isAcceptable(InetSocketAddress address) {
        //对于进来的连接，只判断ip，不判断port
        if (!addressSet.isEmpty()) {
            for (InetSocketAddress inetSocketAddress : addressSet) {
                // 不连接自己
                if (!isSelfAddress(address)&&inetSocketAddress.getAddress().equals(address.getAddress())) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public boolean isActiveIP(String ip) {
        for (Channel c : activeChannels.values()) {
            if (c.getRemoteIp().equals(ip)) {
                return true;
            }
        }

        return false;
    }

    public boolean isActivePeer(String peerId) {
        return activeChannels.containsKey(peerId);
    }

    public int size() {
        return channels.size();
    }

    public void add(Channel ch) {
        log.debug("xdag channel manager->Channel added: remoteAddress = {}:{}", ch.getRemoteIp(), ch.getRemotePort());
        channels.put(ch.getRemoteAddress(), ch);
    }

    public void remove(Channel ch) {
        log.debug("Channel removed: remoteAddress = {}:{}", ch.getRemoteIp(), ch.getRemotePort());

        channels.remove(ch.getRemoteAddress());
        if (ch.isActive()) {
            activeChannels.remove(ch.getRemotePeer().getPeerId());
            ch.setInactive();
        }
    }

    public void closeBlacklistedChannels() {
        for (Map.Entry<InetSocketAddress, Channel> entry : channels.entrySet()) {
            Channel channel = entry.getValue();
            if (!isAcceptable(channel.getRemoteAddress())) {
                remove(channel);
                channel.close();
            }
        }
    }

    public void onChannelActive(Channel channel, Peer peer) {
        channel.setActive(peer);
        activeChannels.put(peer.getPeerId(), channel);
    }

    public List<Peer> getActivePeers() {
        List<Peer> list = new ArrayList<>();

        for (Channel c : activeChannels.values()) {
            list.add(c.getRemotePeer());
        }

        return list;
    }

    public Set<InetSocketAddress> getActiveAddresses() {
        Set<InetSocketAddress> set = new HashSet<>();

        for (Channel c : activeChannels.values()) {
            Peer p = c.getRemotePeer();
            set.add(new InetSocketAddress(p.getIp(), p.getPort()));
        }

        return set;
    }

    public List<Channel> getActiveChannels() {
        return new ArrayList<>(activeChannels.values());
    }

    public List<Channel> getActiveChannels(List<String> peerIds) {
        List<Channel> list = new ArrayList<>();

        for (String peerId : peerIds) {
            if (activeChannels.containsKey(peerId)) {
                list.add(activeChannels.get(peerId));
            }
        }

        return list;
    }

    public List<Channel> getIdleChannels() {
        List<Channel> list = new ArrayList<>();

        for (Channel c : activeChannels.values()) {
            if (c.getMsgQueue().isIdle()) {
                list.add(c);
            }
        }

        return list;
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

}
