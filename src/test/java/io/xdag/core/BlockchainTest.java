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
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.net.message.NetStatus;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
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

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static org.junit.Assert.assertEquals;

@Slf4j
public class BlockchainTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

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
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getSumsDB());

        blockStore.reset();
        AccountStore accountStore = new AccountStore(xdagWallet, blockStore, dbFactory.getDB(DatabaseName.ACCOUNT));
        accountStore.reset();
        OrphanPool orphanPool = new OrphanPool(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanPool.reset();

        kernel.setBlockStore(blockStore);
        kernel.setAccountStore(accountStore);
        kernel.setOrphanPool(orphanPool);
        kernel.setWallet(xdagWallet);
        NetStatus netStatus = new NetStatus();
        kernel.setNetStatus(netStatus);
    }

    public void assertXdagStatus(long nblocks, long nmain, long nextra, long norphan, BlockchainImpl bci) {
        assertEquals("blocks:", nblocks, bci.getNetStatus().getNblocks());
        assertEquals("main:", nmain, bci.getNetStatus().getNmain());
        assertEquals("nextra:", nextra, bci.getExtraSize());
        assertEquals("orphan:", norphan, bci.getOrphanSize());
    }

    @Test
    public void blockchainStartFirstTest() throws ParseException {
        // 0.3.1 pool first start xdag_create_block hex
        String raw031Block = "00000000000000005805000000000000ADB776977D0100000000000000000000CACFC85A0A71A9126E14F0C57E1CB4E5F84F908E6333E20A9C5C47E66E1F828F45C5E51D710B772B29BFC41BEE64C4F251209A7582C9B80C02647BD9F231CC4C0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        // 0.4.0 pool first start xdag_create_block hex
        String raw040Block = "00000000000000005805000000000000306268977D0100000000000000000000736842A30FD96D8FC6F8AA9F4DAE81DCDE5F7148A448E3F5D8D3FA87B57D5C86E37716C0F00E1C3C406174245DC8304D69E557E75F304DA8FF070B27DB66A6DE0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        byte[] minShare = new byte[32];
        ECKey pool031Key = new ECKey();
        ECKey pool040Key = new ECKey();

        List<Address> pending = Lists.newArrayList();
//        long time = 0;
//
//        XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp());
        FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
        Date firstExtraBlockTIme = fastDateFormat.parse("2020-09-20 23:00:00");
        Date date = null;

        log.debug("1. create pool_0.3.1 and pool_0.4.0 address block");
        Block pool031AddressBlock = new Block(new XdagBlock(Hex.decode(raw031Block)));
        Address pool031Address = new Address(pool031AddressBlock.getHashLow());
        Block pool040AddressBlock = new Block(new XdagBlock(Hex.decode(raw040Block)));
        Address pool040Address = new Address(pool040AddressBlock.getHashLow());

        log.debug("2. create pool_0.3.1 and pooL_0.4.0 first extra block");
        date = new Date(XdagTime.getEndOfEpoch(firstExtraBlockTIme.getTime()));
        date = DateUtils.addSeconds(date, 64);
        pending.clear();
        pending.add(pool040Address);
        pending.add(pool031Address);
        Block pool031ExtraBlock1 = new Block(date.getTime(), null, null, pending, false, null, -1);
        pool031ExtraBlock1.signOut(pool031Key);
        minShare = new byte[32];
        Arrays.fill(minShare, (byte)0x0);
        pool031ExtraBlock1.setNonce(minShare);

        pending.clear();
        pending.add(pool040Address);
        pending.add(pool031Address);
        Block pool040ExtraBlock1 = new Block(date.getTime(), null, null, pending, false, null, -1);
        pool040ExtraBlock1.signOut(pool040Key);
        minShare = new byte[32];
        Arrays.fill(minShare, (byte)0xf);
        pool040ExtraBlock1.setNonce(minShare);
        Address pool040ExtraBlock1Address = new Address(pool040ExtraBlock1.getHashLow());

        log.debug("3. create pool_0.4.0 second extra block");
        date = new Date(XdagTime.getEndOfEpoch(date.getTime()));
        date = DateUtils.addSeconds(date, 64);
        pending.clear();
        pending.add(pool040ExtraBlock1Address);
        Block pool040ExtraBlock2 = new Block(date.getTime(), null, null, pending, false, null, -1);
        pool040ExtraBlock2.signOut(pool040Key);
        minShare = new byte[32];
        Arrays.fill(minShare, (byte)0xf);
        pool040ExtraBlock2.setNonce(minShare);

        log.debug("4. pool_0.4.0 create transaction block (from 'pool040' to 'pool031' 10 XDAG)");
        date = new Date(XdagTime.getEndOfEpoch(date.getTime()));
        date = DateUtils.addSeconds(date, 64 + 32);
        pending.clear();
        pending.add(new Address(pool040ExtraBlock1Address.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, 10)); // key1
        pending.add(new Address(pool031Address.getHashLow(), XDAG_FIELD_OUT, 10));
        List<ECKey> keys = new ArrayList<>();
        keys.add(pool040Key);
        Block txBlock1 = new Block(date.getTime(), null, pending, null, false, keys, 0); // orphan
        txBlock1.signOut(pool040Key);

        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        ImportResult result = ImportResult.IMPORTED_NOT_BEST;

        // 1. add pool031 and pool040 address block
        result = blockchain.tryToConnect(pool031AddressBlock);
        assertXdagStatus(1, 0, 0,1, blockchain);
        result = blockchain.tryToConnect(pool040AddressBlock);
        assertXdagStatus(2, 0, 0,2, blockchain);

        // 2. add pool031 and pool040 extra block1
        result = blockchain.tryToConnect(pool031ExtraBlock1);
        assertXdagStatus(3, 0, 0,1, blockchain);
        result = blockchain.tryToConnect(pool040ExtraBlock1);
        assertXdagStatus(4, 0, 0,2, blockchain);

        // 3. add pool031 and pool040 extra block2
        result = blockchain.tryToConnect(pool040ExtraBlock2);
        assertXdagStatus(5, 0, 0,2, blockchain);

        // 4. add transactionblock
        result = blockchain.tryToConnect(txBlock1);
        assertXdagStatus(6, 1, 0,3, blockchain);

        Block store031AddressBlock = blockchain.getBlockByHash(pool031AddressBlock.getHashLow(), false);
        Block store040AddressBlock = blockchain.getBlockByHash(pool040AddressBlock.getHashLow(), false);

        Block store031ExtraBlock1 = blockchain.getBlockByHash(pool031ExtraBlock1.getHashLow(), false);
        Block store040ExtraBlock1 = blockchain.getBlockByHash(pool040ExtraBlock1.getHashLow(), false);
        Block store040ExtraBlock2 = blockchain.getBlockByHash(pool040ExtraBlock2.getHashLow(), false);
        System.out.println("store031AddressBlock:" + amount2xdag(store031AddressBlock.getAmount()));
        System.out.println("store040AddressBlock:" + amount2xdag(store040AddressBlock.getAmount()));
        System.out.println("store031ExtraBlock1:" + amount2xdag(store031ExtraBlock1.getAmount()));
        System.out.println("store040ExtraBlock1:" + amount2xdag(store040ExtraBlock1.getAmount()));
        System.out.println("store040ExtraBlock2:" + amount2xdag(store040ExtraBlock2.getAmount()));
//        assertEquals("XDAG supply:", String.valueOf(1024.0), String.valueOf(amount2xdag(store040ExtraBlock1.getAmount())));
    }

    public void printHash(byte[] hash, String prefix) {
        System.out.println(prefix + Hex.toHexString(hash));
    }

    //@Test
    public void Testblockload() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
        loadBlockchain(config.getOriginStoreDir(), 1563368095744L, 1649267441664L, blockchain);
        printBlockchainInfo(blockchain);

        System.out.println("Balance:" + amount2xdag(kernel.getAccountStore().getGBalance()));

        System.out.println("========Minedblocks========");
        List<Block> blocks = blockchain.listMinedBlocks(20);
        // List<Block> mainblocks = blockchain.listMainBlocks(20);
        System.out.println(blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            System.out.println(Hex.toHexString(blocks.get(i).getHashLow()));
        }

        System.out.println("========Xfer========");

        Map<Address, ECKey> pairs = kernel.getAccountStore().getAccountListByAmount(xdag2amount(100));

        for (Address input : pairs.keySet()) {
            System.out.println("Input:" + input.getType());
            System.out.println("Input:" + input.getAmount());
            System.out.println("Input:" + Hex.toHexString(input.getHashLow()));
            System.out.println("Input data:" + Hex.toHexString(input.getData()));
        }

        byte[] to = Hex.decode("0000000000000000a968f33f0396f13cfd95171dd83866a321aa466e5f2042bc");
        List<Address> tos = new ArrayList<>();
        tos.add(new Address(to, XDAG_FIELD_OUT, 100));
        Block transaction = blockchain.createNewBlock(pairs, tos, false);
        for (ECKey ecKey : pairs.values()) {
            if (ecKey.equals(kernel.getWallet().getDefKey().ecKey)) {
                transaction.signOut(ecKey);
            } else {
                transaction.signIn(ecKey);
            }
        }
        System.out.println("Transaction hash:" + Hex.toHexString(transaction.getHashLow()));
        System.out.println("Transaction data:" + Hex.toHexString(transaction.getXdagBlock().getData()));
    }

    //@Test
    public void TestLoadBlocksByTime() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel, dbFactory);
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
        System.out.println(
                "=====================================Blockchain Info========================================");
        System.out.println("blocks:" + blockchain.getBlockSize());
        System.out.println("main blocks:" + blockchain.getMainBlockSize());
        System.out.println("extra blocks:" + blockchain.getExtraSize());
        System.out.println("orphan blocks:" + blockchain.getOrphanSize());
        System.out.println("chain difficulty:" + blockchain.getTopDiff().toString(16));
        System.out.println("XDAG supply:" + blockchain.getMainBlockSize() * 1024);
        if (blockchain.getOrphanSize() > 0) {
            for (int i = 0; i < blockchain.getOrphanSize(); i++) {
                System.out.println(
                        "orphan block:"
                                + Hex.toHexString(
                                        blockchain
                                                .getBlockFromOrphanPool((int) blockchain.getOrphanSize())
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

//    @Test
    public void testXdagAmount() {
        System.out.println(xdag2amount(10.99));
        System.out.println(xdag2amount(1024));
        System.out.println(amount2xdag(xdag2amount(10.99)));
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
}