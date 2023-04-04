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

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.db.rocksdb.BlockStoreImpl.HASH_BLOCK_INFO;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.RandomXConstants;
import io.xdag.core.*;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.db.rocksdb.AddressStoreImpl;
import io.xdag.db.rocksdb.BlockStoreImpl;
import io.xdag.db.rocksdb.DatabaseFactory;
import io.xdag.db.rocksdb.DatabaseName;
import io.xdag.db.rocksdb.OrphanBlockStoreImpl;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.db.rocksdb.SnapshotStoreImpl;
import io.xdag.mine.randomx.RandomX;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.Wallet;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Slf4j
public class SnapshotStoreTest {

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

    List<Block> extraBlockList = Lists.newLinkedList();
    SnapshotStoreImpl snapshotStore;

    RocksdbKVSource snapshotSource;

    File backup;

    XdagTopStatus topStatus;
    XdagStats stats;

    @Before
    public void setUp() throws Exception {
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_BLOCKS = 64;
        RandomXConstants.RANDOMX_TESTNET_FORK_HEIGHT = 128;
        RandomXConstants.SEEDHASH_EPOCH_TESTNET_LAG = 4;

        config.getNodeSpec().setStoreDir(root1.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root1.newFolder().getAbsolutePath());

        snapshotConfig.getNodeSpec().setStoreDir(root2.newFolder().getAbsolutePath());
        snapshotConfig.getNodeSpec().setStoreBackupDir(root2.newFolder().getAbsolutePath());

        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));
        blockStore.reset();

        AddressStore addressStore = new AddressStoreImpl(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.reset();

        OrphanBlockStore orphanBlockStore = new OrphanBlockStoreImpl(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanBlockStore.reset();

        snapshotSource =  (RocksdbKVSource)dbFactory.getDB(DatabaseName.SNAPSHOT);
        snapshotStore = new SnapshotStoreImpl(snapshotSource);
        snapshotStore.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanBlockStore(orphanBlockStore);
        kernel.setAddressStore(addressStore);
        kernel.setWallet(wallet);

        RandomX nativeRandomX = new RandomX(config);
        kernel.setRandomx(nativeRandomX);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        kernel.setBlockchain(blockchain);
        nativeRandomX.init();

        backup = root2.newFolder();
        createBlockchain();
        FileUtils.copyDirectory(new File(config.getNodeSpec().getStoreDir()),backup);
    }

    @Test
    public void testMakeSnapshot() throws Exception {
        makeSnapshot();

        BlockInfo blockInfo1 = (BlockInfo) snapshotStore.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address1.toArray())), BlockInfo.class);
        BlockInfo blockInfo2 = (BlockInfo) snapshotStore.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address2.toArray())), BlockInfo.class);
        BlockInfo blockInfo3 = (BlockInfo) snapshotStore.deserialize(
                snapshotSource.get(BytesUtils.merge(HASH_BLOCK_INFO, address3.toArray())), BlockInfo.class);

        //Compare balances
        assertEquals("924.0", String.valueOf(amount2xdag(blockInfo1.getAmount())));
        assertEquals("1024.0", String.valueOf(amount2xdag(blockInfo2.getAmount())));
        assertEquals("1024.0", String.valueOf(amount2xdag(blockInfo3.getAmount())));

        //Compare public key
//        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        assertArrayEquals(poolKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true), blockInfo1.getSnapshotInfo().getData());

        //Compare 512 bytes of data
        assertArrayEquals(extraBlockList.get(11).getXdagBlock().getData().toArray(), blockInfo2.getSnapshotInfo().getData());
        assertArrayEquals(extraBlockList.get(23).getXdagBlock().getData().toArray(), blockInfo3.getSnapshotInfo().getData());
    }

    @Test
    public void testSaveSnapshotToIndex() throws Exception {
        makeSnapshot();
        RocksdbFactory dbFactory = new RocksdbFactory(snapshotConfig);
        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));
        blockStore.reset();
        AddressStore addressStore = new AddressStoreImpl(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.reset();

        List<KeyPair> keys = Lists.newArrayList();
        keys.add(poolKey);

        snapshotStore.saveSnapshotToIndex(blockStore, keys,0);
//        snapshotStore.saveAddress(blockStore, addressStore, keys,0);

        //Verify the total balance of the current account
        assertEquals("45980.0", String.valueOf(BasicUtils.amount2xdag(snapshotStore.getAllBalance())));
        //Verify height
        assertEquals(45, height);

        XdagStats xdagStats = new XdagStats();
        xdagStats.balance = UInt64.valueOf(snapshotStore.getOurBalance());
        xdagStats.setTotalnmain(height);
        xdagStats.setNmain(height);

        //Verify Stats
//        assertEquals(xdagStats.balance, stats.balance);
        assertEquals(xdagStats.nmain, stats.nmain);
    }

    public void makeSnapshot() throws IOException {
        dataConfig.getNodeSpec().setStoreDir(backup.getAbsolutePath());
        dataConfig.getNodeSpec().setStoreBackupDir(root2.newFolder().getAbsolutePath());
        RocksdbKVSource blockSource = new RocksdbKVSource(DatabaseName.BLOCK.toString());
        blockSource.setConfig(dataConfig);
        blockSource.init();

        snapshotSource = new RocksdbKVSource(DatabaseName.SNAPSHOT.toString());
        snapshotSource.setConfig(dataConfig);
        snapshotStore = new SnapshotStoreImpl(snapshotSource);
        snapshotStore.init();

        RocksdbKVSource indexSource = new RocksdbKVSource(DatabaseName.INDEX.toString());
        indexSource.setConfig(dataConfig);
        indexSource.init();

        snapshotStore.makeSnapshot(blockSource, indexSource,false);
        height = snapshotStore.getHeight();
    }

    public void createBlockchain() {
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);

        //Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;

        MockBlockchain blockchain = (MockBlockchain) kernel.getBlockchain();
        List<Address> pending = Lists.newArrayList();
        Bytes32 ref;
        ImportResult result;

        // 1. create 30 mainblocks
        for (int i = 0; i < 30; i++) {
            generateTime += 64000L;
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(IMPORTED_BEST, result);
            ref = extraBlock.getHashLow();
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            extraBlockList.add(extraBlock);
        }

        // 2. make one transaction(100 XDAG) block(from No.1 mainblock to one address)
        Address from = new Address(extraBlockList.get(0).getHashLow(), XDAG_FIELD_IN,false);
        Address to = new Address(BasicUtils.keyPair2Hash(addrKey), XDAG_FIELD_OUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateOldTransactionBlock(config, poolKey, xdagTime - 1, from, to, xdag2amount(100));

        // 3. local check
        assertTrue(blockchain.canUseInput(txBlock));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        // 4. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        result = blockchain.tryToConnect(txBlock);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow(),false));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();

        // 5. confirm transaction block with 16 mainblocks
        for (int i = 0; i <= 16; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(IMPORTED_BEST, result);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }

        UInt64 toBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        Block fromBlock = blockchain.getBlockStore().getBlockInfoByHash(from.getAddress());

        assertEquals("100.0", String.valueOf(amount2xdag(toBalance)));
        // block reword 1024 - 100 = 924.0
        assertEquals("924.0", String.valueOf(amount2xdag(fromBlock.getInfo().getAmount())));


        address1 = from.getAddress();
        address2 = extraBlockList.get(11).getHashLow();
        address3 = extraBlockList.get(23).getHashLow();

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
        public void startCheckMain(long period) {

        }

    }
}
