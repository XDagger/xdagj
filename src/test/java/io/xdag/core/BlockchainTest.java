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
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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

    public static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    Config config = new Config();
    Wallet xdagWallet;
    Kernel kernel;
    DatabaseFactory dbFactory;

    @Before
    public void setUp() throws Exception {
        config.setStoreDir(root.newFolder().getAbsolutePath());
        config.setStoreBackupDir(root.newFolder().getAbsolutePath());

        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        xdagWallet = new WalletImpl();
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

    @Test
    public void testAddressBlock() {
        ECKey key = new ECKey();
        Block addressBlock = generateAddressBlock(key, new Date().getTime());
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        assertTrue(result == IMPORTED_BEST);
        XdagStats stats = blockchain.getXdagStats();
        assertNotNull(stats);
        assertArrayEquals(addressBlock.getHashLow(), stats.getTop());
        Block storedBlock = blockchain.getBlockByHash(stats.getTop(), false);
        assertNotNull(storedBlock);
        assertArrayEquals(addressBlock.getHashLow(), storedBlock.getHashLow());
    }

    @Test
    public void testExtraBlock() throws ParseException, InterruptedException {
        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        ECKey key = new ECKey();
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        XdagStats stats = blockchain.getXdagStats();
        assertNotNull(stats);
        List<Address> pending = Lists.newArrayList();

        ImportResult result = INVALID_BLOCK;
        log.debug("1. create 1 address block");
        Block addressBlock = generateAddressBlock(key, date.getTime());

        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertChainStatus(1, 0, 0,1, blockchain);
        assertTrue(result == IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow(), stats.getTop());
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
            System.out.println(Hex.toHexString(sb.getHashLow()) + ": " + String.valueOf(amount2xdag(sb.getInfo().getAmount())));
            assertEquals("1024.0", String.valueOf(amount2xdag(sb.getInfo().getAmount())));
        });
    }

    @Test
    public void testTransactionBlock() throws ParseException {
        ECKey addrKey = new ECKey();
        ECKey poolKey = new ECKey();
        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        Block addressBlock = generateAddressBlock(addrKey, date.getTime());
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        ImportResult result = blockchain.tryToConnect(addressBlock);
        assertTrue(result == IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        byte[] ref = addressBlock.getHashLow();
        for(int i = 1; i <= 10; i++) {
            date = DateUtils.addSeconds(date, 64);
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(date.getTime());
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertTrue(result == IMPORTED_BEST);
            assertChainStatus(i+1, i-1, 1, i<2?1:0, blockchain);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        Address from  = new Address(extraBlockList.get(0).getHashLow(), XDAG_FIELD_IN);
        Address to = new Address(addressBlock.getHashLow(), XDAG_FIELD_OUT);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(date.getTime()));
        Block txBlock = generateTransactionBlock(poolKey, xdagTime - 1, from, to, xdag2amount(100.00));
        assertTrue(blockchain.canUseInput(txBlock));
        result = blockchain.tryToConnect(txBlock);
        assertTrue(result == IMPORTED_NOT_BEST);
        assertChainStatus(12, 10, 1,1, blockchain);

        pending.clear();
        pending.add(new Address(txBlock.getHashLow()));
        ref = extraBlockList.get(extraBlockList.size()-1).getHashLow();
        for(int i = 1; i <= 3; i++) {
            date = DateUtils.addSeconds(date, 64);
            pending.add(new Address(ref, XDAG_FIELD_OUT));
            long time = XdagTime.msToXdagtimestamp(date.getTime());
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(poolKey, xdagTime, pending);
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
        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        ECKey fromKey = new ECKey();
        ECKey toKey = new ECKey();
        Block fromAddrBlock = generateAddressBlock(fromKey, date.getTime());
        Block toAddrBlock = generateAddressBlock(toKey, date.getTime());

        Address from = new Address(fromAddrBlock.getHashLow(), XDAG_FIELD_IN);
        Address to = new Address(toAddrBlock);

        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        blockchain.tryToConnect(fromAddrBlock);
        blockchain.tryToConnect(toAddrBlock);

        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(date.getTime()));
        Block txBlock = generateTransactionBlock(fromKey, xdagTime - 1, from, to, xdag2amount(100.00));
        assertTrue(blockchain.canUseInput(txBlock));
    }

    //@Test
    public void Testblockload() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        loadBlockchain(config.getOriginStoreDir(), 1563368095744L, 1649267441664L, blockchain);
        printBlockchainInfo(blockchain);

        System.out.println("Balance:" + amount2xdag(kernel.getBlockStore().getXdagStatus().getBalance()));

        System.out.println("========Minedblocks========");
        List<Block> blocks = blockchain.listMinedBlocks(20);
        // List<Block> mainblocks = blockchain.listMainBlocks(20);
        System.out.println(blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            System.out.println(Hex.toHexString(blocks.get(i).getHashLow()));
        }

        System.out.println("========Xfer========");

//        Map<Address, ECKey> pairs = kernel.getAccountStore().getAccountListByAmount(xdag2amount(100));
//
//        for (Address input : pairs.keySet()) {
//            System.out.println("Input:" + input.getType());
//            System.out.println("Input:" + input.getAmount());
//            System.out.println("Input:" + Hex.toHexString(input.getHashLow()));
//            System.out.println("Input data:" + Hex.toHexString(input.getData()));
//        }
//
//        byte[] to = Hex.decode("0000000000000000a968f33f0396f13cfd95171dd83866a321aa466e5f2042bc");
//        List<Address> tos = new ArrayList<>();
//        tos.add(new Address(to, XDAG_FIELD_OUT, 100));
//        Block transaction = blockchain.createNewBlock(pairs, tos, false);
//        for (ECKey ecKey : pairs.values()) {
//            if (ecKey.equals(kernel.getWallet().getDefKey().ecKey)) {
//                transaction.signOut(ecKey);
//            } else {
//                transaction.signIn(ecKey);
//            }
//        }
//        System.out.println("Transaction hash:" + Hex.toHexString(transaction.getHashLow()));
//        System.out.println("Transaction data:" + Hex.toHexString(transaction.getXdagBlock().getData()));
    }

    //@Test
    public void TestLoadBlocksByTime() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        loadBlockchain(config.getOriginStoreDir(), 1563368095744L, 1627725496320L, blockchain);
        printBlockchainInfo(blockchain);
        System.out.println(
                "=====================================Load Blocks from blockchain========================================");

        // List<Block> blocks =
        // blockchain.getBlockByTime(1614907703296L,1627792605183L);
        // System.out.println("=====================================Block
        // size:"+blocks.size()+"========================================");

    }

    public void printBlockchainInfo(BlockchainImpl blockchain) {
        System.out.println("=====================================Blockchain Info========================================");
        System.out.println("blocks:" + blockchain.getXdagStats().nblocks);
        System.out.println("main blocks:" + blockchain.getXdagStats().nmain);
        System.out.println("extra blocks:" + blockchain.getXdagStats().nextra);
        System.out.println("orphan blocks:" + blockchain.getXdagStats().nnoref);
        System.out.println("chain difficulty:" + blockchain.getXdagStats().getTopDiff().toString(16));
        System.out.println("XDAG supply:" + blockchain.getXdagStats().nmain * 1024);
        if (blockchain.getXdagStats().nnoref > 0) {
            for (int i = 0; i < blockchain.getXdagStats().nnoref; i++) {
                System.out.println(
                        "orphan block:"
                                + Hex.toHexString(
                                        blockchain
                                                .getBlockFromOrphanPool((int) blockchain.getXdagStats().nnoref)
                                                .get(i)
                                                .getHashLow()));
            }
        }
    }

    // 01780000 0179ffff
    public long loadBlockchain(
            String srcFilePath, long starttime, long endtime, BlockchainImpl blockchain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        StringBuffer file = new StringBuffer(srcFilePath);
        FileInputStream inputStream = null;
        FileChannel channel = null;
        starttime |= 0x00000;
        endtime |= 0xffff;
        File fileImpl = null;
        long res = 0;

        while (starttime < endtime) {
            List<String> filename = getFileName(starttime);
            String blockfile = Hex.toHexString(BytesUtils.byteToBytes((byte) ((starttime >> 16) & 0xff), true));
            file.append(filename.get(filename.size() - 1)).append(blockfile).append(".dat");
            fileImpl = new File(file.toString());
            if (!fileImpl.exists()) {
                starttime += 0x10000;
                file = new StringBuffer(srcFilePath);
                continue;
            }
            System.out.println("Block from:" + file.toString());
            try {

                inputStream = new FileInputStream(fileImpl);
                channel = inputStream.getChannel();
                while (true) {
                    int eof = channel.read(buffer);
                    if (eof == -1) {
                        break;
                    }
                    buffer.flip();
                    res++;
                    if (blockchain != null) {
                        Block block = new Block(new XdagBlock(buffer.array().clone()));
                        blockchain.tryToConnect(block);
                    }
                    buffer.clear();
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            starttime += 0x10000;
            file = new StringBuffer(srcFilePath);
        }

        return res;
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
//        System.out.println(xdag2amount(10.99));
        assertEquals(47201690584L, xdag2amount(10.99));
        assertEquals(4398046511104L, xdag2amount(1024));
        assertEquals((double)10.99, amount2xdag(xdag2amount(10.99)), 0);
        assertEquals((double)1024.0, amount2xdag(xdag2amount(1024)), 0);
        assertEquals((double)0.93, amount2xdag(xdag2amount(0.93)), 0);

        // this maybe issue
        System.out.println(amount2xdag(4000000001L));

        System.out.println(xdag2amount(500.2));
        System.out.println(xdag2amount(1024 - 500.2));
        System.out.println(amount2xdag(xdag2amount(1024 - 500.2) + xdag2amount(500.2)));
        System.out.println(
                xdag2amount(1024 - 500.2 - 234.4 - 312.2)
                        + xdag2amount(500.2)
                        + xdag2amount(234.4)
                        + xdag2amount(312.2));
        System.out.println(xdag2amount(1024));

        System.out.println(
                amount2xdag(
                        xdag2amount(
                                1024 - 500.2 - 234.4 - 312.2 - 10.3 - 1.1 - 2.2 - 3.3 - 2.2 - 4.4 - 10.3 - 1.1
                                        - 2.2 - 3.3 - 2.2 - 4.4)
                                + xdag2amount(500.2)
                                + xdag2amount(234.4)
                                + xdag2amount(312.2)
                                + xdag2amount(10.3)
                                + xdag2amount(1.1)
                                + xdag2amount(2.2)
                                + xdag2amount(3.3)
                                + xdag2amount(2.2)
                                + xdag2amount(4.4)
                                + xdag2amount(10.3)
                                + xdag2amount(1.1)
                                + xdag2amount(2.2)
                                + xdag2amount(3.3)
                                + xdag2amount(2.2)
                                + xdag2amount(4.4)));
        System.out.println(amount2xdag(xdag2amount(1024)));
    }

    @Test
    public void testGetStartAmount() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        assertEquals(String.valueOf(amount2xdag(blockchain.getStartAmount(1L))), "1024.0");
        assertEquals(String.valueOf(amount2xdag(blockchain.getStartAmount(Constants.MAIN_APOLLO_TESTNET_HEIGHT))), "128.0");
    }

    @Test
    public void testGetSupply() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        assertEquals("1024.0", String.valueOf(amount2xdag(blockchain.getSupply(1))));
        assertEquals("2048.0", String.valueOf(amount2xdag(blockchain.getSupply(2))));
        assertEquals("3072.0", String.valueOf(amount2xdag(blockchain.getSupply(3))));
        long apolloSypply = blockchain.getSupply(Constants.MAIN_APOLLO_TESTNET_HEIGHT);
        assertEquals(String.valueOf(Constants.MAIN_APOLLO_TESTNET_HEIGHT * 1024 - (1024-128)), BasicUtils.formatDouble(amount2xdag(apolloSypply)));
    }

}