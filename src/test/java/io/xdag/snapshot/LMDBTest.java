package io.xdag.snapshot;

import static io.xdag.snapshot.config.SnapShotKeys.SNAPSHOT_KEY_STATS_MAIN;
import static io.xdag.snapshot.config.SnapShotKeys.getMutableBytesByKey;
import static io.xdag.snapshot.config.SnapShotKeys.getMutableBytesByKey_;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;
import static java.nio.ByteBuffer.allocateDirect;
import static java.util.Arrays.copyOfRange;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;
import static org.lmdbjava.Env.create;
import static org.lmdbjava.EnvFlags.MDB_FIXEDMAP;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.crypto.ECDSASignature;
import io.xdag.crypto.ECKeyPair;
import io.xdag.snapshot.core.BalanceData;
import io.xdag.snapshot.core.StatsBlock;
import io.xdag.utils.Numeric;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.xerial.snappy.Snappy;


public class LMDBTest {

    @Test
    public void testBalance() throws IOException {
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/5000");
        Env<ByteBuffer> env = create()
                .setMaxReaders(4)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_FIXEDMAP, MDB_NOSYNC);

        final Dbi<ByteBuffer> db = env.openDbi("balance", MDB_CREATE, MDB_INTEGERKEY);

        Txn<ByteBuffer> txn = env.txnRead();
        int i = 0;
        double totalBalance = 0;

        try (CursorIterable<ByteBuffer> ci = db.iterate(txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                BalanceData data = BalanceData.parse(Bytes.wrapByteBuffer(kv.key()), Bytes.wrapByteBuffer(kv.val()));
                System.out.println(data);
                assert data != null;

                totalBalance += amount2xdag(data.getAmount());
                i++;
            }
        }

        System.out.println("total address:" + i);
        System.out.println("totalbalance:" + totalBalance);

        txn.close();
        env.close();

    }

    @Test
    public void testStats() throws IOException {
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/5000");
        Env<ByteBuffer> env = create()
                .setMaxReaders(4)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_FIXEDMAP, MDB_NOSYNC);

        Dbi<ByteBuffer> db = env.openDbi("stats", MDB_CREATE);
        ByteBuffer snapshotHeightKey = allocateDirect(getMutableBytesByKey(SNAPSHOT_KEY_STATS_MAIN).size());
        snapshotHeightKey.put(getMutableBytesByKey(SNAPSHOT_KEY_STATS_MAIN).toArray()).flip();
        Txn<ByteBuffer> txn = env.txnRead();
        ByteBuffer snapshotHeight = db.get(txn, snapshotHeightKey);

        StatsBlock snapshotHeightBlock = StatsBlock
                .parse(Bytes.wrapByteBuffer(snapshotHeightKey), Bytes.wrapByteBuffer(snapshotHeight));
        System.out.println("snapshot:" + snapshotHeightBlock);

        for (int i = 1; i <= 128; i++) {
            String key = SNAPSHOT_KEY_STATS_MAIN + "_" + i;
            ByteBuffer snapshotey = allocateDirect(getMutableBytesByKey_(key).size());
            snapshotey.put(getMutableBytesByKey_(key).toArray()).flip();
            ByteBuffer snapshot = db.get(txn, snapshotey);
            StatsBlock snapshotBlock = StatsBlock
                    .parse(Bytes.wrapByteBuffer(snapshotey), Bytes.wrapByteBuffer(snapshot));
            System.out.println("snapshot " + i + ":" + snapshotBlock);
        }

        txn.close();
        env.close();

    }


    @Test
    public void testPubkey() throws IOException {
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/pubkey");
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
                ECKeyPair ecKeyPair = new ECKeyPair(null,
                        new BigInteger(1, copyOfRange(Bytes.wrapByteBuffer(kv.val()).toArray(), 1,
                                Bytes.wrapByteBuffer(kv.val()).size())));

                System.out
                        .println("account:" + hash2Address(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())));
                System.out.println("pub:" + ecKeyPair.getPublicKey().toString(16));
            }
        }
        txn.close();
        env.close();
    }

    @Test
    public void testSignature() throws IOException {
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/pubkey");
        Env<ByteBuffer> env = create()
                .setMaxReaders(1)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_NOSYNC);

        final Dbi<ByteBuffer> db = env.openDbi("signature", MDB_CREATE);

        Txn<ByteBuffer> txn = env.txnRead();

        int i = 0;
        Bytes32 keyhash = null;

        try (CursorIterable<ByteBuffer> ci = db.iterate(txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                BigInteger r;
                BigInteger s;

                r = Numeric.toBigInt(Bytes.wrapByteBuffer(kv.val()).slice(0, 32).toArray());
                s = Numeric.toBigInt(Bytes.wrapByteBuffer(kv.val()).slice(32, 32).toArray());
                ECDSASignature tmp = new ECDSASignature(r, s);

                System.out
                        .println("account:" + hash2Address(Arrays.reverse(Bytes.wrapByteBuffer(kv.key()).toArray())));
                System.out.println("signature:" + Hex.toHexString(tmp.toByteArray()));
            }
        }

        txn.close();
        env.close();
    }

    @Test
    public void testBlock() throws IOException {
        File file = new File(
                "/Users/paulochen/Documents/projects/self/xdagj/src/test/resources/pubkey");
        Env<ByteBuffer> env = create()
                .setMaxReaders(1)
                .setMapSize(268435456)
                .setMaxDbs(4)
                .open(file, MDB_NOSYNC);

        final Dbi<ByteBuffer> db = env.openDbi("block", MDB_CREATE);

        Txn<ByteBuffer> txn = env.txnRead();

        try (CursorIterable<ByteBuffer> ci = db.iterate(txn, KeyRange.all())) {

            for (final KeyVal<ByteBuffer> kv : ci) {
                assertThat(kv.key(), notNullValue());
                assertThat(kv.val(), notNullValue());
                Bytes bytes = Bytes.wrapByteBuffer(kv.val());
                byte[] uncompress = Snappy.uncompress(bytes.toArray());
                Block block = new Block(new XdagBlock(uncompress));
                System.out.println("account:" + hash2Address(block.getHash()));
                System.out.println("block:" + block);
            }
        }

        txn.close();
        env.close();
    }


}
