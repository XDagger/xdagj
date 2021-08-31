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

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.cli.Commands.getStateByFlags;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.ImportResult;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.utils.Numeric;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.MutableBytes32;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SyncTest {

    @Rule
    public TemporaryFolder root1 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root2 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root3 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root4 = new TemporaryFolder();

    Config config = new DevnetConfig();
    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);

    @Test
    //  |    epoch 1    |    epoch 2    |   epoch 3    |
    //        A <-------- B <-------- C
    //                         |
    //                         |------------- D
    public void testAddressBlock() throws Exception {
        Kernel kernel1 = createKernel(root1, false);
        Kernel kernel2 = createKernel(root2, false);
        assertNotEquals(syncCase(kernel1.getBlockchain(), true), syncCase(kernel2.getBlockchain(), false));

        Kernel kernel3 = createKernel(root3, true);
        Kernel kernel4 = createKernel(root4, true);
        assertEquals(syncCase(kernel3.getBlockchain(), true), syncCase(kernel4.getBlockchain(), false));
    }

    public String syncCase(Blockchain blockchain, boolean direction) {
        // 1. create case
        ECKeyPair key = ECKeyPair.create(private_1);
        List<Address> pending = Lists.newArrayList();

        long generateTime = 1600616700000L;
        long time = XdagTime.msToXdagtimestamp(generateTime);
        long secondTime = XdagTime.getEndOfEpoch(time) + 1;
        long thirdTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime + 64000L));
        long fourthTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime + 64000L + 64000L));

        Block addressBlock = generateAddressBlock(config, key, generateTime);
        pending.add(new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT));

        Block secondBlock = generateExtraBlock(config, key, secondTime, pending);
        pending.clear();
        pending.add(new Address(secondBlock.getHashLow(), XDAG_FIELD_OUT));

        Block thirdBlock = generateExtraBlock(config, key, thirdTime, pending);
        pending.clear();
        pending.add(new Address(secondBlock.getHashLow(), XDAG_FIELD_OUT));

        Block fourthBlock = generateExtraBlock(config, key, fourthTime, pending);

        ImportResult result = blockchain.tryToConnect(addressBlock);
        result = blockchain.tryToConnect(secondBlock);
        assertEquals(IMPORTED_BEST, result);

        if (direction) { //正向
            result = blockchain.tryToConnect(thirdBlock);
            result = blockchain.tryToConnect(fourthBlock);
        } else { //反向
            result = blockchain.tryToConnect(fourthBlock);
            result = blockchain.tryToConnect(thirdBlock);
        }

        generateTime = fourthTime;
        MutableBytes32 ref = fourthBlock.getHashLow();

        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
//            date = DateUtils.addSeconds(date, 64);
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long xdagTime = XdagTime.getEndOfEpoch(generateTime);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
        }
        String res = getStateByFlags(blockchain.getBlockByHash(secondBlock.getHashLow(), false).getInfo().getFlags());
        return res;
    }

    public Kernel createKernel(TemporaryFolder root, boolean isNewVersion) throws Exception {
        Config config = new DevnetConfig();
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());
        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        String pwd = "password";
        Wallet wallet = new Wallet(config);
        wallet.unlock(pwd);
        ECKeyPair key = ECKeyPair.create(Numeric.toBigInt(SampleKeys.PRIVATE_KEY_STRING));
        wallet.setAccounts(Collections.singletonList(key));

        Kernel kernel = new Kernel(config);
        DatabaseFactory dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK));

        blockStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(wallet);

        Blockchain blockchain;
        if (!isNewVersion) {
            blockchain = new MockBlockchain(kernel);
        } else {
            blockchain = new NewMockBlockchain(kernel);
        }
        kernel.setBlockchain(blockchain);
        return kernel;
    }

    static class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain() {
        }

        @Override
        public boolean isSyncFixFork(long currentHeight) {
            return false;
        }

    }

    static class NewMockBlockchain extends BlockchainImpl {

        public NewMockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain() {
        }

        @Override
        public boolean isSyncFixFork(long currentHeight) {
            return true;
        }
    }

}
