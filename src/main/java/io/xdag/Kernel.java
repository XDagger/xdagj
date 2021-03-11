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
package io.xdag;

import io.xdag.config.Config;
import io.xdag.consensus.SyncManager;
import io.xdag.consensus.XdagPow;
import io.xdag.consensus.XdagSync;
import io.xdag.core.*;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.discovery.DiscoveryController;
import io.xdag.event.EventProcesser;
import io.xdag.mine.MinerServer;
import io.xdag.mine.handler.ConnectionLimitHandler;
import io.xdag.mine.manager.AwardManager;
import io.xdag.mine.manager.AwardManagerImpl;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.manager.MinerManagerImpl;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.XdagClient;
import io.xdag.net.XdagServer;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.NetDB;
import io.xdag.net.node.NodeManager;
import io.xdag.libp2p.Libp2pNetwork;
import io.xdag.libp2p.manager.ChannelManager;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;


@Getter
@Setter
public class Kernel {
    protected Status status = Status.STOPPED;
    protected Config config;
    protected Wallet wallet;
    private String arg;
    protected DatabaseFactory dbFactory;
    protected BlockStore blockStore;
    protected OrphanPool orphanPool;
    protected Blockchain blockchain;
    protected NetDB netDB;
    protected XdagClient client;
    protected XdagChannelManager channelMgr;
    protected NodeManager nodeMgr;
    protected NetDBManager netDBMgr;
    protected XdagServer p2p;
    protected XdagSync sync;
    protected Libp2pNetwork libp2pNetwork;
    protected DiscoveryController discoveryController;
    protected ChannelManager channelManager;
    protected XdagPow pow;
    protected SyncManager syncMgr;
    /** 初始化一个后续都可以用的handler */
    protected ConnectionLimitHandler connectionLimitHandler;
    protected Block firstAccount;
    protected Miner poolMiner;
    protected AwardManager awardManager;
    protected MinerManager minerManager;
    protected MinerServer minerServer;
    protected XdagState xdagState;


    protected AtomicInteger channelsAccount = new AtomicInteger(0);

    public Kernel(Config config, Wallet wallet) {
        this.config = config;
        this.wallet = wallet;
        // 启动的时候就是在初始化
        this.xdagState = XdagState.INIT;
    }

    public Kernel(Config config) {
        this.config = config;
    }

    /** Start the kernel. */
    public synchronized void testStart() throws Exception {
        EventProcesser.getEventBus().register(this);

        // ====================================
        // start channel manager
        // ====================================
        channelMgr = new XdagChannelManager(this);
        netDBMgr = new NetDBManager(config);
        netDBMgr.init();

        // ====================================
        // wallet init
        // ====================================
        if (wallet == null) {
            wallet = new WalletImpl();
            wallet.init(config);
        }

        dbFactory = new RocksdbFactory(config);
        blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME));
        blockStore.init();
        orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.init();

        // ====================================
        // netstatus netdb init
        // ====================================
        netDB = new NetDB();

        // ====================================
        // initialize blockchain database
        // ====================================
        blockchain = new BlockchainImpl(this);
        //
//        blockchain.loadBlockchain(config.getOriginStoreDir());
        XdagStats xdagStats = blockchain.getXdagStats();
        // 如果是第一次启动，则新建第一个地址块
        if (xdagStats.getOurLastBlockHash() == null) {
            firstAccount = new Block(XdagTime.getCurrentTimestamp(), null, null, false, null,null, -1);
            firstAccount.signOut(wallet.getDefKey().ecKey);
            poolMiner = new Miner(firstAccount.getHash());
            xdagStats.setOurLastBlockHash(firstAccount.getHashLow());
            if(xdagStats.getGlobalMiner() == null) {
                xdagStats.setGlobalMiner(firstAccount.getHash());
            }
            blockchain.tryToConnect(firstAccount);
        } else {
            poolMiner = new Miner(xdagStats.getGlobalMiner());
        }

        // log.debug("Net Status:"+netStatus);

        // ====================================
        // set up client
        // ====================================
        channelManager = new ChannelManager();
        libp2pNetwork = new Libp2pNetwork(this);
        libp2pNetwork.start();
        discoveryController = new DiscoveryController();
        discoveryController.start(this);
        p2p = new XdagServer(this);
        p2p.start();
        client = new XdagClient(config);

        // ====================================
        // start node manager
        // ====================================
        nodeMgr = new NodeManager(this);
        nodeMgr.start();

        // ====================================
        // send request
        // ====================================
        sync = new XdagSync(this);
        sync.start();

        // ====================================
        // sync block
        // ====================================
        syncMgr = new SyncManager(this);
        syncMgr.start();

//        peerDiscoveryAgent = new PeerDiscoveryAgent(true);
//        peerDiscoveryAgent.start(true);
        // ====================================
        // set up pool miner
        // ====================================
        poolMiner.setMinerStates(MinerStates.MINER_SERVICE);

        // ====================================
        // set up minermanager awardmanager
        // ====================================
        minerManager = new MinerManagerImpl(this);
        awardManager = new AwardManagerImpl(this);
        // ====================================
        // poolnode open
        // ====================================
        connectionLimitHandler = new ConnectionLimitHandler(config.getMaxConnectPerIp());
        minerServer = new MinerServer(this);

        // ====================================
        // pow
        // ====================================
        pow = new XdagPow(this);
        pow.start();
        minerManager.setPoW(pow);
        minerManager.start();
        if (Config.MAINNET) {
            xdagState.setState(XdagState.WAIT);
        } else {
            xdagState.setState(XdagState.WTST);
        }
        Launcher.registerShutdownHook("kernel", this::testStop);
//        state = State.RUNNING;
    }

    /** Stops the kernel. */
    public synchronized void testStop() {
        // stop consensus
        sync.stop();
        syncMgr.stop();
        pow.stop();

        // stop node manager and channel manager
        channelMgr.stop();
        nodeMgr.stop();

        // close timer
        MessageQueue.timer.shutdown();

        // close server
        p2p.close();
        // close client
        client.close();

        for (DatabaseName name : DatabaseName.values()) {
            dbFactory.getDB(name).close();
        }

        minerServer.close();
        minerManager.close();
    }


    public void onSyncDone() {
        status = Status.SYNCDONE;
    }

    public void resetStore() {
    }

    public enum Status {
        STOPPED, SYNCING, BLOCK_PRODUCTION_ON, SYNCDONE
    }

    public ChannelManager getLibp2pChannelManager() {
        return channelManager;
    }
}
