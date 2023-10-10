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

import java.io.File;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hyperledger.besu.crypto.KeyPair;

import io.xdag.cli.TelnetServer;
import io.xdag.config.Config;
import io.xdag.core.PowManager;
import io.xdag.consensus.XdagPow;
import io.xdag.core.Dagchain;
import io.xdag.core.DagchainImpl;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.core.Genesis;
import io.xdag.db.LeveldbDatabase;
import io.xdag.core.PendingManager;
import io.xdag.core.SyncManager;
import io.xdag.consensus.XdagSync;
import io.xdag.crypto.RandomX;
import io.xdag.net.ChannelManager;
import io.xdag.net.NetDBManager;
import io.xdag.net.PeerClient;
import io.xdag.net.PeerServer;
import io.xdag.net.node.NodeManager;
import io.xdag.utils.TimeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Kernel holds references to each individual components.
 */
@Slf4j
@Getter
public class DagKernel {

    public enum State {
        STOPPED, BOOTING, RUNNING, STOPPING
    }

    protected State state = State.STOPPED;
    protected Config config;
    protected Genesis genesis;
    protected Wallet wallet;
    protected KeyPair coinbase;
    protected DatabaseFactory dbFactory;
    protected Dagchain dagchain;
    protected PeerClient client;

    protected ChannelManager channelManager;
    protected PendingManager pendingManager;
    protected NodeManager nodeManager;
    protected PeerServer p2p;

    protected Thread consThread;
    protected XdagSync sync;
    protected XdagPow pow;
    protected RandomX randomx;
    protected TelnetServer telnetServer;
    protected NetDBManager netDBManager;

//    private final byte[] DUMMY_ADDRESS = new Key().toAddress();

    /**
     * Creates a kernel instance and initializes it.
     */
    public DagKernel(Config config, Genesis genesis, Wallet wallet, KeyPair coinbase) {
        this.config = config;
        this.genesis = genesis;
        this.wallet = wallet;
        this.coinbase = coinbase;
    }

    public DagKernel(Config config, Wallet wallet) {
        this.config = config;
        this.genesis = Genesis.load(config.getNodeSpec().getNetwork());
        this.wallet = wallet;
        this.coinbase = wallet.getDefKey();
    }

    /**
     * Start the kernel.
     */
    public synchronized void start() {
        if (state != State.STOPPED) {
            return;
        } else {
            state = State.BOOTING;
        }

        // ====================================
        // print system info
        // ====================================
        log.info(config.getClientId());
        log.info("System booting up: network = {}, networkVersion = {}, coinbase = {}", config.getNodeSpec().getNetwork(),
                config.getNodeSpec().getNetworkVersion(),
                coinbase);
        //printSystemInfo();
        TimeUtils.startNtpProcess();

        // ====================================
        // initialize blockchain database
        // ====================================
        dbFactory = new LeveldbDatabase.LeveldbFactory(config.chainDir());
        dagchain = new DagchainImpl(config, pendingManager, genesis, dbFactory);
        long number = dagchain.getLatestMainBlockNumber();
        log.info("Latest block number = {}", number);

        // ====================================
        // set up client
        // ====================================
        client = new PeerClient(config, coinbase);

        // ====================================
        // start channel/pending/node manager
        // ====================================
        netDBManager = new NetDBManager(this.config);
        netDBManager.init();
        log.info("NetDB Manager init.");

        channelManager = new ChannelManager(this);
        pendingManager = new PendingManager(this);
        nodeManager = new NodeManager(this);

        pendingManager.start();
        nodeManager.start();

        // ====================================
        // start p2p module
        // ====================================
        p2p = new PeerServer(this);
        p2p.start();

        // ====================================
        // randomX init
        // ====================================
        randomx = new RandomX(config);
        randomx.init();
        //        randomXUtils.randomXLoadingForkTime();
        log.info("RandomX init");

        // ====================================
        // start API module
        // ====================================
//        api = new XdagApiService(this);
//        if (config.apiEnabled()) {
//            api.start();
//        }

        // ====================================
        // start sync/consensus
        // ====================================
        sync = new XdagSync(this);
        pow = new XdagPow(this);

        consThread = new Thread(pow::start, "consensus");
        consThread.start();

        // ====================================
        // start telnet server
        // ====================================
        this.telnetServer = new TelnetServer(config.getAdminSpec().getTelnetIp(), config.getAdminSpec().getTelnetPort(),
                this);
        telnetServer.start();

        // ====================================
        // register shutdown hook
        // ====================================
        Launcher.registerShutdownHook("dagkernel", this::stop);

        state = State.RUNNING;
    }

