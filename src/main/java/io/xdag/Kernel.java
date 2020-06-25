package io.xdag;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.xdag.config.Config;
import io.xdag.consensus.SyncManager;
import io.xdag.consensus.XdagPow;
import io.xdag.consensus.XdagSync;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.XdagState;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.event.KernelBootingEvent;
import io.xdag.event.PubSub;
import io.xdag.event.PubSubFactory;
import io.xdag.mine.MinerServer;
import io.xdag.mine.manager.AwardManager;
import io.xdag.mine.manager.AwardManagerImpl;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.manager.MinerManagerImpl;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.ConnectionLimitHandler;
import io.xdag.net.XdagClient;
import io.xdag.net.XdagServer;
import io.xdag.net.XdagVersion;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.manager.XdagChannelManager;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.NetDB;
import io.xdag.net.message.NetStatus;
import io.xdag.net.node.NodeManager;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;

public class Kernel {
  private static final PubSub pubSub = PubSubFactory.getDefault();

  public enum State {
    STOPPED,
    BOOTING,
    RUNNING,
    STOPPING
  }

  protected State state = State.STOPPED;

  public enum Status {
    STOPPED,
    SYNCING,
    BLOCK_PRODUCTION_ON,
    SYNCDONE
  }

  protected Status status = Status.STOPPED;

  protected Config config;

  protected Wallet wallet;

  protected DatabaseFactory dbFactory;
  protected BlockStore blockStore;
  protected AccountStore accountStore;
  protected OrphanPool orphanPool;
  protected Blockchain chain;
  protected NetStatus netStatus;
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

  // add by myron
  protected XdagState xdagState;

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
    if (state != State.STOPPED) {
      return;
    } else {
      state = State.BOOTING;
      pubSub.publish(new KernelBootingEvent());
    }

    // ====================================
    // print system info
    // ====================================
    // logger.info("Xdag clinet/server system booting up: network = {}, version {},
    // user host = [{}:{}]", Config.MainNet ==true ? "MainNet" : "TestNet",
    // XdagVersion.V03, config.getNodeIp(),config.getNodePort());

    System.out.println(
        "Xdag clinet/server system booting up: network = "
            + (Config.MainNet == true ? "MainNet" : "TestNet")
            + ", version "
            + XdagVersion.V03
            + ", user host = ["
            + config.getNodeIp()
            + ":"
            + config.getNodePort()
            + "]");

    //        try {
    //            //初始密钥
    //            config.initKeys();
    //        } catch (Exception e) {
    //            e.printStackTrace();
    //        }

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
    blockStore =
        new BlockStore(
            dbFactory.getDB(DatabaseName.INDEX),
            dbFactory.getDB(DatabaseName.BLOCK),
            dbFactory.getDB(DatabaseName.TIME),
            dbFactory.getSumsDB());
    blockStore.reset();
    accountStore = new AccountStore(wallet, blockStore, dbFactory.getDB(DatabaseName.ACCOUNT));
    accountStore.reset();
    orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
    orphanPool.reset();

    // ====================================
    // netstatus netdb init
    // ====================================
    netStatus = new NetStatus();
    netDB = new NetDB();

    // ====================================
    // initialize blockchain database
    // ====================================
    chain = new BlockchainImpl(this, dbFactory);
    // 如果是第一次启动，则新建第一个地址块
    if (chain.getAllAccount().size() == 0) {
      firstAccount = new Block(XdagTime.getCurrentTimestamp(), null, null, null, false, null, -1);
      firstAccount.signOut(wallet.getDefKey().ecKey);
      chain.tryToConnect(firstAccount);
      poolMiner = new Miner(firstAccount.getHash());
    } else {
      poolMiner = new Miner(accountStore.getGlobalMiner());
    }

    //        logger.debug("Net Status:"+netStatus);

    // ====================================
    // set up client
    // ====================================
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
    //        minerServer.start();

    connectionLimitHandler = new ConnectionLimitHandler(config.getMaxConnectPerIp());

    // ====================================
    // pow
    // ====================================
    pow = new XdagPow(this);

    minerManager.setPoW(pow);
    minerManager.start();

    if (Config.MainNet) {
      xdagState.setState(XdagState.WAIT);
    } else {
      xdagState.setState(XdagState.WTST);
    }

