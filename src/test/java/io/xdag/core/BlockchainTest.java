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

package io.xdag.core;

import static io.xdag.BlockBuilder.generateAddressBlock;
import static io.xdag.BlockBuilder.generateExtraBlock;
import static io.xdag.BlockBuilder.generateExtraBlockGivenRandom;
import static io.xdag.BlockBuilder.generateNewTransactionBlock;
import static io.xdag.BlockBuilder.generateOldTransactionBlock;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_INPUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.keyPair2Hash;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.crypto.jni.Native;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.OrphanPool;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.ByteArrayToByte32;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Slf4j
public class BlockchainTest {

    static { Security.addProvider(new BouncyCastleProvider());  }

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    BigInteger private_2 = new BigInteger("10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046", 16);

    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);
    SECPPrivateKey secretkey_2 = SECPPrivateKey.create(private_2, Sign.CURVE_NAME);

    private static void assertChainStatus(long nblocks, long nmain, long nextra, long norphan, BlockchainImpl bci) {
        assertEquals("blocks:", nblocks, bci.getXdagStats().nblocks);
        assertEquals("main:", nmain, bci.getXdagStats().nmain);
        assertEquals("nextra:", nextra, bci.getXdagStats().nextra);
        assertEquals("orphan:", norphan, bci.getXdagStats().nnoref);
    }

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

        AddressStore addressStore = new AddressStore(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setAddressStore(addressStore);
        kernel.setWallet(wallet);
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }


    @Test
    public void testExtraBlock() {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        KeyPair poolKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), xdag2amount(0));
        XdagTopStatus stats = blockchain.getXdagTopStatus();
        assertNotNull(stats);
        List<Address> pending = Lists.newArrayList();

        ImportResult result;
        log.debug("1. create 1 tx block");
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);


        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertChainStatus(1, 0, 0, 1, blockchain);
        assertSame(result, IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow().toArray(), stats.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();
        // 2. create 100 mainblocks
        for (int i = 1; i <= 27; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(poolKey),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            Block storedExtraBlock = blockchain.getBlockByHash(Bytes32.wrap(stats.getTop()), false);
            assertArrayEquals(extraBlock.getHashLow().toArray(), storedExtraBlock.getHashLow().toArray());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        UInt64 poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        assertEquals(10240,(long)amount2xdag(poolBalance));
    }

    @Test
    public void testNew2NewTransactionBlock() {
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
//        System.out.println(PubkeyAddressUtils.toBase58(Keys.toBytesAddress(addrKey)));
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), xdag2amount(0));
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();
        // 2. create 10 mainblocks
        for (int i = 1; i <= 17; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }
        //TODO 两种不同的交易模式的测试
        // 3. make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        Address from = new Address(ByteArrayToByte32.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
//        System.out.println(PubkeyAddressUtils.toBase58(from.getAddress().slice(8,20).toArray()));
        Address to = new Address(ByteArrayToByte32.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
//        System.out.println(PubkeyAddressUtils.toBase58(to.getAddress().slice(8,20).toArray()));
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateNewTransactionBlock(config, poolKey, xdagTime - 1, from, to, xdag2amount(100.00));

        // 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        assertTrue(blockchain.checkMineAndAdd(txBlock));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        result = blockchain.tryToConnect(txBlock);
        Block c = blockchain.getBlockByHash(txBlock.getHashLow(),true);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
        // there is 12 blocks and 10 mainblocks
        assertChainStatus(19, 17, 1, 1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow(),false));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 3 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }

        Block toBlock = blockchain.getBlockStore().getBlockInfoByHash(to.getAddress());
        Block fromBlock = blockchain.getBlockStore().getBlockInfoByHash(from.getAddress());
        UInt64 poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        UInt64 addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        assertEquals(10240,(long)amount2xdag(blockchain.getAddressStore().getAllBalance()));
        assertEquals(10140,(long)amount2xdag(poolBalance.toLong()));
        assertEquals(100,(long)amount2xdag(addressBalance.toLong()));
    }

    @Test
    public void testOld2NewTransaction(){
        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);
//        System.out.println(PubkeyAddressUtils.toBase58(Keys.toBytesAddress(addrKey)));
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();
        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }
        //TODO 两种不同的交易模式的测试
        // 3. make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        Address from = new Address(addressBlock.getHashLow(), XDAG_FIELD_IN,false);
