package io.xdag.snapshot;

import static io.xdag.snapshot.config.SnapShotKeys.*;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.lmdbjava.ByteBufferProxy.PROXY_OPTIMAL;
import static org.lmdbjava.DbiFlags.*;
import static org.lmdbjava.DirectBufferProxy.PROXY_DB;
import static org.lmdbjava.Env.create;
import static org.lmdbjava.EnvFlags.MDB_FIXEDMAP;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;
import static org.lmdbjava.GetOp.MDB_SET;
import static org.lmdbjava.SeekOp.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import cn.hutool.core.io.resource.Resource;
import com.google.common.io.Resources;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.XdagState;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.snapshot.core.BalanceData;
import io.xdag.snapshot.core.ExtStatsData;
import io.xdag.snapshot.core.StatsData;
import io.xdag.snapshot.db.SnapshotStore;
import io.xdag.utils.Numeric;
import io.xdag.wallet.Wallet;
import net.bytebuddy.implementation.bytecode.StackSize;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.*;
import org.lmdbjava.CursorIterable.KeyVal;


public class LMDBTest {

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

        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        ECKeyPair key = ECKeyPair.create(Numeric.toBigInt(SampleKeys.PRIVATE_KEY_STRING));
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

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
        kernel.setWallet(wallet);
    }


    @Test
    public void testBalance() throws IOException {
        SnapshotStore snapshotStore = new SnapshotStore(dbFactory.getDB(DatabaseName.SNAPSHOT));
        snapshotStore.reset();

        File file = new File("/Users/punk/Documents/code/java_project/xdagJ_net/snapshot/xdagj/src/test/resources/5000");
        Env<ByteBuffer> env = create()
                .setMaxReaders(4)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file,MDB_FIXEDMAP,MDB_NOSYNC);

        final Dbi<ByteBuffer> db = env.openDbi("balance", MDB_CREATE,MDB_INTEGERKEY);

        Txn<ByteBuffer> txn = env.txnRead();
        int i = 0;
        double totalBalance = 0;

        Bytes32 tempkey = null;
        try (CursorIterable<ByteBuffer> ci = db.iterate(txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
//                System.out.println("key:"+"key size:"+Bytes.wrapByteBuffer(kv.key()).size()+" "+Bytes.wrap(Bytes.wrapByteBuffer(kv.key())).toHexString());
//                System.out.println("value:"+"value size:"+Bytes.wrapByteBuffer(kv.val()).size()+" "+Bytes.wrap(Bytes.wrapByteBuffer(kv.val())).toHexString());

                BalanceData data = BalanceData.parse(Bytes.wrapByteBuffer(kv.key()),Bytes.wrapByteBuffer(kv.val()));
                assert data != null;

                totalBalance += amount2xdag(data.getAmount());
//                System.out.println(data);
                if (i == 0) {
                    // Bytes32 use ref
                    tempkey = Bytes32.wrap(Bytes.wrapByteBuffer(kv.key())).copy();
                    snapshotStore.saveBalanceData(data, Bytes32.wrap(Bytes.wrapByteBuffer(kv.key())).toArray());
                    System.out.println("tempkey:"+tempkey);
                    System.out.println(data);
                }
                i ++;
            }
            ci.close();
        }



        System.out.println("total address:"+i);
        System.out.println("totalbalance:"+totalBalance);


        assert tempkey != null;
        System.out.println("tempkey:"+tempkey);
        System.out.println(snapshotStore.getBalanceData(tempkey.toArray()));

        txn.close();
        env.close();

    }
    @Test
    public void testStats() throws IOException {
        SnapshotStore snapshotStore = new SnapshotStore(dbFactory.getDB(DatabaseName.SNAPSHOT));
        snapshotStore.reset();

        File file = new File("/Users/punk/Documents/code/java_project/xdagJ_net/snapshot/xdagj/src/test/resources/5000");
        Env<ByteBuffer> env = create()
                .setMaxReaders(4)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file,MDB_FIXEDMAP,MDB_NOSYNC);

        Dbi<ByteBuffer> db = env.openDbi("stats", MDB_CREATE);
        ByteBuffer timeKey = allocateDirect(getMutableBytesByKey(SNAPTSHOT_KEY_TIME).size());
        timeKey.put(getMutableBytesByKey(SNAPTSHOT_KEY_TIME).toArray()).flip();
//        final ByteBuffer val = allocateDirect(700);
        Txn<ByteBuffer> txn = env.txnRead();

        ByteBuffer timeValue = db.get(txn, timeKey);
        System.out.println("time:"+Bytes.wrapByteBuffer(timeValue).toLong(ByteOrder.LITTLE_ENDIAN));

        ByteBuffer statsKey = allocateDirect(getMutableBytesByKey(SNAPTSHOT_KEY_STATS).size());
        statsKey.put(getMutableBytesByKey(SNAPTSHOT_KEY_STATS).toArray()).flip();
