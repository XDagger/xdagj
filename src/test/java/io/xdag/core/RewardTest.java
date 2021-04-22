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

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RewardTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    Config config = new Config();
    OldWallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;

    class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public long getReward(long nmain) {
            long start = getStartAmount(nmain);
            return start >> (nmain >> 4);
        }
    }

    @Before
    public void setUp() throws Exception {
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());

        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        xdagWallet = new OldWallet();
        xdagWallet.init(config);

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

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
    }

    @Test
    public void testReward() throws ParseException {
        String privString = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
        BigInteger privateKey = new BigInteger(privString, 16);

        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 16000;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 16;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;

        RandomX randomXUtils = new RandomX();
        randomXUtils.init();
        kernel.setRandomXUtils(randomXUtils);

        byte[] targetBlock = new byte[0];

        ECKeyPair addrKey = ECKeyPair.create(privateKey);
        ECKeyPair poolKey = ECKeyPair.create(privateKey);
        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        // 1. add one address block
        Block addressBlock = generateAddressBlock(addrKey, date.getTime());
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertTrue(result == IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();

        byte[] unwindRef = null;
        Date unwindDate = null;
        // 2. create 20 mainblocks
        for(int i = 1; i <= 20; i++) {
            date = DateUtils.addSeconds(date, 64);
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(date.getTime());
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            ref = extraBlock.getHashLow();
            if (i == 10) {
                unwindRef = ref.clone();
                unwindDate = date;
            }
            if (i == 15) {
                targetBlock = ref.clone();
            }

            extraBlockList.add(extraBlock);
        }

        date = unwindDate;
        ref = unwindRef;

        // 3. create 20 fork blocks
        for (int i = 0; i < 30; i++ ) {
            date = DateUtils.addSeconds(date, 64);
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(date.getTime());
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlockGivenRandom(poolKey, xdagTime, pending,"3456");
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }


        assertEquals(0,blockchain.getBlockByHash(targetBlock,false).getInfo().getAmount());

    }


}
