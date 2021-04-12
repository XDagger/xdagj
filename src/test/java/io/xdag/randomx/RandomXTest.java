package io.xdag.randomx;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.core.*;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.utils.BytesUtils;
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
import static io.xdag.utils.BasicUtils.getDiffByHash;
import static org.junit.Assert.*;

@Slf4j
public class RandomXTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

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
        randomX.randomXLoadingForkTime();
    }




    @Test
    public void addMainBlock() throws ParseException {
        XdagStats stats = blockchain.getXdagStats();
        XdagTopStatus xdagTopStatus = blockchain.getXdagTopStatus();


        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        ECKey key = new ECKey();
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
        for(int i = 1; i <= 200; i++) {
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
        System.out.println(stats.nmain);
    }

    public void unwindMainBlock() {

    }

    @Test
    public void testIsFork() throws ParseException {
        addMainBlock();
        assertTrue(randomX.isRandomxFork(25009794+1));
        unwindMainBlock();
    }

    @Test
    public void testRandomXHash() throws ParseException {
        addMainBlock();
        ECKey ecKey = new ECKey();
        Block block = generateAddressBlock(ecKey,25009890);
        BigInteger rawDiff = blockchain.getDiffByRawHash(block.getHash());
        System.out.println("Raw Diff:"+rawDiff.toString(16));
        BigInteger rxDiff = blockchain.getDiffByRandomXHash(block);
        System.out.println("RandomX Diff:"+rxDiff.toString(16));
    }


    @Test
    public void testDiffCalculate() {
        String expectedRawDiff = "461465028";

        String[] blocks = new String[]{
                "000000000000000000000181caa5ffff40000000005533380000000000000000",
                "0000000000000000ec6bda9802c1779f2cd9d9785917669eaa6d61cb9928cd8a",
                "00000000000000008b956448c1990e16386ae1a962e47213f21b57b6a7d6e9eb",
                "0000000000000000e3eac3bf10f4d30e0708f3b86c7e7028866b3f4270870b78",
                "b76d51c96bb292fd1d208a10219c691e7eccd3e0956bd75517e706a8e969a288",
                "170e82edc59bca5e2e8742ee2c65ff291a8eb2acf2c28fa40a1cec761ac021ad",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "561452034c58c079dc06665e485f1ab2fca8cdde5bcc8e8ceed527f1495c0c8f"
        };


        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0; i < 16; i++) {
            stringBuilder.append(Hex.toHexString(Arrays.reverse(Hex.decode(blocks[i]))));
        }


        Block block = new Block(new XdagBlock(Hex.decode(stringBuilder.toString())));
        BigInteger rawDiff = blockchain.getDiffByRawHash(block.recalcHash());

        assertEquals(expectedRawDiff,rawDiff.toString(16));

        String[] rxBlock = new String[]{
                "000000000000000000000181cad2ffff40000000000055380000000000000000",
                "00000000000000005ef574cff88be6bc09e5c9689d0ac7263607249a7b6b570c",
                "572db6fce584bc215291473d9b8cad52fb3a261c42e82ee227d2bcad93289d94",
                "c522105b431ee950578b111ce179a0129d5afb31d08bf00221b52a0d4491c59f",
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
                "62c0b478e98efef59d611aba3109caa95d3e9e3372777a1e733f6c0daf5e037f"
        };

        stringBuilder = new StringBuilder();
        for(int i = 0; i < 16; i++) {
            stringBuilder.append(Hex.toHexString(Arrays.reverse(Hex.decode(rxBlock[i]))));
        }

        block = new Block(new XdagBlock(Hex.decode(stringBuilder.toString())));
        BigInteger rxDiff = blockchain.getDiffByRandomXHash(block);
        System.out.println(rxDiff.toString(16));


    }

    @Test
    public void bytesTest() {
        byte[] a = BytesUtils.longToBytes(255,true);
        byte[] b = BytesUtils.generateRandomArray();

    }


}