    /**
     * Moves database to another directory.
     */
    private void moveDatabase(File srcDir, File dstDir) {
        // store the sub-folders
        File[] files = srcDir.listFiles();

        // create the destination folder
        dstDir.mkdirs();

        // move to destination
        for (File f : Objects.requireNonNull(files)) {
            f.renameTo(new File(dstDir, f.getName()));
        }
    }

    /**
     * Prints system info.
     */
//    protected void printSystemInfo() {
//        try {
//            SystemInfo si = new SystemInfo();
//            HardwareAbstractionLayer hal = si.getHardware();
//
//            // computer system
//            ComputerSystem cs = hal.getComputerSystem();
//            logger.info("Computer: manufacturer = {}, model = {}", cs.getManufacturer(), cs.getModel());
//
//            // operating system
//            OperatingSystem os = si.getOperatingSystem();
//            logger.info("OS: name = {}", os);
//
//            // cpu
//            CentralProcessor cp = hal.getProcessor();
//            logger.info("CPU: processor = {}, cores = {} / {}", cp, cp.getPhysicalProcessorCount(),
//                    cp.getLogicalProcessorCount());
//
//            // memory
//            GlobalMemory m = hal.getMemory();
//            long mb = 1024L * 1024L;
//            logger.info("Memory: total = {} MB, available = {} MB", m.getTotal() / mb, m.getAvailable() / mb);
//
//            // disk
//            for (HWDiskStore disk : hal.getDiskStores()) {
//                logger.info("Disk: name = {}, size = {} MB", disk.getName(), disk.getSize() / mb);
//            }
//
//            // network
//            for (NetworkIF net : hal.getNetworkIFs()) {
//                logger.info("Network: name = {}, ip = [{}]", net.getDisplayName(), String.join(",", net.getIPv4addr()));
//            }
//
//            // java version
//            log.info("Java: version = {}, xmx = {} MB", System.getProperty("java.version"),
//                    Runtime.getRuntime().maxMemory() / mb);
//        } catch (RuntimeException e) {
//            log.error("Unable to retrieve System information.", e);
//        }
//    }

    /**
     * Sets up uPnP port mapping.
     */
//    protected void setupUpnp() {
//        try {
//            GatewayDiscover discover = new GatewayDiscover();
//            Map<InetAddress, GatewayDevice> devices = discover.discover();
//            for (Map.Entry<InetAddress, GatewayDevice> entry : devices.entrySet()) {
//                GatewayDevice gw = entry.getValue();
//                logger.info("Found a gateway device: local address = {}, external address = {}",
//                        gw.getLocalAddress().getHostAddress(), gw.getExternalIPAddress());
//
//                gw.deletePortMapping(config.p2pListenPort(), "TCP");
//                gw.addPortMapping(config.p2pListenPort(), config.p2pListenPort(), gw.getLocalAddress().getHostAddress(),
//                        "TCP", "Xdag P2P network");
//            }
//        } catch (IOException | SAXException | ParserConfigurationException e) {
//            logger.info("Failed to add port mapping", e);
//        }
//    }

    /**
     * Stops the kernel.
     */
    public synchronized void stop() {
        if (state != State.RUNNING) {
            return;
        } else {
            state = State.STOPPING;
        }

        // stop consensus
        try {
            sync.stop();
            pow.stop();

            // make sure consensus thread is fully stopped
            consThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to stop sync/bft manager properly");
        }

        // stop API and p2p
//        api.stop();
        p2p.stop();

        // stop pending manager and node manager
        pendingManager.stop();
        nodeManager.stop();

        // close client
        client.close();

        // make sure no thread is reading/writing the state
        ReentrantReadWriteLock.WriteLock lock = dagchain.getStateLock().writeLock();
        lock.lock();
        try {
            for (DatabaseName name : DatabaseName.values()) {
                dbFactory.getDB(name).close();
            }
        } finally {
            lock.unlock();
        }

        state = State.STOPPED;
    }

    /**
     * Returns the kernel state.
     */
    public State state() {
        return state;
    }

    /**
     * Returns the syncing manager.
     */
    public SyncManager getSyncManager() {
        return sync;
    }

    /**
     * Returns the POW manager.
     */
    public PowManager getPowManager() {
        return pow;
    }

    /**
     * Get instance of Xdag API server
     */
//    public XdagApiService getApi() {
//        return api;
//    }
}
