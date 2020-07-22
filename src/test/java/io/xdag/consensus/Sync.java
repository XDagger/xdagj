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

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.BlockchainImpl;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.net.message.NetStatus;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;

public class Sync {

    Config config = new Config();
    Wallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;

    //
    @Before
    public void setUp() throws Exception {
        config.setStoreDir("/Users/punk/testRocksdb/XdagDB");
        config.setStoreBackupDir("/Users/punk/testRocksdb/XdagDB/backupdata");

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME),
                null);
        blockStore.reset();
        AccountStore accountStore = new AccountStore(xdagWallet, blockStore, dbFactory.getDB(DatabaseName.ACCOUNT));
        accountStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setAccountStore(accountStore);
        kernel.setOrphanPool(orphanPool);
        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        xdagWallet = new WalletImpl();
        xdagWallet.init(config);
        kernel.setWallet(xdagWallet);
        kernel.setNetStatus(new NetStatus());
        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        kernel.setBlockchain(blockchain);
    }

    // Xdag PoW可以看作状态机 1.开始出块 2.接收到share更新块 3.接收到新pretop 回到1 4.timeout发送区块 回到1
    @Test
    public void TestPoW() throws InterruptedException {
        XdagPow pow = new XdagPow(kernel);
        pow.onStart();

        byte[] minShare = new byte[32];
        new Random().nextBytes(minShare);

        Thread sendPretop = new Thread(
                () -> {
                    try {
                        for (int i = 0; i < 2; i++) {
                            Thread.sleep(6000);
                            pow.receiveNewPretop(minShare);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
        sendPretop.start();
        sendPretop.join();
        pow.stop();
    }
}
