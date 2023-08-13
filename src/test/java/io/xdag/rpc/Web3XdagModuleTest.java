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

package io.xdag.rpc;

import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.db.BlockStore;
import io.xdag.db.OrphanBlockStore;
import io.xdag.db.rocksdb.*;
import io.xdag.rpc.modules.xdag.Web3XdagModule;
import io.xdag.rpc.modules.xdag.Web3XdagModuleImpl;
import io.xdag.rpc.modules.xdag.XdagModule;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

public class Web3XdagModuleTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    BigInteger private_2 = new BigInteger("10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046", 16);

    @Before
    public void setUp() throws Exception {
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));

        blockStore.reset();
        OrphanBlockStore orphanBlockStore = new OrphanBlockStoreImpl(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanBlockStore.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanBlockStore(orphanBlockStore);
        kernel.setWallet(wallet);
    }

    @Test
    public void syncingTest() {
//        XdagModule xdagModule = new XdagModule((byte) 0x1,new XdagModuleWalletDisabled(),new XdagModuleTransactionEnabled(kernel.getBlockchain()));
//        Web3XdagModule web3XdagModule = createWeb3XdagModule(kernel,xdagModule);
    }

    private Web3XdagModule createWeb3XdagModule(Kernel kernel, XdagModule module) {

        return new Web3XdagModuleImpl(module, kernel);
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }
}
