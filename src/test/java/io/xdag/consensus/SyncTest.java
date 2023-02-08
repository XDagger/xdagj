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
import static io.xdag.BlockBuilder.generateExtraBlockGivenRandom;
import static io.xdag.cli.Commands.getStateByFlags;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.ImportResult;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.db.BlockStore;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.OrphanPool;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.math.BigInteger;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SyncTest {

    static { Security.addProvider(new BouncyCastleProvider());  }

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
    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);

    @Before
    public void setup() {
    }

    @Test
    //  |    epoch 1    |    epoch 2    |   epoch 3    |
    //        A <-------- B <-------- C
    //                         |
    //                         |------------- D
    public void testCase1() throws Exception {
        Kernel kernel1 = createKernel(root1, false, 10000);
        Kernel kernel2 = createKernel(root2, false, 10000);
        assertNotEquals(syncCase(kernel1.getBlockchain(), true), syncCase(kernel2.getBlockchain(), false));

        Kernel kernel3 = createKernel(root3, true, 0);
        Kernel kernel4 = createKernel(root4, true, 0);
        assertEquals(syncCase(kernel3.getBlockchain(), true), syncCase(kernel4.getBlockchain(), false));
    }


    @Test
    //  |    epoch 1    |    epoch 2    |   epoch 3    |
    //        A <-------- B <-------- C
    //                         |
    //                         |------------- D
    public void testCase2() throws Exception {
        Kernel kernel1 = createKernel(root1, false, 10000);
        Kernel kernel2 = createKernel(root2, false, 10000);
        String[] res1 = syncCase2(kernel1.getBlockchain(), true);
        String[] res2 = syncCase2(kernel2.getBlockchain(), false);
        assertNotEquals(res1[0], res2[0]);
        assertNotEquals(res1[1], res2[1]);

        for (int height = 10; height <= 15; height++) {
            Kernel kernel3 = createKernel(root3, true, height);
            Kernel kernel4 = createKernel(root4, true, height);
            res1 = syncCase2(kernel3.getBlockchain(), true);
            res2 = syncCase2(kernel4.getBlockchain(), false);
            if (height <= 12) {
                assertEquals(res1[0], res2[0]);
                assertEquals(res1[1], res2[1]);
            } else {
                assertNotEquals(res1[0], res2[0]);
                assertEquals(res1[1], res2[1]);
            }
        }
    }

    public String syncCase(Blockchain blockchain, boolean direction) {
        // 1. create case
        KeyPair key = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        List<Address> pending = Lists.newArrayList();

        long generateTime = 1600616700000L;
        long time = XdagTime.msToXdagtimestamp(generateTime);
        long secondTime = XdagTime.getEndOfEpoch(time) + 1;
        long thirdTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime + 64000L));
        long fourthTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime + 64000L + 64000L));

        Block addressBlock = generateAddressBlock(config, key, generateTime);
        pending.add(new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT,false));

        Block secondBlock = generateExtraBlock(config, key, secondTime, pending);
        pending.clear();
        pending.add(new Address(secondBlock.getHashLow(), XDAG_FIELD_OUT,false));

        Block thirdBlock = generateExtraBlock(config, key, thirdTime, pending);
        pending.clear();
        pending.add(new Address(secondBlock.getHashLow(), XDAG_FIELD_OUT,false));

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
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            long xdagTime = XdagTime.getEndOfEpoch(generateTime);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
        }
        String res = getStateByFlags(blockchain.getBlockByHash(secondBlock.getHashLow(), false).getInfo().getFlags());
        return res;
    }


    public String[] syncCase2(Blockchain blockchain, boolean direction) {
        // 1. create case
        KeyPair key = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        List<Address> pending = Lists.newArrayList();

        long generateTime = 1600616700000L;
        long time;
        ImportResult result;

        Block addressBlock = generateAddressBlock(config, key, generateTime);

        // 1. 加入地址块
        result = blockchain.tryToConnect(addressBlock);
        assertSame(result, IMPORTED_BEST);
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();

        // 2. create 100 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // 第一个case
        generateTime += 64000L;
        long tempTime = XdagTime.msToXdagtimestamp(generateTime);
        long firstTime = XdagTime.getEndOfEpoch(tempTime);
        pending.clear();
        pending.add(new Address(ref, XDAG_FIELD_OUT,false));
        Block A = generateExtraBlock(config, key, firstTime, pending);
        pending.clear();
        pending.add(new Address(A.getHashLow(), XDAG_FIELD_OUT,false));

        long secondTime = XdagTime.getEndOfEpoch(tempTime) + 1;
        Block B = generateExtraBlock(config, key, secondTime, pending);
        pending.clear();
        pending.add(new Address(B.getHashLow(), XDAG_FIELD_OUT,false));

        generateTime += 64000L;
        tempTime = XdagTime.msToXdagtimestamp(generateTime);
        long thirdTime = XdagTime.getEndOfEpoch(tempTime);
        Block C = generateExtraBlock(config, key, thirdTime, pending);
        pending.clear();
        pending.add(new Address(B.getHashLow(), XDAG_FIELD_OUT,false));

        generateTime += 64000L;
        tempTime = XdagTime.msToXdagtimestamp(generateTime);
        long fourthTime = XdagTime.getEndOfEpoch(tempTime);
        Block D = generateExtraBlock(config, key, fourthTime, pending);

        result = blockchain.tryToConnect(A);
        result = blockchain.tryToConnect(B);
        assertEquals(IMPORTED_BEST, result);

        if (direction) { //正向
            result = blockchain.tryToConnect(C);
            result = blockchain.tryToConnect(D);
        } else { //反向
            result = blockchain.tryToConnect(D);
            result = blockchain.tryToConnect(C);
        }

        ref = D.getHashLow();

        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
        }

        generateTime += 64000L;
        tempTime = XdagTime.msToXdagtimestamp(generateTime);
        firstTime = XdagTime.getEndOfEpoch(tempTime);
        pending.clear();
        pending.add(new Address(ref, XDAG_FIELD_OUT,false));
        Block A2 = generateExtraBlock(config, key, firstTime, pending);
        pending.clear();
        pending.add(new Address(A2.getHashLow(), XDAG_FIELD_OUT,false));

        secondTime = XdagTime.getEndOfEpoch(tempTime) + 1;
        Block B2 = generateExtraBlock(config, key, secondTime, pending);
        pending.clear();
        pending.add(new Address(B2.getHashLow(), XDAG_FIELD_OUT,false));

        generateTime += 64000L;
        tempTime = XdagTime.msToXdagtimestamp(generateTime);
        thirdTime = XdagTime.getEndOfEpoch(tempTime);
        Block C2 = generateExtraBlockGivenRandom(config, key, thirdTime, pending, "1235");
        pending.clear();
        pending.add(new Address(B2.getHashLow(), XDAG_FIELD_OUT,false));

        generateTime += 64000L;
        tempTime = XdagTime.msToXdagtimestamp(generateTime);
        fourthTime = XdagTime.getEndOfEpoch(tempTime);
        Block D2 = generateExtraBlock(config, key, fourthTime, pending);

        result = blockchain.tryToConnect(A2);
        result = blockchain.tryToConnect(B2);
        assertEquals(IMPORTED_BEST, result);

        if (direction) { //正向
            result = blockchain.tryToConnect(C2);
            result = blockchain.tryToConnect(D2);
        } else { //反向
            result = blockchain.tryToConnect(D2);
            result = blockchain.tryToConnect(C2);
        }

        ref = D2.getHashLow();

        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
        }

        String res1 = getStateByFlags(blockchain.getBlockByHash(B.getHashLow(), false).getInfo().getFlags());
        String res2 = getStateByFlags(blockchain.getBlockByHash(B2.getHashLow(), false).getInfo().getFlags());
        return new String[]{res1, res2};
    }

    public Kernel createKernel(TemporaryFolder root, boolean isNewVersion, int forkHeight) throws Exception {
        Config config = new DevnetConfig();
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        String pwd = "password";
        Wallet wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));

        Kernel kernel = new Kernel(config);
        DatabaseFactory dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));

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
            blockchain = new NewMockBlockchain(kernel, forkHeight);
        }
        kernel.setBlockchain(blockchain);
        return kernel;
    }

    static class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain(long period) {
        }

        @Override
        public boolean isSyncFixFork(long currentHeight) {
            return false;
        }

    }

    static class NewMockBlockchain extends BlockchainImpl {

        int testForkHeight;

        public NewMockBlockchain(Kernel kernel, int testForkHeight) {
            super(kernel);
            this.testForkHeight = testForkHeight;
        }

        @Override
        public void startCheckMain(long period) {
        }

        @Override
        public boolean isSyncFixFork(long currentHeight) {
            return currentHeight >= testForkHeight;
        }
    }

}
