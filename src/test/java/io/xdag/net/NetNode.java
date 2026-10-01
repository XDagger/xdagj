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

import io.xdag.consensus.SyncManager;
import io.xdag.consensus.XdagSync;
import io.xdag.core.ChainHarness;
import io.xdag.crypto.keys.ECKeyPair;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * A node for network tests: a chain in a temporary directory plus the real network layer on a loopback port.
 * Nothing runs on a timer except the P2P layer itself; a test drives synchronisation by calling
 * {@link XdagSync#syncOnce()} and the sync state check itself.
 */
public final class NetNode {
    public final ChainHarness h;
    public final ChannelManager channelMgr;
    public final SyncManager syncMgr;
    public final XdagSync sync;
    public final ECKeyPair key;
    public final int port;

    public NetNode(File dir, int port, List<InetSocketAddress> seeds) {
        this(dir, port, seeds, List.of(), 0);
    }

    /**
     * @param seeds     dialled until the node has a connection
     * @param trusted   always dialled
     * @param forkEpoch activation epoch of the open-network fork (0: in force, the node is open)
     */
    public NetNode(File dir, int port, List<InetSocketAddress> seeds, List<InetSocketAddress> trusted, long forkEpoch) {
        this.port = port;
        key = ECKeyPair.generate();
        h = ChainHarness.create(dir, forkEpoch, key, c -> {
            c.setRootDir(dir.getAbsolutePath());
            c.setNodeKeyFile(new File(dir, "node.key").getAbsolutePath());
            c.setNodeIp("127.0.0.1");
            c.setNodeBindIp("127.0.0.1");
            c.setNodePort(port);
            c.getSeedNodes().clear();
            c.getSeedNodes().addAll(seeds);
            c.getTrustedNodes().clear();
            c.getTrustedNodes().addAll(trusted);
            c.setDiscoveryEnabled(true);
            c.setAllowPrivateAddresses(true);
            c.setMinConnections(1);
        });
        h.kernel.setBlockchain(h.chain);
        channelMgr = new ChannelManager(h.kernel);
        h.kernel.setChannelMgr(channelMgr);
        syncMgr = new SyncManager(h.kernel);
        h.kernel.setSyncMgr(syncMgr);
        sync = new XdagSync(h.kernel);
        h.kernel.setSync(sync);
    }

    public InetSocketAddress address() {
        return new InetSocketAddress("127.0.0.1", port);
    }

    public void start() {
        channelMgr.start();
    }

    public void stop() {
        try {
            channelMgr.stop();
        } finally {
            h.close();
        }
    }

    /**
     * Waits until this node and each of the others see each other as peers. The P2P layer dials configured peers
     * by itself; should an attempt fail, it does not try the address again for 30 s. A test does not want to
     * wait that long: after a few seconds the missing peers are dialled again from here.
     */
    public void awaitConnectedTo(long timeoutMs, NetNode... others) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long redial = System.currentTimeMillis() + 3_000;
        while (true) {
            boolean all = true;
            for (NetNode other : others) {
                all &= peer(other) != null && other.peer(this) != null;
            }
            if (all) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("timed out waiting for the nodes to connect");
            }
            if (System.currentTimeMillis() >= redial) {
                for (NetNode other : others) {
                    if (peer(other) == null) {
                        channelMgr.connect("127.0.0.1", other.port);
                    }
                }
                redial = System.currentTimeMillis() + 3_000;
            }
            Thread.sleep(50);
        }
    }

    /** The first peer, or null. */
    public Channel peer() {
        List<Channel> channels = channelMgr.getActiveChannels();
        return channels.isEmpty() ? null : channels.getFirst();
    }

    /** The connection to another test node, or null. */
    public Channel peer(NetNode other) {
        String id = other.channelMgr.getNodeKey().toBase58Address();
        for (Channel c : channelMgr.getActiveChannels()) {
            if (id.equals(c.getRemotePeer().getPeerId())) {
                return c;
            }
        }
        return null;
    }
}