//        System.out.println(PubkeyAddressUtils.toBase58(from.getAddress().slice(8,20).toArray()));
        Address to = new Address(ByteArrayToByte32.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
//        System.out.println(PubkeyAddressUtils.toBase58(to.getAddress().slice(8,20).toArray()));
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateOldTransactionBlock(config, poolKey, xdagTime - 1, from, to, xdag2amount(100.00));

        // 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        assertTrue(blockchain.checkMineAndAdd(txBlock));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        result = blockchain.tryToConnect(txBlock);
        Block c = blockchain.getBlockByHash(txBlock.getHashLow(),true);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
        // there is 12 blocks and 10 mainblocks
        assertChainStatus(12, 10, 1, 1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow(),false));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 3 mainblocks
        for (int i = 1; i <= 4; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }

        UInt64 poolBalance = UInt64.valueOf((long)amount2xdag(blockchain.getBlockByHash(addressBlock.getHash(),false).getInfo().getAmount()));
        UInt64 addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        assertEquals(900,poolBalance.toLong());
        assertEquals(100,(long)amount2xdag(addressBalance.toLong()));
    }

    @Test
    public void testCanUseInput() {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        KeyPair fromKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair toKey = KeyPair.create(secretkey_2, Sign.CURVE, Sign.CURVE_NAME);
        Block fromAddrBlock = generateAddressBlock(config, fromKey, generateTime);
        Block toAddrBlock = generateAddressBlock(config, toKey, generateTime);

        Address from = new Address(fromAddrBlock.getHashLow(), XDAG_FIELD_IN,true);
        Address to = new Address(toAddrBlock);

        BlockchainImpl blockchain = spy(new BlockchainImpl(kernel));

        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateOldTransactionBlock(config, fromKey, xdagTime - 1, from, to, xdag2amount(100.00));

        when(blockchain.getBlockByHash(from.getAddress(), false)).thenReturn(fromAddrBlock);
        when(blockchain.getBlockByHash(from.getAddress(), true)).thenReturn(fromAddrBlock);

        // 1. local check
        assertTrue(blockchain.canUseInput(txBlock));

        // 2. remote check
        Block block = new Block(txBlock.getXdagBlock());
        assertTrue(blockchain.canUseInput(block));
    }

    @Test
    public void testXdagAmount() {
        assertEquals(47201690584L, xdag2amount(10.99).toLong());
        assertEquals(4398046511104L, xdag2amount(1024).toLong());
        assertEquals(10.990000000224, amount2xdag(xdag2amount(10.99).toLong()), 0);
        assertEquals(10.990000000224, amount2xdag(xdag2amount(10.99)), 0);
        assertEquals(1024.0, amount2xdag(xdag2amount(1024)), 0);
        assertEquals(0.930000000168, amount2xdag(xdag2amount(0.93)), 0);
    }

    @Test
    public void testGetStartAmount() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        assertEquals(String.valueOf(amount2xdag(blockchain.getStartAmount(1L))), "1024.0");
        assertEquals(String.valueOf(amount2xdag(blockchain.getStartAmount(config.getApolloForkHeight()))), "128.0");
    }

    @Test
    public void testGetSupply() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        assertEquals("1024.0", String.valueOf(amount2xdag(blockchain.getSupply(1))));
        assertEquals("2048.0", String.valueOf(amount2xdag(blockchain.getSupply(2))));
        assertEquals("3072.0", String.valueOf(amount2xdag(blockchain.getSupply(3))));
        long apolloSypply = blockchain.getSupply(config.getApolloForkHeight());
        assertEquals(String.valueOf(config.getApolloForkHeight() * 1024 - (1024 - 128)),
                BasicUtils.formatDouble(amount2xdag(apolloSypply)));
    }

    @Test
    public void testOriginFork() {
        String firstDiff = "3f4a35eaa6";
        String secondDiff = "1a24b50c9f2";

        KeyPair addrKey = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(secretkey_2, Sign.CURVE, Sign.CURVE_NAME);
        long generateTime = 1600616700000L;
        // 1. add one address block
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        Bytes32 ref = addressBlock.getHashLow();

        Bytes32 unwindRef = Bytes32.ZERO;
        long unwindDate = 0;
        // 2. create 20 mainblocks
        for (int i = 1; i <= 20; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(poolKey),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
            if (i == 16) {
                unwindRef = ref;
                unwindDate = generateTime;
            }
        }

        assertEquals(firstDiff, blockchain.getXdagTopStatus().getTopDiff().toString(16));

        generateTime = unwindDate;
        ref = Bytes32.wrap(unwindRef);

        // 3. create 20 fork blocks
        for (int i = 0; i < 20; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,true));
            pending.add(new Address(keyPair2Hash(poolKey),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlockGivenRandom(config, poolKey, xdagTime, pending, "3456");
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
        }

        assertEquals(secondDiff, blockchain.getXdagTopStatus().getTopDiff().toString(16));
    }

    @Test
    public void testForkAllChain() {
        KeyPair poolKey = KeyPair.create(secretkey_2, Sign.CURVE, Sign.CURVE_NAME);
        long generateTime = 1600616700000L;

        // 1. add one address block
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);

        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        Bytes32 ref = addressBlock.getHashLow();

        // 2. create 20 mainblocks
        for (int i = 1; i <= 20; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(poolKey),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
        }
        Bytes32 first = blockchain.getBlockByHeight(5).getHash();

        generateTime = 1600616700001L;
        Block addressBlock1 = generateAddressBlock(config, poolKey, generateTime);
        result = blockchain.tryToConnect(addressBlock1);
        pending = Lists.newArrayList();
        ref = addressBlock1.getHashLow();

        // 3. create 30 fork blocks
        for (int i = 0; i < 40; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
//            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(poolKey),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
//            assertSame(result, IMPORTED_BEST);
//            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();
        }
        assertEquals(13, blockchain.getXdagStats().nmain);
        Bytes32 second = blockchain.getBlockByHeight(5).getHash();
        assertNotEquals(first, second);

    }

    static class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }


        @Override
        public void startCheckMain(long period) {
//            super.startCheckMain(period);
        }

        @Override
        public void addOurBlock(int keyIndex, Block block) {
            return;
        }

    }

}
