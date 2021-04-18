package io.xdag.randomx;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.RandomXConstants;
import io.xdag.core.*;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.crypto.Sign;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.INVALID_BLOCK;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static org.junit.Assert.*;

@Slf4j
public class RandomXTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    private final String privString = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
    private final BigInteger privateKey = new BigInteger(privString, 16);

    Config config = new Config();
    OldWallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;
    BlockchainImpl blockchain;
    RandomX randomX;
    @Before
    public void setUp() throws Exception {
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());

        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 32;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 16;


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

        randomX = new RandomX();
        kernel.setRandomXUtils(randomX);

        blockchain = new BlockchainImpl(kernel);
        randomX.init();
    }




    public void addMainBlock() throws ParseException {

        XdagTopStatus xdagTopStatus = blockchain.getXdagTopStatus();

        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        ECKeyPair key = ECKeyPair.create(privateKey);
        List<Address> pending = Lists.newArrayList();

        ImportResult result = INVALID_BLOCK;
        log.debug("1. create 1 address block");

        Block addressBlock = generateAddressBlock(key, date.getTime());

        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertTrue(result == IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow(), xdagTopStatus.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();
        for(int i = 1; i <= 100; i++) {
            log.debug("create No." + i + " extra block");
            date = DateUtils.addSeconds(date, 64);
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(date.getTime());
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(key, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            assertArrayEquals(extraBlock.getHashLow(), xdagTopStatus.getTop());
            Block storedExtraBlock = blockchain.getBlockByHash(xdagTopStatus.getTop(), false);
            assertArrayEquals(extraBlock.getHashLow(), storedExtraBlock.getHashLow());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }
    }


    @Test
    public void testDiffCalculate() {

        String expectedRawDiff = "382ceb150";

        String[] blocks = new String[]{
                "000000000000000000000181cac9ffff40000000000055380000000000000000",
                "000000000000000019e5f0b41d83d26cd9f0c5855f36f75c369c51121a7e62c3",
                "8fb20675341a8d9314633c402593f3e5c9cc2b19bf9dc356f13eb632116a0b8d",
                "77cb18fc224742884641595ba808e608d5be56608c9ea888bbf73ee238b193f4",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "7a5da62bcee326f589cab57cf943b843fdf4c390a56ba803dbaf408cfcd2e2c0"
        };

        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0; i < 16; i++) {
            stringBuilder.append(Hex.toHexString(Arrays.reverse(Hex.decode(blocks[i]))));
        }

        Block block = new Block(new XdagBlock(Hex.decode(stringBuilder.toString())));
        BigInteger rawDiff = blockchain.getDiffByRawHash(block.recalcHash());
        assertEquals(expectedRawDiff,rawDiff.toString(16));
    }


    @Test
    public void testRandomXBlockHash() throws ParseException {
        String expectedDiff = "31943fcc136";
        addMainBlock();
        randomX.randomXPoolReleaseMem();
        assertEquals(expectedDiff,blockchain.getXdagTopStatus().getTopDiff().toString(16));
    }


    @Test
    public void testECKeypairParity() {
        ECKeyPair ecKey = Keys.createEcKeyPair();
        byte[] publicKeyBytes = Sign.publicKeyBytesFromPrivate(ecKey.getPrivateKey(), false);
        byte lastByte = publicKeyBytes[publicKeyBytes.length - 1];
        // 奇偶
        boolean pubKeyParity = (lastByte & 1) == 0;

        // 奇偶
        boolean pubKeyParity1 = !ecKey.getPublicKey().testBit(0);
        assertEquals(pubKeyParity, pubKeyParity1);
    }

}
