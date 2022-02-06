package io.xdag.snapshot;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.RandomXConstants;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagTopStatus;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.randomx.RandomX;
import io.xdag.snapshot.core.BalanceData;
import io.xdag.snapshot.core.SnapshotUnit;
import io.xdag.snapshot.core.StatsBlock;
import io.xdag.snapshot.db.SnapshotChainStore;
import io.xdag.snapshot.db.SnapshotChainStoreImpl;
import io.xdag.utils.Numeric;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Slf4j
public class SnapshotProcess {

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
    @Rule
    public TemporaryFolder root1 = new TemporaryFolder();
    @Rule
    public TemporaryFolder root2 = new TemporaryFolder();
    public BigInteger endDiff;
    public Block extrablock;
    Config config = new DevnetConfig();
    SnapshotChainStore snapshotChainStore;
    private long forkHeight;

    @Before
    public void init() {
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 64;
        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 128;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;
        forkHeight = 3;

    }

    public Kernel createKernel(TemporaryFolder root, boolean enableSnapshot) throws Exception {
        Config config = new DevnetConfig();
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        if (enableSnapshot) {
            config.getSnapshotSpec().snapshotEnable();
            config.getSnapshotSpec().setSnapshotHeight(100);
        }

        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        String pwd = "password";
        Wallet wallet = new Wallet(config);
        wallet.unlock(pwd);
        SECP256K1.KeyPair key = SECP256K1.KeyPair.fromSecretKey(SampleKeys.SRIVATE_KEY);
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

        RandomX randomX = new RandomX(config);
        kernel.setRandomXUtils(randomX);

        MockBlockchain blockchain = new MockBlockchain(kernel);
        kernel.setBlockchain(blockchain);
        randomX.init();

        return kernel;
    }


    @Before
    public void setUp() throws Exception {
        Kernel kernel = createKernel(root1, false);
        snapshotChainStore = new SnapshotChainStoreImpl(
                new RocksdbFactory(kernel.getConfig()).getDB(DatabaseName.SNAPSHOT));
        snapshotChainStore.reset();
        createSnapshotStore(snapshotChainStore, kernel);
    }

    public void createSnapshotStore(SnapshotChainStore snapshotChainStore, Kernel kernel) throws IOException {
        createTestBlockchain(snapshotChainStore, kernel);
//        createSnapshotData(snapshotChainStore, kernel);
    }