    Launcher.registerShutdownHook("kernel", this::testStop);
    state = State.RUNNING;
  }

  /** Stops the kernel. */
  public synchronized void testStop() {
    if (state != State.RUNNING) {
      return;
    } else {
      state = State.STOPPING;
      // logger.info("try to shut down the program");
      System.out.println("try to shut down the program");
    }

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

    ReentrantReadWriteLock.WriteLock lock = chain.getStateLock().writeLock();
    lock.lock();
    try {
      for (DatabaseName name : DatabaseName.values()) {
        dbFactory.getDB(name).close();
      }
      // close save sums
      blockStore.closeSum();
    } finally {
      lock.unlock();
    }

    minerServer.close();
    minerManager.close();
    state = State.STOPPED;
  }

  /**
   * Returns the kernel state.
   *
   * @return
   */
  public State state() {
    return state;
  }

  /**
   * Returns the wallet.
   *
   * @return
   */
  public Wallet getWallet() {
    return wallet;
  }

  /**
   * Returns the blockchain.
   *
   * @return
   */
  public Blockchain getBlockchain() {
    return chain;
  }

  /**
   * Returns the client.
   *
   * @return
   */
  public XdagClient getClient() {
    return client;
  }

  /**
   * Returns the channel manager.
   *
   * @return
   */
  public XdagChannelManager getChannelManager() {
    return channelMgr;
  }

  /**
   * Returns the config.
   *
   * @return
   */
  public Config getConfig() {
    return config;
  }

  /**
   * Returns the p2p server instance.
   *
   * @return a {@link XdagServer} instance or null
   */
  public XdagServer getP2p() {
    return p2p;
  }

  /**
   * Returns the database factory.
   *
   * @return
   */
  public DatabaseFactory getDbFactory() {
    return dbFactory;
  }

  /**
   * Returns blockStore
   *
   * @return
   */
  public BlockStore getBlockStore() {
    return blockStore;
  }

  /**
   * Returns accountStore
   *
   * @return
   */
  public AccountStore getAccountStore() {
    return accountStore;
  }

  /**
   * Returns OrphanPool
   *
   * @return
   */
  public OrphanPool getOrphanPool() {
    return orphanPool;
  }

  /**
   * Returns NetStatus
   *
   * @return
   */
  public NetStatus getNetStatus() {
    return netStatus;
  }

  /**
   * Returns SyncManager
   *
   * @return
   */
  public SyncManager getSyncMgr() {
    return syncMgr;
  }

  /**
   * Returns NodeManager
   *
   * @return
   */
  public NodeManager getNodeMgr() {
    return nodeMgr;
  }

  /**
   * Returns PoW
   *
   * @return
   */
  public XdagPow getPow() {
    return pow;
  }

  /**
   * Returns NetDB
   *
   * @return
   */
  public NetDB getNetDB() {
    return netDB;
  }

  /**
   * Returns State
   *
   * @return
   */
  public State getState() {
    return state;
  }

  public NetDBManager getNetDBMgr() {
    return netDBMgr;
  }

  public void setNetDBMgr(NetDBManager netDBMgr) {
    this.netDBMgr = netDBMgr;
  }

  public void setWallet(Wallet wallet) {
    this.wallet = wallet;
  }

  public void setBlockStore(BlockStore blockStore) {
    this.blockStore = blockStore;
  }

  public void setAccountStore(AccountStore accountStore) {
    this.accountStore = accountStore;
  }

  public void setOrphanPool(OrphanPool orphanPool) {
    this.orphanPool = orphanPool;
  }

  public void setNetStatus(NetStatus netStatus) {
    this.netStatus = netStatus;
  }

  public void setBlockchain(Blockchain chain) {
    this.chain = chain;
  }

  public void setSyncMgr(SyncManager syncMgr) {
    this.syncMgr = syncMgr;
  }

  public void setNodeMgr(NodeManager nodeMgr) {
    this.nodeMgr = nodeMgr;
  }

  public void setPow(XdagPow pow) {
    this.pow = pow;
  }

  public void setNetDB(NetDB netDB) {
    this.netDB = netDB;
  }

  public void onSyncDone() {
    status = Status.SYNCDONE;
  }

  public void onPow() {
    status = Status.BLOCK_PRODUCTION_ON;
  }

  public Block getFirstAccount() {
    return firstAccount;
  }

  public void resetStore() {}

  public Miner getPoolMiner() {
    return poolMiner;
  }

  public MinerManager getMinerManager() {
    return minerManager;
  }

  public AwardManager getAwardManager() {
    return awardManager;
  }

  public MinerServer getMinerServer() {
    return minerServer;
  }

  public ConnectionLimitHandler getConnectionLimitHandler() {
    return connectionLimitHandler;
  }

  public XdagState getXdagState() {
    return this.xdagState;
  }
}
