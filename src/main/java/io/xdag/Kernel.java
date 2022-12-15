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
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.consensus.SyncManager;
import io.xdag.consensus.XdagPow;
import io.xdag.consensus.XdagSync;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.crypto.Keys;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.OrphanPool;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.mine.MinerServer;
import io.xdag.mine.manager.AwardManager;
import io.xdag.mine.manager.AwardManagerImpl;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.manager.MinerManagerImpl;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.mine.randomx.RandomX;
import io.xdag.net.XdagClient;
import io.xdag.net.XdagServer;
import io.xdag.net.libp2p.Libp2pNetwork;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.NetDB;
import io.xdag.net.node.NodeManager;
import io.xdag.rpc.Web3;
import io.xdag.rpc.Web3Impl;
import io.xdag.rpc.cors.CorsConfiguration;
import io.xdag.rpc.modules.web3.Web3XdagModule;
import io.xdag.rpc.modules.web3.Web3XdagModuleImpl;
import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.rpc.modules.xdag.XdagModuleChainBase;
import io.xdag.rpc.modules.xdag.XdagModuleTransactionEnabled;
import io.xdag.rpc.modules.xdag.XdagModuleWalletDisabled;
import io.xdag.rpc.netty.JsonRpcWeb3FilterHandler;
import io.xdag.rpc.netty.JsonRpcWeb3ServerHandler;
import io.xdag.rpc.netty.Web3HttpServer;
import io.xdag.rpc.netty.Web3WebSocketServer;
import io.xdag.rpc.netty.XdagJsonRpcHandler;
import io.xdag.rpc.serialize.JacksonBasedRpcSerializer;
import io.xdag.rpc.serialize.JsonRpcSerializer;
import io.xdag.utils.ByteArrayToByte32;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Slf4j
@Getter
@Setter
public class Kernel {

    private Status status = Status.STOPPED;
    private Config config;
    private Wallet wallet;
    private DatabaseFactory dbFactory;
    private AddressStore addressStore;
    private BlockStore blockStore;
    private OrphanPool orphanPool;
    private Blockchain blockchain;
    private NetDB netDB;
    private XdagClient client;
    private XdagChannelManager channelMgr;
    private NodeManager nodeMgr;
    private NetDBManager netDBMgr;
    private XdagServer p2p;
    private XdagSync sync;
    private XdagPow pow;
    private SyncManager syncMgr;

    private byte[] firstAccount;
    private Block firstBlock;
    private Miner poolMiner;
    private AwardManager awardManager;
    private MinerManager minerManager;
    private MinerServer minerServer;
    private XdagState xdagState;
    private Libp2pNetwork libp2pNetwork;
    //    protected DiscoveryController discoveryController;
    private AtomicInteger channelsAccount = new AtomicInteger(0);
    private PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();

    private TelnetServer telnetServer;

    private RandomX randomx;

    // 记录运行状态
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    // 记录启动时间片
    @Getter
    private long startEpoch;


    // rpc
    private JsonRpcWeb3ServerHandler jsonRpcWeb3ServerHandler;
    private Web3 web3;
    private Web3WebSocketServer web3WebSocketServer;
    private Web3HttpServer web3HttpServer;
    private JsonRpcWeb3FilterHandler jsonRpcWeb3FilterHandler;
    private JacksonBasedRpcSerializer jacksonBasedRpcSerializer;

    public Kernel(Config config, Wallet wallet) {
        this.config = config;
        this.wallet = wallet;
        this.xdagState = XdagState.INIT;
        this.telnetServer = new TelnetServer(config.getAdminSpec().getTelnetIp(), config.getAdminSpec().getTelnetPort(),
                this);
    }

    public Kernel(Config config) {
        this.config = config;
    }

