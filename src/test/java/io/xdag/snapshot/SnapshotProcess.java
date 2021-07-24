package io.xdag.snapshot;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.snapshot.config.SnapShotKeys.SNAPSHOT_KEY_STATS_MAIN;
import static io.xdag.snapshot.config.SnapShotKeys.getMutableBytesByKey;
import static io.xdag.snapshot.config.SnapShotKeys.getMutableBytesByKey_;
import static java.nio.ByteBuffer.allocateDirect;
import static java.util.Arrays.copyOfRange;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;
import static org.lmdbjava.Env.create;
import static org.lmdbjava.EnvFlags.MDB_FIXEDMAP;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.RandomXConstants;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockchainImpl;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagTopStatus;
import io.xdag.crypto.ECDSASignature;
import io.xdag.crypto.ECKeyPair;
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
import io.xdag.utils.BytesUtils;
import io.xdag.utils.Numeric;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.xerial.snappy.Snappy;

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
//        createTestBlockchain(snapshotChainStore, kernel);
        createSnapshotData(snapshotChainStore, kernel);
    }


    public void createTestBlockchain(SnapshotChainStore snapshotChainStore, Kernel kernel) {
        long generateTime = 1600616700000L;
        long starttime = generateTime;
        ECKeyPair key = ECKeyPair.create(Numeric.toBigInt(SampleKeys.PRIVATE_KEY_STRING));
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
        assertArrayEquals(addressBlock.getHashLow(), stats.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();

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
            assertArrayEquals(extraBlock.getHashLow(), stats.getTop());
            Block storedExtraBlock = blockchain.getBlockByHash(stats.getTop(), false);
            assertArrayEquals(extraBlock.getHashLow(), storedExtraBlock.getHashLow());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // 最后一个作为extrablock
        this.extrablock = extraBlockList.get(extraBlockList.size() - 1);
        long endTime = extrablock.getTimestamp();

        this.endDiff = blockchain.getXdagTopStatus().getTopDiff();

        List<Block> blocks = blockchain.getBlocksByTime(starttime, endTime);
        List<Block> mains = blockchain.listMainBlocks(10);

        saveBlocks(snapshotChainStore, blocks, key.getPublicKey());

        saveStatsBlock(mains);
    }

    public void saveStatsBlock(List<Block> mains) {
        for (int i = 0; i < mains.size(); i++) {
            Block block = mains.get(i);
            StatsBlock statsBlock = new StatsBlock(block.getInfo().getHeight(), block.getTimestamp(), block.getHash(),
                    block.getInfo().getDifficulty());
            snapshotChainStore.saveSnaptshotStatsBlock(i, statsBlock);
        }
    }

    public void saveBlocks(SnapshotChainStore snapshotChainStore, List<Block> blocks, BigInteger pubkey) {
        int i = 0;
        for (Block block : blocks) {

            BalanceData balanceData = new BalanceData(block.getInfo().getAmount(), block.getTimestamp(),
                    block.getHash(), block.getInfo().getFlags());
            if (i < 30) {
                snapshotChainStore
                        .saveSnapshotUnit(block.getHash(),
                                new SnapshotUnit(null, balanceData, block.getXdagBlock().getData(), block.getHash()));
                // TODO: 部分区块用pubkey存储，部分用整个data存储，交易相关的要涉及输入pubkey跟输入data两种
            } else {
                snapshotChainStore.saveSnapshotUnit(block.getHash(),
                        new SnapshotUnit(pubkey.toByteArray(), balanceData, null, block.getHash()));
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

    @Test
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
        System.out.println(blockchain.getBlockByHash(blockchain.getXdagTopStatus().getTop(), false).getInfo());
        System.out.println(Hex.toHexString(blockchain.getXdagTopStatus().getTop()));
        System.out.println(blockchain.getXdagTopStatus().getTopDiff().toString(16));
        // add new block
//        ImportResult result = blockchain.tryToConnect(this.extrablock);
//        assertEquals(IMPORTED_BEST, result);
//        assertEquals(blockchain.getXdagTopStatus().getTopDiff(), endDiff);
    }

    public void createSnapshotData(SnapshotChainStore snapshotChainStore, Kernel kernel) throws IOException {
        Set<Bytes32> set = new HashSet<>();
        Map<Bytes32, BalanceData> balanceDataMap = new HashMap<>();
        Map<Bytes32, ECKeyPair> ecKeyPairHashMap = new HashMap<>();
        Map<Bytes32, ECDSASignature> signatureHashMap = new HashMap<>();
        Map<Bytes32, Block> blockHashMap = new HashMap<>();
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/5000");
        Env<ByteBuffer> env = create()
                .setMaxReaders(8)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_FIXEDMAP, MDB_NOSYNC);
        final Dbi<ByteBuffer> balance_db = env.openDbi("balance", MDB_CREATE, MDB_INTEGERKEY);
        Txn<ByteBuffer> balance_txn = env.txnRead();
        try (CursorIterable<ByteBuffer> ci = balance_db.iterate(balance_txn, KeyRange.all())) {
            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                BalanceData data = BalanceData.parse(Bytes.wrapByteBuffer(kv.key()), Bytes.wrapByteBuffer(kv.val()));
                set.add(Bytes32.wrap(data.getHash()));
                balanceDataMap.put(Bytes32.wrap(data.getHash()), data);
            }
        }
        System.out.println("balancemap size:" + balanceDataMap.size());
        balance_txn.close();
        File file_pub = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/pubkey");
        Env<ByteBuffer> env_pub = create()
                .setMaxReaders(1)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file_pub, MDB_NOSYNC);
        final Dbi<ByteBuffer> pub_db = env_pub.openDbi("pubkey", MDB_CREATE);
        Txn<ByteBuffer> pub_txn = env_pub.txnRead();
        try (CursorIterable<ByteBuffer> ci = pub_db.iterate(pub_txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                ECKeyPair ecKeyPair = new ECKeyPair(null,
                        new BigInteger(1, copyOfRange(Bytes.wrapByteBuffer(kv.val()).toArray(), 1,
                                Bytes.wrapByteBuffer(kv.val()).size())));
                set.add(Bytes32.wrap(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())));
                ecKeyPairHashMap.put(Bytes32.wrap(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())), ecKeyPair);
            }
        }
        System.out.println("pubkey size:" + ecKeyPairHashMap.size());
        pub_txn.close();

        final Dbi<ByteBuffer> sig_db = env_pub.openDbi("signature", MDB_CREATE);

        Txn<ByteBuffer> sig_txn = env_pub.txnRead();

        try (CursorIterable<ByteBuffer> ci = sig_db.iterate(sig_txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                BigInteger r;
                BigInteger s;

                r = Numeric.toBigInt(Bytes.wrapByteBuffer(kv.val()).slice(0, 32).toArray());
                s = Numeric.toBigInt(Bytes.wrapByteBuffer(kv.val()).slice(32, 32).toArray());
                ECDSASignature ecdsaSignature = new ECDSASignature(r, s);
                set.add(Bytes32.wrap(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())));
                signatureHashMap
                        .put(Bytes32.wrap(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())), ecdsaSignature);
            }
        }

        System.out.println("sig size:" + signatureHashMap.size());
        sig_txn.close();

        final Dbi<ByteBuffer> block_db = env_pub.openDbi("block", MDB_CREATE);

        Txn<ByteBuffer> block_txn = env_pub.txnRead();

        try (CursorIterable<ByteBuffer> ci = block_db.iterate(block_txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                Bytes bytes = Bytes.wrapByteBuffer(kv.val());
                byte[] uncompress = Snappy.uncompress(bytes.toArray());
                Block block = new Block(new XdagBlock(uncompress));
                set.add(Bytes32.wrap(block.getHash()));
                blockHashMap.put(Bytes32.wrap(block.getHash()), block);
            }
        }
        System.out.println("block size:" + blockHashMap.size());
        block_txn.close();

        for (Bytes32 bytes32 : set) {
            BalanceData balanceData = balanceDataMap.get(bytes32);
            ECKeyPair ecKeyPair = ecKeyPairHashMap.get(bytes32);
            ECDSASignature signature = signatureHashMap.get(bytes32);
            Block block = blockHashMap.get(bytes32);
            if (balanceData == null) {
                balanceData = new BalanceData();
                if (ecKeyPair != null) {
                    balanceData.setHash(bytes32.toArray());
                }
            }
            byte[] data = null;
            if (block == null && signature != null) {
                data = createXdagBlock(signature, balanceData);
            } else if (block != null) {
                data = block.getXdagBlock().getData();
            }
            SnapshotUnit snapshotUnit = new SnapshotUnit(
                    ecKeyPair == null ? null : ecKeyPair.getPublicKey().toByteArray(), balanceData, data,
                    bytes32.toArray());
            snapshotChainStore.saveSnapshotUnit(bytes32.toArray(), snapshotUnit);
        }

        Dbi<ByteBuffer> stats_db = env.openDbi("stats", MDB_CREATE);
        ByteBuffer snapshotHeightKey = allocateDirect(getMutableBytesByKey(SNAPSHOT_KEY_STATS_MAIN).size());
        snapshotHeightKey.put(getMutableBytesByKey(SNAPSHOT_KEY_STATS_MAIN).toArray()).flip();
        Txn<ByteBuffer> stats_txn = env.txnRead();
        ByteBuffer snapshotHeight = stats_db.get(stats_txn, snapshotHeightKey);

        StatsBlock snapshotHeightBlock = StatsBlock
                .parse(Bytes.wrapByteBuffer(snapshotHeightKey), Bytes.wrapByteBuffer(snapshotHeight));
        snapshotChainStore.saveSnaptshotStatsBlock(0, snapshotHeightBlock);

        for (int i = 1; i <= 128; i++) {
            String key = SNAPSHOT_KEY_STATS_MAIN + "_" + i;
            ByteBuffer snapshotey = allocateDirect(getMutableBytesByKey_(key).size());
            snapshotey.put(getMutableBytesByKey_(key).toArray()).flip();
            ByteBuffer snapshot = stats_db.get(stats_txn, snapshotey);
            StatsBlock snapshotBlock = StatsBlock
                    .parse(Bytes.wrapByteBuffer(snapshotey), Bytes.wrapByteBuffer(snapshot));
            snapshotChainStore.saveSnaptshotStatsBlock(i, snapshotBlock);
        }
        stats_txn.close();

        env.close();
        env_pub.close();
    }

    public byte[] createXdagBlock(ECDSASignature signature, BalanceData balanceData) {
        MutableBytes mutableBytes = MutableBytes.create(512);
        byte[] transportHeader = BytesUtils.longToBytes(0, true);
        byte[] type = BytesUtils.longToBytes(1368, true);
        byte[] time = BytesUtils.longToBytes(balanceData.getTime(), true);
        byte[] fee = BytesUtils.longToBytes(0, true);
        byte[] sig = BytesUtils.subArray(signature.toByteArray(), 0, 64);
        mutableBytes.set(0, Bytes.wrap(transportHeader));
        mutableBytes.set(8, Bytes.wrap(type));
        mutableBytes.set(16, Bytes.wrap(time));
        mutableBytes.set(24, Bytes.wrap(fee));
        mutableBytes.set(32, Bytes.wrap(sig));
        return mutableBytes.toArray();
    }

    static class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void initSnapshot() {

        }

        @Override
        public void startCheckMain() {

        }

    }
}