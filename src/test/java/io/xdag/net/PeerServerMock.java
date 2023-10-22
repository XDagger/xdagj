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

import java.util.concurrent.atomic.AtomicBoolean;

import io.xdag.DagKernel;
import io.xdag.KernelMock;
import io.xdag.config.Config;
import io.xdag.config.spec.NodeSpec;
import io.xdag.consensus.XdagPow;
import io.xdag.core.DagchainImpl;
import io.xdag.core.PendingManager;
import io.xdag.consensus.XdagSync;
import io.xdag.db.Database;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.LeveldbDatabase;
import io.xdag.net.node.NodeManager;

public class PeerServerMock {

    private KernelMock kernel;
    private PeerServer server;

    private PendingManager pendingManager;

    private DatabaseFactory dbFactory;
    private PeerClient client;

    private AtomicBoolean isRunning = new AtomicBoolean(false);

    public PeerServerMock(KernelMock kernel) {
        this.kernel = kernel;
        this.pendingManager = kernel.getPendingManager();
    }

    public synchronized void start() {
        if (isRunning.compareAndSet(false, true)) {
            Config config = kernel.getConfig();
            NodeSpec nodeSpec = config.getNodeSpec();

            dbFactory = new LeveldbDatabase.LeveldbFactory(config.chainDir());
            client = new PeerClient(config, kernel.getCoinbase());

            kernel.setDagchain(new DagchainImpl(config, pendingManager, dbFactory));
            kernel.setClient(client);
            kernel.setChannelManager(new ChannelManager(kernel));
            kernel.setPendingManager(new PendingManager(kernel));
            kernel.setNodeManager(new NodeManager(kernel));
            kernel.setPowManager(new XdagPow(kernel));
            kernel.setSyncManager(new XdagSync(kernel));

            // start peer server
            server = new PeerServer(kernel);
            server.start(nodeSpec.getNodeIp(), nodeSpec.getNodePort());
        }
    }

    public synchronized void stop() {
        if (server != null && client != null && isRunning.compareAndSet(true, false)) {
            server.stop();

            client.close();

            for (DatabaseName name : DatabaseName.values()) {
                Database db = dbFactory.getDB(name);
                db.close();
            }
        }
    }

    public DagKernel getKernel() {
        return kernel;
    }

    public PeerServer getServer() {
        return server;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
