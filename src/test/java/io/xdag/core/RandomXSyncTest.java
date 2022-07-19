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

package io.xdag.core;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.BlockBuilder.generateExtraBlockGivenRandom;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.RandomXConstants;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.randomx.RandomX;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Slf4j
public class RandomXSyncTest {

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
    private final String privString = "10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046";
    private final BigInteger privateKeyBigint = new BigInteger(privString, 16);
    private final SECPPrivateKey privateKey =  SECPPrivateKey.create(privateKeyBigint, Sign.CURVE_NAME);
    @Rule
    public TemporaryFolder root1 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root2 = new TemporaryFolder();
    Config config = new DevnetConfig();
    private long forkHeight;

    @Before
    public void init() {
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 64;
        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 128;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;
        forkHeight = 3;
    }

    @Test
    public void syncTest() throws Exception {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 构建三个mockKernel
        Kernel kernel1 = createKernel(root1);
        Kernel kernel2 = createKernel(root2);

        // 第一个kernel新增区块数据
        long end = addBlocks(kernel1, 130);
        log.debug("Add block done");
        long nmain = kernel1.getBlockchain().getXdagStats().nmain;
        String expected = kernel1.getBlockchain().getBlockByHeight(nmain - 1).getInfo().getDifficulty().toString(16);

        // 第二个跟第三个同步第一个的数据
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread1 = new SyncThread(latch, kernel1, kernel2, generateTime, end, "1");
        thread1.start();

        latch.await();

        String kernel2Diff = kernel2.getBlockchain().getBlockByHeight(nmain - 1).getInfo().getDifficulty().toString(16);
//        System.out.println("第二次同步");
        assertEquals(expected, kernel2Diff);

        kernel1.getRandomx().randomXPoolReleaseMem();
        kernel2.getRandomx().randomXPoolReleaseMem();
    }

    public void sync(Kernel kernel1, Kernel kernel2, long startTime, long endTime, String syncName) {

        List<Block> blocks = kernel1.getBlockchain().getBlocksByTime(startTime, endTime);
        for (Block block : blocks) {
            ImportResult result = kernel2.getBlockchain()
                    .tryToConnect(new Block(new XdagBlock(block.getXdagBlock().getData())));
        }
    }

    // return endTime
    public long addBlocks(Kernel kernel, int number) throws ParseException {
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();

//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
//        ECKeyPair key = ECKeyPair.create(privateKey);
        KeyPair key = KeyPair.create(privateKey, Sign.CURVE, Sign.CURVE_NAME);
//        System.out.println(key.getPrivateKey().toString(16));
        List<Address> pending = Lists.newArrayList();

        ImportResult result;
        log.debug("1. create 1 address block");

        Block addressBlock = generateAddressBlock(config, key, generateTime);

        // 1. add address block
        result = kernel.getBlockchain().tryToConnect(addressBlock);
        assertSame(result, IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow().toArray(), xdagTopStatus.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();
        long endTime = 0;

        Bytes32 forkHash = Bytes32.ZERO;
        long forkDate = 0;

        for (int i = 1; i <= number; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            result = kernel.getBlockchain().tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertArrayEquals(extraBlock.getHashLow().toArray(), xdagTopStatus.getTop());
            Block storedExtraBlock = kernel.getBlockchain().getBlockByHash(Bytes32.wrap(xdagTopStatus.getTop()), false);
            assertArrayEquals(extraBlock.getHashLow().toArray(), storedExtraBlock.getHashLow().toArray());
            ref = extraBlock.getHashLow();
            if (i == number - forkHeight) {
                forkHash = ref;
                forkDate = generateTime;
            }
            extraBlockList.add(extraBlock);
        }

        generateTime = forkDate;
        ref = forkHash;

        // 3. create number fork blocks
        for (int i = 0; i < forkHeight + 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlockGivenRandom(config, key, xdagTime, pending, "3456");
            kernel.getBlockchain().tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            endTime = xdagTime;
        }

        return endTime;
    }

    public Kernel createKernel(TemporaryFolder root) throws Exception {
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

        RandomX randomX = new RandomX(config);
        kernel.setRandomx(randomX);

        MockBlockchain blockchain = new MockBlockchain(kernel);
        kernel.setBlockchain(blockchain);
        randomX.init();

        return kernel;
    }

    class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain(long period) {

        }
    }

    public class SyncThread extends Thread {

        private CountDownLatch latch;
        private Kernel kernel1;
        private Kernel kernel2;
        private long startTime;
        private long endTime;
        private String syncName;

        public SyncThread(CountDownLatch latch, Kernel kernel1, Kernel kernel2, long startTime, long endTime,
                String syncName) {
            this.latch = latch;
            this.kernel1 = kernel1;
            this.kernel2 = kernel2;
            this.startTime = startTime;
            this.endTime = endTime;
            this.syncName = syncName;
        }

        @Override
        public void run() {
            synchronized (SyncThread.class) {
                sync(kernel1, kernel2, startTime, endTime, syncName);
                latch.countDown();
            }
        }

    }
}
