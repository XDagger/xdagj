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
package io.xdag.db;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.RandomXConstants;
import io.xdag.core.*;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.crypto.jni.Native;
import io.xdag.db.*;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.mine.randomx.RandomX;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.KeyPair;

import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.db.BlockStore.HASH_BLOCK_INFO;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.*;
import static org.junit.Assert.assertArrayEquals;

@Slf4j
public class SnapshotJTest {

    @Rule
    public TemporaryFolder root1 = new TemporaryFolder();

    @Rule
    public TemporaryFolder root2 = new TemporaryFolder();

    Config config = new DevnetConfig();
    Config snapshotConfig = new DevnetConfig();
    Config dataConfig = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);

    KeyPair poolKey;

    long height;
    MutableBytes32 address1;
    MutableBytes32 address2;
    MutableBytes32 address3;
    MutableBytes32 address4;
    List<Block> extraBlockList = Lists.newLinkedList();
    SnapshotJ snapshotSource;
    File backup;

    XdagTopStatus topStatus;
    XdagStats stats;

    @Before
    public void setUp() throws Exception {
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 16;
        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 32;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 1;

        config.getNodeSpec().setStoreDir(root1.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root1.newFolder().getAbsolutePath());

        snapshotConfig.getNodeSpec().setStoreDir(root2.newFolder().getAbsolutePath());
        snapshotConfig.getNodeSpec().setStoreBackupDir(root2.newFolder().getAbsolutePath());

        Native.init(config);
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));

        blockStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        snapshotSource = new SnapshotJ("SNAPSHOTJ");
        snapshotSource.setConfig(snapshotConfig);
        snapshotSource.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(wallet);

        RandomX randomX = new RandomX(config);
        kernel.setRandomx(randomX);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        kernel.setBlockchain(blockchain);
        randomX.init();

        backup = root2.newFolder();
        createBlockchain();
        FileUtils.copyDirectory(new File(config.getNodeSpec().getStoreDir()),backup);
    }

    @Test
    public void testMakeSnapshot() throws Exception {
        makeSnapshot();
        BlockInfo blockInfo1 = (BlockInfo) SnapshotJ.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address1.toArray())), BlockInfo.class);
        BlockInfo blockInfo2 = (BlockInfo) SnapshotJ.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address2.toArray())), BlockInfo.class);

        BlockInfo blockInfo3 = (BlockInfo) SnapshotJ.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address3.toArray())), BlockInfo.class);
        BlockInfo blockInfo4 = (BlockInfo) SnapshotJ.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address4.toArray())), BlockInfo.class);

        //Compare balances
        assertEquals("2048.0", String.valueOf(amount2xdag(blockInfo1.getAmount())));
        assertEquals("0.0", String.valueOf(amount2xdag(blockInfo2.getAmount())));

        //Compare public key
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        assertArrayEquals(poolKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true), blockInfo1.getSnapshotInfo().getData());
        assertArrayEquals(addrKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true), blockInfo2.getSnapshotInfo().getData());

        //Compare 512 bytes of data
        assertArrayEquals(extraBlockList.get(55).getXdagBlock().getData().toArray(), blockInfo3.getSnapshotInfo().getData());
        assertArrayEquals(extraBlockList.get(60).getXdagBlock().getData().toArray(), blockInfo4.getSnapshotInfo().getData());
    }

    @Test
    public void testSaveSnapshotToIndex() throws Exception {
        makeSnapshot();
        RocksdbFactory dbFactory = new RocksdbFactory(snapshotConfig);
        BlockStore blockStore = new BlockStore(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));
        blockStore.reset();

        List<KeyPair> keys = new ArrayList<>();
        keys.add(poolKey);

        snapshotSource.saveSnapshotToIndex(blockStore, keys);

        //Verify the total balance of the current account
        assertEquals("75776.0", String.valueOf(BasicUtils.amount2xdag(snapshotSource.getOurBalance())));
        //Verify height
        assertEquals(74,height);

        XdagStats xdagStats = new XdagStats();
        xdagStats.balance = snapshotSource.getOurBalance();
        xdagStats.setTotalnmain(height);
        xdagStats.setNmain(height);

        //Verify Stats
        assertEquals(xdagStats.balance,stats.balance);
        assertEquals(xdagStats.nmain,stats.nmain);
    }

    public void makeSnapshot() throws IOException {
        dataConfig.getNodeSpec().setStoreDir(backup.getAbsolutePath());
        dataConfig.getNodeSpec().setStoreBackupDir(root2.newFolder().getAbsolutePath());
        RocksdbKVSource blockSource = new RocksdbKVSource(DatabaseName.BLOCK.toString());
        blockSource.setConfig(dataConfig);
        blockSource.init();

        SnapshotJ index = new SnapshotJ(DatabaseName.INDEX.toString());
        index.setConfig(dataConfig);
        index.init();

        snapshotSource = new SnapshotJ("SNAPSHOTJ");
        snapshotSource.setConfig(dataConfig);
        snapshotSource.init();

        index.makeSnapshot(blockSource, snapshotSource);

        height = index.getHeight();
    }

    public void createBlockchain() {
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. add one address block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = (MockBlockchain) kernel.getBlockchain();

        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        Bytes32 ref = addressBlock.getHashLow();
        // 2. create 100 mainblocks
        for (int i = 1; i <= 25; i++) {
//            date = DateUtils.addSeconds(date, 64);
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            //assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // 3. make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        Address from = new Address(extraBlockList.get(0).getHashLow(), XDAG_FIELD_IN);
        Address to = new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateTransactionBlock(config, poolKey, xdagTime - 1, from, to, xdag2amount(100.00));

        // 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        assertTrue(blockchain.checkMineAndAdd(txBlock));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        result = blockchain.tryToConnect(txBlock);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
        // there is 12 blocks and 10 mainblocks
        //assertChainStatus(12, 10, 1, 1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow()));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 100 mainblocks
        for (int i = 1; i <= 25; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }

        Block toBlock = blockchain.getBlockStore().getBlockInfoByHash(to.getHashLow());
        Block fromBlock = blockchain.getBlockStore().getBlockInfoByHash(from.getHashLow());
        // block reword 1024 + 100 = 1124.0
        assertEquals("1124.0", String.valueOf(amount2xdag(toBlock.getInfo().getAmount())));
        // block reword 1024 - 100 = 924.0
        assertEquals("924.0", String.valueOf(amount2xdag(fromBlock.getInfo().getAmount())));

        // test two key to use
        // 4. make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        to = new Address(extraBlockList.get(0).getHashLow(), XDAG_FIELD_IN);
        from = new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT);
        xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));

        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(from.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, xdag2amount(1124.00))); // key1
        refs.add(new Address(to.getHashLow(), XDAG_FIELD_OUT, xdag2amount(1124.00)));
        List<KeyPair> keys = new ArrayList<>();
        keys.add(addrKey);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, -1); // orphan
        b.signIn(addrKey);
        b.signOut(poolKey);

        txBlock = b;
        // 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));

        result = blockchain.tryToConnect(txBlock);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
        // there is 12 blocks and 10 mainblocks
//        assertChainStatus(12, 10, 1,1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow()));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 100 mainblocks
        for (int i = 1; i <= 25; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }

        toBlock = blockchain.getBlockStore().getBlockInfoByHash(to.getHashLow());
        fromBlock = blockchain.getBlockStore().getBlockInfoByHash(from.getHashLow());
        address1 = to.getHashLow();
        address2 = from.getHashLow();
        address3 = extraBlockList.get(55).getHashLow();
        address4 = extraBlockList.get(60).getHashLow();
        assertEquals("2048.0", String.valueOf(amount2xdag(toBlock.getInfo().getAmount())));
        assertEquals("0.0", String.valueOf(amount2xdag(fromBlock.getInfo().getAmount())));
        topStatus =blockchain.getXdagTopStatus();
        stats = blockchain.getXdagStats();
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
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
