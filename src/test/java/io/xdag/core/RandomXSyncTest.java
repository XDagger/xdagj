package io.xdag.core;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.RandomXConstants;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.randomx.RandomX;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.*;

@Slf4j
public class RandomXSyncTest {

    @Rule
    public TemporaryFolder root1 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root2 = new TemporaryFolder();


    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
    private final String privString = "421d725da10c056a955d2444a5a043b1a5d4515db126b8631806a8ccbda93369";
    private final BigInteger privateKey = new BigInteger(privString, 16);

    private long forkHeight;

    @Before
    public void init() {
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 64;
        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 128;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;
        forkHeight = 3;

    }

    class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain() {

        }
    }

    @Test
    public void syncTest() throws Exception {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 构建三个mockKernel
        Kernel kernel1 = createKernel(root1);
        Kernel kernel2 = createKernel(root2);

        // 第一个kernel新增区块数据
        long end = addBlocks(kernel1,130);
        log.debug("Add block done");
        long nmain = kernel1.getBlockchain().getXdagStats().nmain;
        String expected = kernel1.getBlockchain().getBlockByHeight(nmain-1).getInfo().getDifficulty().toString(16);

        // 第二个跟第三个同步第一个的数据
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread1 = new SyncThread(latch,kernel1,kernel2,generateTime,end,"1");
        thread1.start();

        latch.await();

        String kernel2Diff = kernel2.getBlockchain().getBlockByHeight(nmain-1).getInfo().getDifficulty().toString(16);
//        System.out.println("第二次同步");
        assertEquals(expected,kernel2Diff);

        kernel1.getRandomXUtils().randomXPoolReleaseMem();
        kernel2.getRandomXUtils().randomXPoolReleaseMem();
    }

    public class SyncThread extends Thread {
        private CountDownLatch latch;
        private Kernel kernel1;
        private Kernel kernel2;
        private long startTime;
        private long endTime;
        private String syncName;

        public SyncThread(CountDownLatch latch, Kernel kernel1, Kernel kernel2, long startTime, long endTime, String syncName) {
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
                sync(kernel1,kernel2,startTime,endTime,syncName);
                latch.countDown();
            }
        }

    }

    public void sync(Kernel kernel1,Kernel kernel2, long startTime, long endTime, String syncName) {

        List<Block> blocks = kernel1.getBlockchain().getBlocksByTime(startTime, endTime);
        for (Block block : blocks) {
            ImportResult result = kernel2.getBlockchain().tryToConnect(new Block(new XdagBlock(block.getXdagBlock().getData())));
        }
    }

    // return endTime
    public long addBlocks(Kernel kernel, int number) throws ParseException {
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();

//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
//        ECKeyPair key = ECKeyPair.create(privateKey);
        ECKeyPair key = ECKeyPair.create(privateKey);
//        System.out.println(key.getPrivateKey().toString(16));
        List<Address> pending = Lists.newArrayList();

        ImportResult result;
        log.debug("1. create 1 address block");

        Block addressBlock = generateAddressBlock(key, generateTime);

        // 1. add address block
        result = kernel.getBlockchain().tryToConnect(addressBlock);
        assertSame(result, IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow(), xdagTopStatus.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();
        long endTime = 0;

        byte[] forkHash = new byte[0];
        long forkDate = 0;

        for(int i = 1; i <= number; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(key, xdagTime, pending);
            result = kernel.getBlockchain().tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertArrayEquals(extraBlock.getHashLow(), xdagTopStatus.getTop());
            Block storedExtraBlock = kernel.getBlockchain().getBlockByHash(xdagTopStatus.getTop(), false);
            assertArrayEquals(extraBlock.getHashLow(), storedExtraBlock.getHashLow());
            ref = extraBlock.getHashLow();
            if (i == number-forkHeight) {
                forkHash = ref.clone();
                forkDate = generateTime;
            }
            extraBlockList.add(extraBlock);
        }

        generateTime = forkDate;
        ref = forkHash;

        // 3. create number fork blocks
        for (int i = 0; i < forkHeight + 10; i++ ) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlockGivenRandom(key, xdagTime, pending, "3456");
            kernel.getBlockchain().tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            endTime = xdagTime;
        }

        return endTime;
    }

    public Kernel createKernel(TemporaryFolder root) throws Exception {
        Config config = new Config();
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());
        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        OldWallet xdagWallet = new OldWallet();
        xdagWallet.init(config);
        xdagWallet.createNewKey();


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
        kernel.setWallet(xdagWallet);

        RandomX randomX = new RandomX();
        kernel.setRandomXUtils(randomX);

        MockBlockchain blockchain = new MockBlockchain(kernel);
        kernel.setBlockchain(blockchain);
        randomX.init();

        return kernel;
    }
}
