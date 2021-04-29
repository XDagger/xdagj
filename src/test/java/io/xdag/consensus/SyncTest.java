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
package io.xdag.consensus;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.Block;
import io.xdag.core.BlockchainImpl;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.mine.manager.AwardManager;
import io.xdag.mine.manager.AwardManagerImpl;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.manager.MinerManagerImpl;
import io.xdag.mine.miner.Miner;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Random;

public class SyncTest {
//    @Rule
//    public TemporaryFolder root = new TemporaryFolder();
//
//    private Config config = new Config();
//    private OldWallet xdagWallet;
//    private Kernel kernel;
//    private DatabaseFactory dbFactory;
//    private MinerManager minerManager;
//    private AwardManager awardManager;
//    private Miner poolMiner;
//
//    @Before
//    public void setUp() throws Exception {
//        config.setStoreDir(root.newFolder().getAbsolutePath());
//        config.setStoreBackupDir(root.newFolder().getAbsolutePath());
//
//        kernel = new Kernel(config);
//        dbFactory = new RocksdbFactory(config);
//        minerManager = new MinerManagerImpl(kernel);
//        awardManager = new AwardManagerImpl(kernel);
//
//
//        BlockStore blockStore = new BlockStore(
//                dbFactory.getDB(DatabaseName.INDEX),
//                dbFactory.getDB(DatabaseName.BLOCK),
//                dbFactory.getDB(DatabaseName.TIME));
//        blockStore.reset();
//        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
//        orphanPool.reset();
//
//        kernel.setBlockStore(blockStore);
//        kernel.setOrphanPool(orphanPool);
//        Native.init();
//        if (Native.dnet_crypt_init() < 0) {
//            throw new Exception("dnet crypt init failed");
//        }
//        xdagWallet = new OldWallet();
//        xdagWallet.init(config);
//        Block firstAccount = new Block(XdagTime.getCurrentTimestamp(), null, null, false, null,null, -1);
//        firstAccount.signOut(xdagWallet.getDefKey().ecKey);
//        poolMiner = new Miner(firstAccount.getHash());
//        kernel.setWallet(xdagWallet);
//        BlockchainImpl blockchain = new BlockchainImpl(kernel);
//        kernel.setBlockchain(blockchain);
//        kernel.setMinerManager(minerManager);
//        kernel.setAwardManager(awardManager);
//        awardManager.setPoolMiner(firstAccount.getHash());
//        kernel.setPoolMiner(poolMiner);
//
//    }
//
//    // Xdag PoW可以看作状态机 1.开始出块 2.接收到share更新块 3.接收到新pretop 回到1 4.timeout发送区块 回到1
//    @Test
//    public void TestPoW() throws InterruptedException {
//        XdagPow pow = new XdagPow(kernel);
//        pow.start();
//
//        byte[] minShare = new byte[32];
//        new Random().nextBytes(minShare);
//
//        Thread sendPretop = new Thread(
//        () -> {
//            try {
//                for (int i = 0; i < 2; i++) {
//                    Thread.sleep(6000);
//                    pow.receiveNewPretop(minShare);
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//        sendPretop.start();
//        sendPretop.join();
//        pow.stop();
//    }
}