    /**
     * Start the kernel.
     */
    public synchronized void testStart() throws Exception {
        if (isRunning.get()) {
            return;
        }
        isRunning.set(true);
        startEpoch = XdagTime.getCurrentEpoch();

        // ====================================
        // start channel manager
        // ====================================
        channelMgr = new XdagChannelManager(this);
        channelMgr.start();
        log.info("Channel Manager start...");
        netDBMgr = new NetDBManager(this.config);
        netDBMgr.init();
        log.info("NetDB Manager init.");

        // ====================================
        // wallet init
        // ====================================

//        if (wallet == null) {
//        wallet = new OldWallet();
//        wallet.init(this.config);

        log.info("Wallet init.");

        dbFactory = new RocksdbFactory(this.config);
        blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.TXHISTORY));
        log.info("Block Store init.");
        blockStore.init();

        addressStore = new AddressStore(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.init();
        log.info("Address Store init");

        orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        log.info("Orphan Pool init.");
        orphanPool.init();

        // ====================================
        // netstatus netdb init
        // ====================================
        netDB = new NetDB();
        log.info("NetDB init");

        // ====================================
        // randomX init
        // ====================================
        randomx = new RandomX(config);
        randomx.init();
//        randomXUtils.randomXLoadingForkTime();
        log.info("RandomX init");

        // ====================================
        // initialize blockchain database
        // ====================================
        blockchain = new BlockchainImpl(this);
        XdagStats xdagStats = blockchain.getXdagStats();
        // 如果是第一次启动，则新建一个创世块
        if (xdagStats.getOurLastBlockHash() == null) {
            firstAccount = Keys.Pub2Byte(wallet.getDefKey().getPublicKey());
            addressStore.addAddress(firstAccount);
            poolMiner = new Miner(ByteArrayToByte32.arrayToByte32(firstAccount));
            firstBlock = new Block(config, XdagTime.getCurrentTimestamp(), null, null, false, null, null, -1);
            firstBlock.signOut(wallet.getDefKey());
            xdagStats.setOurLastBlockHash(firstBlock.getHashLow().toArray());
            if (xdagStats.getGlobalMiner() == null) {
                xdagStats.setGlobalMiner(firstAccount);
            }
            blockchain.tryToConnect(firstBlock);
        } else {
            firstAccount = Keys.Pub2Byte(wallet.getDefKey().getPublicKey());
            poolMiner = new Miner(ByteArrayToByte32.arrayToByte32(firstAccount));
        }
        log.info("Blockchain init");

        // randomX loading
        // TODO: paulochen randomx 需要恢复
        // 初次快照启动
        if (config.getSnapshotSpec().isSnapshotJ()) {
            randomx.randomXLoadingSnapshotJ();
            blockStore.setSnapshotBoot();
        } else {
            if (config.getSnapshotSpec().isSnapshotEnabled() && !blockStore.isSnapshotBoot()) {
                // TODO: forkTime 怎么获得
                System.out.println("pre seed:" + Bytes.wrap(blockchain.getPreSeed()).toHexString());
                randomx.randomXLoadingSnapshot(blockchain.getPreSeed(), 0);
                // 设置为已通过快照启动
                blockStore.setSnapshotBoot();
            } else if (config.getSnapshotSpec().isSnapshotEnabled() && blockStore.isSnapshotBoot()) { // 快照加载后重启
                System.out.println("pre seed:" + Bytes.wrap(blockchain.getPreSeed()).toHexString());
                randomx.randomXLoadingForkTimeSnapshot(blockchain.getPreSeed(), 0);
            } else {
                randomx.randomXLoadingForkTime();
            }
        }

        log.info("RandomX reload");

        // log.debug("Net Status:"+netStatus);

        // ====================================
        // set up client
        // ====================================

        p2p = new XdagServer(this);
        p2p.start();
        log.info("Node server start...");
        client = new XdagClient(this.config);
        log.info("XdagClient nodeId {}", client.getNode().getHexId());

//        libp2pNetwork = new Libp2pNetwork(this);
//        libp2pNetwork.start();

//        discoveryController = new DiscoveryController(this);
//        discoveryController.start();

        // ====================================
        // start node manager
        // ====================================
        nodeMgr = new NodeManager(this);
        nodeMgr.start();
        log.info("Node manager start...");

        // ====================================
        // send request
        // ====================================
        sync = new XdagSync(this);
        sync.start();
        log.info("XdagSync start...");

        // ====================================
        // sync block
        // ====================================
        syncMgr = new SyncManager(this);
        syncMgr.start();
        log.info("SyncManager start...");

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
        minerServer = new MinerServer(this);
        log.info("Pool Server init");

        // ====================================
        // pow
        // ====================================
        pow = new XdagPow(this);
        minerManager.setPoW(pow);
        minerManager.start();
        awardManager.start();

        // register pow
        blockchain.registerListener(pow);

        if (config instanceof MainnetConfig) {
            xdagState = XdagState.WAIT;
        } else if (config instanceof TestnetConfig) {
            xdagState = XdagState.WTST;
        } else if (config instanceof DevnetConfig) {
            xdagState = XdagState.WDST;
        }

        // ====================================
        // rpc start
        // ====================================
        if (config.getRPCSpec().isRPCEnabled()) {
            getWeb3HttpServer().start();
            getWeb3WebSocketServer().start();
        }

        // ====================================
        // telnet server
        // ====================================
        telnetServer.start();

        Launcher.registerShutdownHook("kernel", this::testStop);
    }

    private Web3 getWeb3() {
        if (web3 == null) {
            web3 = buildWeb3();
        }

        return web3;
    }

    private Web3 buildWeb3() {
        Web3XdagModule web3XdagModule = new Web3XdagModuleImpl(
                new XdagModule((byte) 0x1, new XdagModuleWalletDisabled(),
                        new XdagModuleTransactionEnabled(this),
                        new XdagModuleChainBase(this.getBlockchain(),this)), this);
        return new Web3Impl(web3XdagModule);
    }

    private JsonRpcWeb3ServerHandler getJsonRpcWeb3ServerHandler() {
        if (jsonRpcWeb3ServerHandler == null) {
            jsonRpcWeb3ServerHandler = new JsonRpcWeb3ServerHandler(
                    getWeb3(),
                    config.getRPCSpec().getRpcModules()
            );
        }

        return jsonRpcWeb3ServerHandler;
    }

    private Web3WebSocketServer getWeb3WebSocketServer() throws UnknownHostException {
        if (web3WebSocketServer == null) {
            JsonRpcSerializer jsonRpcSerializer = getJsonRpcSerializer();
            XdagJsonRpcHandler jsonRpcHandler = new XdagJsonRpcHandler(jsonRpcSerializer);
            web3WebSocketServer = new Web3WebSocketServer(
                    InetAddress.getByName(config.getRPCSpec().getRPCHost()),
                    config.getRPCSpec().getRPCPortByWebSocket(),
                    jsonRpcHandler,
                    getJsonRpcWeb3ServerHandler()
            );
        }

        return web3WebSocketServer;
    }

    private Web3HttpServer getWeb3HttpServer() throws UnknownHostException {
        if (web3HttpServer == null) {
            web3HttpServer = new Web3HttpServer(
                    InetAddress.getByName(config.getRPCSpec().getRPCHost()),
                    config.getRPCSpec().getRPCPortByHttp(),
                    123,
                    true,
                    new CorsConfiguration("*"),
                    getJsonRpcWeb3FilterHandler(),
                    getJsonRpcWeb3ServerHandler()
            );
        }

        return web3HttpServer;
    }

    private JsonRpcWeb3FilterHandler getJsonRpcWeb3FilterHandler() throws UnknownHostException {
        if (jsonRpcWeb3FilterHandler == null) {
            jsonRpcWeb3FilterHandler = new JsonRpcWeb3FilterHandler(
                    "*",
                    InetAddress.getByName(config.getRPCSpec().getRPCHost()),
                    Collections.singletonList(config.getRPCSpec().getRPCHost())
            );
        }

        return jsonRpcWeb3FilterHandler;
    }

    private JsonRpcSerializer getJsonRpcSerializer() {
        if (jacksonBasedRpcSerializer == null) {
            jacksonBasedRpcSerializer = new JacksonBasedRpcSerializer();
        }

        return jacksonBasedRpcSerializer;
    }

    /**
     * Stops the kernel.
     */
    public synchronized void testStop() {

        if (!isRunning.get()) {
            return;
        }

        isRunning.set(false);

        //
        if (web3HttpServer != null) {
            web3HttpServer.stop();
        }
        if (web3WebSocketServer != null) {
            web3WebSocketServer.stop();
        }

        // 1. 工作层关闭
        // stop consensus
        sync.stop();
        log.info("XdagSync stop.");
        syncMgr.stop();
        log.info("SyncManager stop.");
        pow.stop();
        log.info("Block production stop.");

        // 2. 连接层关闭
        // stop node manager and channel manager
        channelMgr.stop();
        log.info("ChannelMgr stop.");
        nodeMgr.stop();
        log.info("Node manager stop.");

        log.info("ChannelManager stop.");
//        discoveryController.stop();
//        libp2pNetwork.stop();
        // close timer
        MessageQueue.timer.shutdown();

        // close server
        p2p.close();
        log.info("Node server stop.");
        // close client
        client.close();
        log.info("Node client stop.");

        minerServer.close();
        log.info("Pool server stop.");
        minerManager.stop();
        log.info("Miner manager stop.");
        awardManager.stop();

        // 3. 数据层关闭
        // TODO 关闭checkmain线程
        blockchain.stopCheckMain();

        for (DatabaseName name : DatabaseName.values()) {
            dbFactory.getDB(name).close();
        }

        // release
        randomx.randomXPoolReleaseMem();
        log.info("Release randomx");

    }


    public enum Status {
        STOPPED, SYNCING, BLOCK_PRODUCTION_ON, SYNCDONE
    }
}
