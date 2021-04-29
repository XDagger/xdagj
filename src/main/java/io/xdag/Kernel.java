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
import io.xdag.net.libp2p.Libp2pNetwork;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.NetDB;
import io.xdag.net.node.NodeManager;
import io.xdag.randomx.RandomX;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
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
    protected Libp2pNetwork libp2pNetwork;

    protected Block firstAccount;
    protected Miner poolMiner;
    protected AwardManager awardManager;
    protected MinerManager minerManager;
    protected MinerServer minerServer;
    protected XdagState xdagState;
    protected AtomicInteger channelsAccount = new AtomicInteger(0);

    protected TelnetServer telnetServer;

    protected RandomX randomXUtils;

    // 记录运行状态
    protected AtomicBoolean isRunning = new AtomicBoolean(false);
    // 记录启动时间片
    @Getter
    protected long startEpoch;


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
        if (isRunning.get())  {
            return;
        }
        isRunning.set(true);
        startEpoch = XdagTime.getCurrentEpoch();

        EventProcesser.getEventBus().register(this);

        // ====================================
        // start channel manager
        // ====================================
        channelMgr = new XdagChannelManager(this);
        channelMgr.start();
        netDBMgr = new NetDBManager(this.config);
        netDBMgr.init();
        log.info("NetDB Manager init.");

        // ====================================
        // wallet init
        // ====================================
//        if (wallet == null) {
        wallet = new OldWallet();
        wallet.init(this.config);
        log.info("Wallet init.");
//        }

        dbFactory = new RocksdbFactory(this.config);
        blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME));
        log.info("Block Store init.");
        blockStore.init();

        orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        log.info("Orphan Pool init.");
        orphanPool.init();

        // ====================================
        // netstatus netdb init
        // ====================================
        netDB = new NetDB();

        // ====================================
        // randomX init
        // ====================================
        randomXUtils = new RandomX();
        randomXUtils.init();

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

        // randomX loading
        randomXUtils.randomXLoadingForkTime();

        // log.debug("Net Status:"+netStatus);

        // ====================================
        // set up client
        // ====================================


        p2p = new XdagServer(this);
        p2p.start();
        client = new XdagClient(this.config);

        libp2pNetwork = new Libp2pNetwork(this);
        libp2pNetwork.start();




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
        awardManager.start();
        if (Config.MAINNET) {
            xdagState = XdagState.WAIT;
        } else {
            xdagState = XdagState.WTST;
        }

        // ====================================
        // telnet server
        // ====================================
        telnetServer.start();

        Launcher.registerShutdownHook("kernel", this::testStop);
    }

    /** Stops the kernel. */
    public synchronized void testStop() {

        if (!isRunning.get()) {
            return;
        }

        isRunning.set(false);

        // 1. 工作层关闭
        // stop consensus
        sync.stop();
        syncMgr.stop();
        pow.stop();

        // 2. 连接层关闭
        // stop node manager and channel manager
        channelMgr.stop();
        nodeMgr.stop();


        // close timer
        MessageQueue.timer.shutdown();

        // close server
        p2p.close();
        // close client
        client.close();
        libp2pNetwork.stop();

        minerServer.close();
        minerManager.stop();
        awardManager.stop();

        // 3. 数据层关闭
        // TODO 关闭checkmain线程
        blockchain.stopCheckMain();

        for (DatabaseName name : DatabaseName.values()) {
            dbFactory.getDB(name).close();
        }

        // release
        randomXUtils.randomXPoolReleaseMem();

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
