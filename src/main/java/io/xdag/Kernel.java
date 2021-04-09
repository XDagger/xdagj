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

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.xdag.cli.TelnetServer;
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
import io.xdag.libp2p.Libp2pNetwork;
import io.xdag.libp2p.manager.ChannelManager;
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
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Getter
@Setter
public class Kernel {
    protected Status status = Status.STOPPED;
    protected Config config;
    protected OldWallet wallet;
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
    protected Libp2pNetwork libp2pNetwork;
    protected DiscoveryController discoveryController;
    protected ChannelManager channelManager;
    protected AtomicInteger channelsAccount = new AtomicInteger(0);
    protected PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();

    protected TelnetServer telnetServer;

    public Kernel(Config config, OldWallet wallet) {
        this.config = config;
        this.wallet = wallet;
        // 启动的时候就是在初始化
        this.xdagState = XdagState.INIT;
        this.telnetServer = new TelnetServer(config.getTelnetIp(), config.getTelnetPort(),this);
    }

    public Kernel(Config config) {
        this.config = config;
    }

    /** Start the kernel. */
    public synchronized void testStart() throws Exception {

        log.debug("Kernel start...");

        EventProcesser.getEventBus().register(this);

        // ====================================
        // start channel manager
        // ====================================
        channelMgr = new XdagChannelManager(this);
        netDBMgr = new NetDBManager(this.config);
        log.debug("NetDB Manager init.");
        netDBMgr.init();

        // ====================================
        // wallet init
        // ====================================
        if (wallet == null) {
            wallet = new OldWallet();
            log.debug("Wallet init.");
            wallet.init(this.config);
        }

        dbFactory = new RocksdbFactory(this.config);
        blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME));
        log.debug("Block Store init.");
        blockStore.init();

        orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        log.debug("Orphan Pool init.");
        orphanPool.init();

        // ====================================
        // netstatus netdb init
        // ====================================
        netDB = new NetDB();

        // ====================================
        // initialize blockchain database
        // ====================================
        blockchain = new BlockchainImpl(this);
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

        p2p = new XdagServer(this);
        p2p.start();
        client = new XdagClient(this.config);

        libp2pNetwork = new Libp2pNetwork(this);
        libp2pNetwork.start();

        discoveryController = new DiscoveryController(this);
        discoveryController.start();

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
        connectionLimitHandler = new ConnectionLimitHandler(this.config.getMaxConnectPerIp());
        minerServer = new MinerServer(this);

        // ====================================
        // pow
        // ====================================
        pow = new XdagPow(this);
        minerManager.setPoW(pow);
        minerManager.start();
        if (Config.MAINNET) {
            xdagState.setState(XdagState.WAIT);
        } else {
            xdagState.setState(XdagState.WTST);
        }

        // ====================================
        // telnet server
        // ====================================
        telnetServer.start();

        Launcher.registerShutdownHook("kernel", this::testStop);
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

        channelManager.stop();
        discoveryController.stop();
        libp2pNetwork.stop();
        // close timer
        MessageQueue.timer.shutdown();

        // close server
        p2p.close();
        // close client
        client.close();

        // TODO 关闭checkmain线程
        blockchain.stopCheckMain();

        for (DatabaseName name : DatabaseName.values()) {
            dbFactory.getDB(name).close();
        }

        minerServer.close();
        minerManager.close();
    }
    public ChannelManager getLibp2pChannelManager() {
        return channelManager;
    }


    public void onSyncDone() {
        status = Status.SYNCDONE;
    }

    public void resetStore() {
    }

    public enum Status {
        STOPPED, SYNCING, BLOCK_PRODUCTION_ON, SYNCDONE
    }
}