//        final ByteBuffer val = allocateDirect(700);
        ByteBuffer statsValue = db.get(txn, statsKey);
        System.out.println("stats:"+ StatsData.parse(Bytes.wrapByteBuffer(statsValue)));

        snapshotStore.saveStats(StatsData.parse(Bytes.wrapByteBuffer(statsValue)));

        ByteBuffer extstatsKey = allocateDirect(getMutableBytesByKey(SNAPTSHOT_KEY_EXTSTATS).size());
        extstatsKey.put(getMutableBytesByKey(SNAPTSHOT_KEY_EXTSTATS).toArray()).flip();

        ByteBuffer extstatsValue = db.get(txn, extstatsKey);
        System.out.println("extstats:"+ ExtStatsData.parse(Bytes.wrapByteBuffer(extstatsValue)));

        snapshotStore.saveExtStats(ExtStatsData.parse(Bytes.wrapByteBuffer(extstatsValue)));

        ByteBuffer stateKey = allocateDirect(getMutableBytesByKey(SNAPTSHOT_KEY_STATE).size());
        stateKey.put(getMutableBytesByKey(SNAPTSHOT_KEY_STATE).toArray()).flip();

        ByteBuffer stateValue = db.get(txn, stateKey);
        System.out.println("state:"+ XdagState.fromByte((byte) Bytes.wrapByteBuffer(stateValue).getInt(0)));


        System.out.println("get from kvsource:"+snapshotStore.getStats());
        System.out.println("get from kvsource:"+snapshotStore.getExtStats());

        txn.close();
        env.close();

    }



    // 0x675f736e617073686f745f74696d6500
    // 0x675f786461675f657874737461747300
    // 0x675f786461675f737461746500
    // 0x675f786461675f737461747300
    @Test
    public void test() throws UnsupportedEncodingException {
        byte[] key1 = SNAPTSHOT_KEY_TIME.getBytes("utf-8");
        byte[] key2 = SNAPTSHOT_KEY_STATS.getBytes("utf-8");
        byte[] key3 = SNAPTSHOT_KEY_STATE.getBytes("utf-8");
        byte[] key4 = SNAPTSHOT_KEY_TIME.getBytes("utf-8");
        System.out.println(key1.length+":"+Hex.toHexString(key1));
        System.out.println(key2.length+":"+Hex.toHexString(key2));
        System.out.println(key3.length+":"+Hex.toHexString(key3));
        System.out.println(key4.length+":"+Hex.toHexString(key4));

        System.out.println(getMutableBytesByKey(SNAPTSHOT_KEY_TIME).size());
    }

    @Test
    public void byteTest() throws UnsupportedEncodingException {
        MutableBytes bytes = MutableBytes.create(16);
        bytes.set(0,Bytes.wrap(SNAPTSHOT_KEY_TIME.getBytes("utf-8")));
        System.out.println(bytes.size());
        System.out.println(bytes.toHexString());
    }

    @Test
    public void testPubkey() throws IOException {
        SnapshotStore snapshotStore = new SnapshotStore(dbFactory.getDB(DatabaseName.SNAPSHOT));
        snapshotStore.reset();

        File file = new File("/Users/punk/Documents/code/java_project/xdagJ_net/snapshot/xdagj/src/test/resources/pubkey");
        Env<ByteBuffer> env = create()
                .setMaxReaders(1)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_NOSYNC);

        final Dbi<ByteBuffer> db = env.openDbi("pubkey", MDB_CREATE);

        Txn<ByteBuffer> txn = env.txnRead();

        int i = 0;
        Bytes32 keyhash = null;

        try (CursorIterable<ByteBuffer> ci = db.iterate(txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
//                System.out.println("key size:"+"key size:"+Bytes.wrapByteBuffer(kv.key()).size()+" "+Bytes.wrap(Bytes.wrapByteBuffer(kv.key())).toHexString());
//                System.out.println("value size:"+"value size:"+Bytes.wrapByteBuffer(kv.val()).size()+" "+Bytes.wrap(Bytes.wrapByteBuffer(kv.val())).toHexString());

                ECKeyPair ecKeyPair = new ECKeyPair(null,
                        new BigInteger(1, java.util.Arrays.copyOfRange(Bytes.wrapByteBuffer(kv.val()).toArray(), 1, Bytes.wrapByteBuffer(kv.val()).size())));

                if (i == 0) {
                    i++;
                    System.out.println("key:"+ecKeyPair.getPublicKey().toString(16));
                    keyhash = Bytes32.wrap(Bytes.wrapByteBuffer(kv.key())).copy();
                    snapshotStore.savePubKey(Bytes32.wrap(Bytes.wrapByteBuffer(kv.key())).toArray(),ecKeyPair.getPublicKey().toByteArray());
                }

            }
            ci.close();
        }

        System.out.println("key hash:"+keyhash);
        assert keyhash != null;
        System.out.println("key pubkey:"+snapshotStore.getPubKey(keyhash.toArray()).getPublicKey().toString(16));

        txn.close();
        env.close();
    }

    // You've finished! There are lots of other neat things we could show you (eg
    // how to speed up inserts by appending them in key order, using integer
    // or reverse ordered keys, using Env.DISABLE_CHECKS_PROP etc), but you now
    // know enough to tackle the JavaDocs with confidence. Have fun!
    private Env<ByteBuffer> createSimpleEnv(final File path) {
        return create()
                .setMapSize(10_485_760)
                .setMaxDbs(1)
                .setMaxReaders(1)
                .open(path);
    }

    @Test
    public void testRead() {
        File file = new File("/Users/punk/Documents/code/java_project/xdagJ_net/snapshot/xdagj/src/test/resources/");
        PrefixFileFilter extFilter = new PrefixFileFilter("pubkey");
        SuffixFileFilter filter = new SuffixFileFilter("html");
        Collection filesListUtil = FileUtils.listFiles(file, FileFilterUtils.and(extFilter,filter),null);
        System.out.println(filesListUtil.size());
        System.out.println(FileUtils.listFiles(file, FileFilterUtils.and(extFilter,filter),null).size());
    }


}
