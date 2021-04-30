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

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.OldWallet;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.text.ParseException;
import java.util.*;

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.*;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.*;

@Slf4j
public class BlockchainTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    OldWallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;

    @Before
    public void setUp() throws Exception {
        config.getNodeSpec().setStoreDir(root.newFolder().getAbsolutePath());
        config.getNodeSpec().setStoreBackupDir(root.newFolder().getAbsolutePath());

        Native.init(config);
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

    @After
    public void tearDown() throws Exception {
    }

    private static void assertChainStatus(long nblocks, long nmain, long nextra, long norphan, BlockchainImpl bci) {
        assertEquals("blocks:", nblocks, bci.getXdagStats().nblocks);
        assertEquals("main:", nmain, bci.getXdagStats().nmain);
        assertEquals("nextra:", nextra, bci.getXdagStats().nextra);
        assertEquals("orphan:", norphan, bci.getXdagStats().nnoref);
    }


    class MockBlockchain extends BlockchainImpl {

        public MockBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain() {

        }
    }

    @Test
    public void testAddressBlock() {
        ECKeyPair key = Keys.createEcKeyPair();
        Block addressBlock = generateAddressBlock(config, key, new Date().getTime());
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        assertTrue(result == IMPORTED_BEST);
        XdagTopStatus stats = blockchain.getXdagTopStatus();
        assertNotNull(stats);
        assertArrayEquals(addressBlock.getHashLow(), stats.getTop());
        Block storedBlock = blockchain.getBlockByHash(stats.getTop(), false);
        assertNotNull(storedBlock);
        assertArrayEquals(addressBlock.getHashLow(), storedBlock.getHashLow());
    }

    @Test
    public void testExtraBlock() throws ParseException {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        ECKeyPair key = Keys.createEcKeyPair();
        MockBlockchain blockchain = new MockBlockchain(kernel);
        XdagTopStatus stats = blockchain.getXdagTopStatus();
        assertNotNull(stats);
        List<Address> pending = Lists.newArrayList();

        ImportResult result = INVALID_BLOCK;
        log.debug("1. create 1 address block");
        Block addressBlock = generateAddressBlock(config, key, generateTime);

        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertChainStatus(1, 0, 0,1, blockchain);
        assertTrue(result == IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow(), stats.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();
        // 2. create 100 mainblocks
        for(int i = 1; i <= 100; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            assertChainStatus(i+1, i>1?i-1:0, 1, i<2?1:0, blockchain);
            assertArrayEquals(extraBlock.getHashLow(), stats.getTop());
            Block storedExtraBlock = blockchain.getBlockByHash(stats.getTop(), false);
            assertArrayEquals(extraBlock.getHashLow(), storedExtraBlock.getHashLow());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // skip first 2 extra block amount assert
        Lists.reverse(extraBlockList).stream().skip(2).forEach(b->{
            Block sb = blockchain.getBlockByHash(b.getHashLow(), false);
//            System.out.println(Hex.toHexString(sb.getHashLow()) + ": " + String.valueOf(amount2xdag(sb.getInfo().getAmount())));
            assertEquals("1024.0", String.valueOf(amount2xdag(sb.getInfo().getAmount())));
        });
    }

    @Test
    public void testTransactionBlock() throws ParseException {
        ECKeyPair addrKey = Keys.createEcKeyPair();
        ECKeyPair poolKey = Keys.createEcKeyPair();
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. add one address block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertTrue(result == IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();
        // 2. create 10 mainblocks
        for(int i = 1; i <= 10; i++) {
//            date = DateUtils.addSeconds(date, 64);
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            assertChainStatus(i+1, i-1, 1, i<2?1:0, blockchain);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // 3. make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        Address from  = new Address(extraBlockList.get(0).getHashLow(), XDAG_FIELD_IN);
        Address to = new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateTransactionBlock(config, poolKey, xdagTime - 1, from, to, xdag2amount(100.00));

        // 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));


        result = blockchain.tryToConnect(txBlock);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
        // there is 12 blocks and 10 mainblocks
        assertChainStatus(12, 10, 1,1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow()));
        ref = extraBlockList.get(extraBlockList.size()-1).getHashLow();
        // 4. confirm transaction block with 3 mainblocks
        for(int i = 1; i <= 3; i++) {
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
    }

    @Test
    public void testCanUseInput() throws ParseException {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        ECKeyPair fromKey = Keys.createEcKeyPair();
        ECKeyPair toKey = Keys.createEcKeyPair();
        Block fromAddrBlock = generateAddressBlock(config, fromKey, generateTime);
        Block toAddrBlock = generateAddressBlock(config, toKey, generateTime);

        Address from = new Address(fromAddrBlock.getHashLow(), XDAG_FIELD_IN);
        Address to = new Address(toAddrBlock);

        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.tryToConnect(fromAddrBlock);
        blockchain.tryToConnect(toAddrBlock);

        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateTransactionBlock(config, fromKey, xdagTime - 1, from, to, xdag2amount(100.00));

        // 1. local check
        assertTrue(blockchain.canUseInput(txBlock));
        // 2. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
    }

    public List<String> getFileName(long time) {
        List<String> file = new ArrayList<>();
        file.add("");
        StringBuffer stringBuffer = new StringBuffer(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        stringBuffer.append(
                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
        stringBuffer.append("/");
        file.add(String.valueOf(stringBuffer));
        return file;
    }

    @Test
    public void testXdagAmount() {
        assertEquals(47201690584L, xdag2amount(10.99));
        assertEquals(4398046511104L, xdag2amount(1024));
        assertEquals((double)10.99, amount2xdag(xdag2amount(10.99)), 0);
        assertEquals((double)1024.0, amount2xdag(xdag2amount(1024)), 0);
        assertEquals((double)0.93, amount2xdag(xdag2amount(0.93)), 0);

        // this maybe issue
//        System.out.println(amount2xdag(4000000001L));

//        System.out.println(xdag2amount(500.2));
//        System.out.println(xdag2amount(1024 - 500.2));
//        System.out.println(amount2xdag(xdag2amount(1024 - 500.2) + xdag2amount(500.2)));
//        System.out.println(
//                xdag2amount(1024 - 500.2 - 234.4 - 312.2)
//                        + xdag2amount(500.2)
//                        + xdag2amount(234.4)
//                        + xdag2amount(312.2));
//        System.out.println(xdag2amount(1024));
//
//        System.out.println(
//                amount2xdag(
//                        xdag2amount(
//                                1024 - 500.2 - 234.4 - 312.2 - 10.3 - 1.1 - 2.2 - 3.3 - 2.2 - 4.4 - 10.3 - 1.1
//                                        - 2.2 - 3.3 - 2.2 - 4.4)
//                                + xdag2amount(500.2)
//                                + xdag2amount(234.4)
//                                + xdag2amount(312.2)
//                                + xdag2amount(10.3)
//                                + xdag2amount(1.1)
//                                + xdag2amount(2.2)
//                                + xdag2amount(3.3)
//                                + xdag2amount(2.2)
//                                + xdag2amount(4.4)
//                                + xdag2amount(10.3)
//                                + xdag2amount(1.1)
//                                + xdag2amount(2.2)
//                                + xdag2amount(3.3)
//                                + xdag2amount(2.2)
//                                + xdag2amount(4.4)));
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
        assertEquals(String.valueOf(config.getApolloForkHeight() * 1024 - (1024-128)), BasicUtils.formatDouble(amount2xdag(apolloSypply)));
    }

    @Test
    public void testOriginFork() throws ParseException {
        String privString = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
        BigInteger privateKey = new BigInteger(privString, 16);


        String firstDiff = "50372dcc7b";
        String secondDiff = "7fd767e345";

        ECKeyPair addrKey = ECKeyPair.create(privateKey);
        ECKeyPair poolKey = ECKeyPair.create(privateKey);
        long generateTime = 1600616700000L;
        // 1. add one address block
        Block addressBlock = generateAddressBlock(config, addrKey,generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertTrue(result == IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();

        byte[] unwindRef = null;
        long unwindDate = 0;
        // 2. create 20 mainblocks
        for(int i = 1; i <= 20; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            assertChainStatus(i+1, i-1, 1, i<2?1:0, blockchain);
            ref = extraBlock.getHashLow();
            if (i == 16) {
                unwindRef = ref.clone();
                unwindDate = generateTime;
            }
            extraBlockList.add(extraBlock);
        }



        assertEquals(firstDiff,blockchain.getXdagTopStatus().getTopDiff().toString(16));


        generateTime = unwindDate;
        ref = unwindRef;

        // 3. create 20 fork blocks
        for (int i = 0; i < 20; i++ ) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlockGivenRandom(config, poolKey, xdagTime, pending,"3456");
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        assertEquals(secondDiff, blockchain.getXdagTopStatus().getTopDiff().toString(16));


    }



}