    public void createTestBlockchain(SnapshotChainStore snapshotChainStore, Kernel kernel) {
        long generateTime = 1600616700000L;
        long starttime = generateTime;
        SECP256K1.KeyPair key = SECP256K1.KeyPair.fromSecretKey(SampleKeys.SRIVATE_KEY);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        XdagTopStatus stats = blockchain.getXdagTopStatus();
        assertNotNull(stats);
        List<Address> pending = Lists.newArrayList();

        ImportResult result;
        log.debug("1. create 1 address block");
        Block addressBlock = generateAddressBlock(config, key, generateTime);
        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertSame(result, IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow().toArray(), stats.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();

        // 2. create 99 mainblocks + 1 extrablock
        for (int i = 1; i <= 100; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertArrayEquals(extraBlock.getHashLow().toArray(), stats.getTop());
            Block storedExtraBlock = blockchain.getBlockByHash(Bytes32.wrap(stats.getTop()), false);
            assertArrayEquals(extraBlock.getHashLow().toArray(), storedExtraBlock.getHashLow().toArray());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // 最后一个作为extrablock
        this.extrablock = extraBlockList.get(extraBlockList.size() - 1);
        long endTime = extrablock.getTimestamp();

        this.endDiff = blockchain.getXdagTopStatus().getTopDiff();

        List<Block> blocks = blockchain.getBlocksByTime(starttime, endTime);
        List<Block> mains = blockchain.listMainBlocks(10);

        saveBlocks(snapshotChainStore, blocks, key.publicKey().asEcPoint().getEncoded(true));

        saveStatsBlock(mains);
    }

    public void saveStatsBlock(List<Block> mains) {
        for (int i = 0; i < mains.size(); i++) {
            Block block = mains.get(i);
            StatsBlock statsBlock = new StatsBlock(block.getInfo().getHeight(), block.getTimestamp(),
                    block.getHash().toArray(),
                    block.getInfo().getDifficulty());
            snapshotChainStore.saveSnaptshotStatsBlock(i, statsBlock);
        }
    }

    public void saveBlocks(SnapshotChainStore snapshotChainStore, List<Block> blocks, byte[] pubkey) {
        int i = 0;
        for (Block block : blocks) {

            BalanceData balanceData = new BalanceData(block.getInfo().getAmount(), block.getTimestamp(),
                    block.getHash().toArray(), block.getInfo().getFlags());
            if (i < 30) {
                snapshotChainStore
                        .saveSnapshotUnit(block.getHash().toArray(),
                                new SnapshotUnit(null, balanceData, block.getXdagBlock().getData().toArray(),
                                        block.getHash().toArray()));
                // TODO: 部分区块用pubkey存储，部分用整个data存储，交易相关的要涉及输入pubkey跟输入data两种
            } else {
                snapshotChainStore.saveSnapshotUnit(block.getHash().toArray(),
                        new SnapshotUnit(pubkey, balanceData, null, block.getHash().toArray()));
            }
            i++;
        }
    }

    @Test
    public void getBlocksFromTest() throws Exception {
        List<SnapshotUnit> snapshotUnits = snapshotChainStore.getAllSnapshotUnit();
        assertEquals(snapshotUnits.size(), 100);
        List<StatsBlock> statsBlocks = snapshotChainStore.getSnapshotStatsBlock();
        assertEquals(statsBlocks.size(), 10);

        Kernel kernel = createKernel(root2, true);
        MockBlockchain blockchain = (MockBlockchain) kernel.getBlockchain();
        blockchain.setSnapshotChainStore(snapshotChainStore);
        // init snapshot
        blockchain.initSnapshotChain();
        blockchain.initStats();
        // add new block
        ImportResult result = blockchain.tryToConnect(this.extrablock);
        assertEquals(IMPORTED_BEST, result);
        assertEquals(blockchain.getXdagTopStatus().getTopDiff(), endDiff);
    }

    //    @Test
    public void getBlocksFromSnapshot() throws Exception {
        List<SnapshotUnit> snapshotUnits = snapshotChainStore.getAllSnapshotUnit();
        System.out.println(snapshotUnits.size());
        List<StatsBlock> statsBlocks = snapshotChainStore.getSnapshotStatsBlock();
        System.out.println(statsBlocks.size());
        Kernel kernel = createKernel(root2, true);
        MockBlockchain blockchain = (MockBlockchain) kernel.getBlockchain();
        blockchain.setSnapshotChainStore(snapshotChainStore);
        // init snapshot
        blockchain.initSnapshotChain();
        blockchain.initStats();
        System.out.println(
                blockchain.getBlockByHash(Bytes32.wrap(blockchain.getXdagTopStatus().getTop()), false).getInfo());
        System.out.println(Hex.toHexString(blockchain.getXdagTopStatus().getTop()));
        System.out.println(blockchain.getXdagTopStatus().getTopDiff().toString(16));
        // add new block
//        ImportResult result = blockchain.tryToConnect(this.extrablock);
//        assertEquals(IMPORTED_BEST, result);
//        assertEquals(blockchain.getXdagTopStatus().getTopDiff(), endDiff);
    }


    public void createSnapshotData(SnapshotChainStore snapshotChainStore, Kernel kernel) throws IOException {
        if (snapshotChainStore
                .loadFromSnapshotData("/Users/paulochen/Documents/projects/self/xdag/build/snapshot", false,
                        new ArrayList<>())) {
            System.out.println("load success");
        }
    }

    static class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void initSnapshot() {

        }

        @Override
        public void startCheckMain(long period) {

        }

    }
}