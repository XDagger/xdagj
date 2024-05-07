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
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.OrphanBlockStore;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.db.rocksdb.*;
import io.xdag.rpc.modules.xdag.XdagModuleTransactionBase;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.WalletUtils;
import io.xdag.utils.XdagTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.SECPSignature;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.xdag.BlockBuilder.*;
import static io.xdag.core.ImportResult.*;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.utils.BasicUtils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Slf4j
public class BlockchainTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    Config config = new DevnetConfig();
    Wallet wallet;
    String pwd;
    Kernel kernel;
    DatabaseFactory dbFactory;

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    BigInteger private_2 = new BigInteger("10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046", 16);

    SECPPrivateKey secretary_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);
    SECPPrivateKey secretary_2 = SECPPrivateKey.create(private_2, Sign.CURVE_NAME);

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

        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();

        kernel = new Kernel(config, key);
        dbFactory = new RocksdbFactory(config);

        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));

        blockStore.reset();
        OrphanBlockStore orphanBlockStore = new OrphanBlockStoreImpl(dbFactory.getDB(DatabaseName.ORPHANIND));
        orphanBlockStore.reset();

        AddressStore addressStore = new AddressStoreImpl(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.reset();

        TransactionHistoryStore txHistoryStore = Mockito.mock(TransactionHistoryStore.class);

        kernel.setBlockStore(blockStore);
        kernel.setOrphanBlockStore(orphanBlockStore);
        kernel.setAddressStore(addressStore);
        kernel.setTxHistoryStore(txHistoryStore);
        kernel.setWallet(wallet);
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
    }

    @Test
    public void TestRejectAddress() {
        String TransactionBlockRawData = "0000000000000000C19D56050000000040f0819c950100000000000000000000"
                + "0000000081fd3cb36d2e0e4862d51161a687954fb17623690000000001000000"
                + "00000000f697cfd0d0db99aa3b7cc933f78df090f4f78e4f0000000001000000"
                + "6b6b000000000000000000000000000000000000000000000000000000000000"
                + "e81c29e0e0063cf8814239c5f7434f633e7f3a4ab24e461ca2dc724e347ba9a9"
                + "9130e1cce44266f52538ffc40b927f1e73f6124158f0dafe18ed721d589e2892"
                + "3ca7c4b76474ce2b3e9c16ac9304f03bfc8ca18acbe8610140390c4eb1204f08"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000";
        Block block = new Block(new XdagBlock(Hex.decode(TransactionBlockRawData)));
        for (Address link : block.getLinks()) {
            //测试地址
            if (link.getType() == XDAG_FIELD_INPUT){assertEquals(WalletUtils.toBase58(link.getAddress().slice(8, 20).toArray()), "AavSCZUxXbySZXjXcb3mwr5CzwabQXP2A");}
            if (link.getType() == XDAG_FIELD_OUTPUT){assertEquals(WalletUtils.toBase58(link.getAddress().slice(8, 20).toArray()), "8FfenZ1xewHGa3Ydx9zhppgou1hgesX97");}
        }
        assertEquals(kernel.getConfig().getNodeSpec().getRejectAddress(), ""); //默认为空

        XdagModuleTransactionBase testRPC = new XdagModuleTransactionBase(kernel);
        assertTrue(testRPC.checkTransaction(block));
    }

    @Test
    public void testExtraBlock() {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        KeyPair key = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        XdagTopStatus stats = blockchain.getXdagTopStatus();
        assertNotNull(stats);
        List<Address> pending = Lists.newArrayList();

        ImportResult result;
        log.debug("1. create 1 tx block");
        Block addressBlock = generateAddressBlock(config, key, generateTime);

        // 1. add address block
        result = blockchain.tryToConnect(addressBlock);
        assertChainStatus(1, 0, 0, 1, blockchain);
        assertSame(result, IMPORTED_BEST);
        assertArrayEquals(addressBlock.getHashLow().toArray(), stats.getTop());
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();
        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            log.debug("create No." + i + " extra block");
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, key, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i > 1 ? i - 1 : 0, 1, i < 2 ? 1 : 0, blockchain);
            assertArrayEquals(extraBlock.getHashLow().toArray(), stats.getTop());
            Block storedExtraBlock = blockchain.getBlockByHash(Bytes32.wrap(stats.getTop()), false);
            assertArrayEquals(extraBlock.getHashLow().toArray(), storedExtraBlock.getHashLow().toArray());
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        // skip first 2 extra block amount assert
        Lists.reverse(extraBlockList).stream().skip(2).forEach(b -> {
            Block sb = blockchain.getBlockByHash(b.getHashLow(), false);
            assertEquals("1024.0", sb.getInfo().getAmount().toDecimal(1, XUnit.XDAG).toString());
        });
    }

    @Test
    public void testNew2NewTransactionBlock() {
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair addrKey1 = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), XAmount.of(1000, XUnit.XDAG));
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
        Address from = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
        Address to1 = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey1)), XDAG_FIELD_OUTPUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateNewTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(100, XUnit.XDAG));

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
        Address txAddress =  new Address(txBlock.getHashLow(), false);
        pending.add(txAddress);
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 16 mainblocks
        for (int i = 1; i <= 16; i++) {
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

        XAmount poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount mainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
        assertEquals("900.00", poolBalance.toDecimal(2, XUnit.XDAG).toString());//1000 - 100  = 900.00
        assertEquals("99.90", addressBalance.toDecimal(2, XUnit.XDAG).toString());//100 - 0.1 = 99.90
        assertEquals("1024.1" , mainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 0.1 reward.
        XAmount mainBlockFee = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(10).getHashLow()).getFee();
        assertEquals("0.1",mainBlockFee.toDecimal(1, XUnit.XDAG).toString());

        blockchain.unSetMain(extraBlockList.get(10));//test rollback

        XAmount RollBackPoolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount RollBackAddressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount RollBackMainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
        assertEquals("1000.00", RollBackPoolBalance.toDecimal(2, XUnit.XDAG).toString());//rollback 900 + 100 = 1000
        assertEquals("0.00", RollBackAddressBalance.toDecimal(2, XUnit.XDAG).toString());//rollback 99.9 -99.9 = 0
        assertEquals("0.0" , RollBackMainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock reward back 1024 - 1024 = 0.


        //TODO:test wallet create txBlock with fee = 0,
        List<Block> txList = Lists.newLinkedList();
        for (int i = 1; i <= 10; i++) {
            Block txBlock_0;
            if (i == 1){//TODO:test give miners reward with a TX block :one input several output
                 txBlock_0 = generateMinerRewardTxBlock(config, poolKey, xdagTime - i, from, to,to1, XAmount.of(20,XUnit.XDAG),XAmount.of(10,XUnit.XDAG), XAmount.of(10,XUnit.XDAG));
            }else {
                 txBlock_0 = generateWalletTransactionBlock(config, poolKey, xdagTime - i, from, to, XAmount.of(1,XUnit.XDAG));}

            assertEquals(XAmount.ZERO, txBlock_0.getFee());//fee is zero.
            // 4. local check
            assertTrue(blockchain.canUseInput(txBlock_0));
            assertTrue(blockchain.checkMineAndAdd(txBlock_0));
            // 5. remote check
            assertTrue(blockchain.canUseInput(new Block(txBlock_0.getXdagBlock())));
            assertTrue(blockchain.checkMineAndAdd(txBlock_0));

            result = blockchain.tryToConnect(txBlock_0);
            // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
            assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
            txList.add(txBlock_0);
        }
        pending.clear();
        for (Block tx : txList) {
            pending.add(new Address(tx.getHashLow(), false));
        }
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 16 mainblocks
        for (int i = 1; i <= 16; i++) {
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
        XAmount poolBalance_0 = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount addressBalance_0 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount addressBalance_1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
        XAmount mainBlockLinkTxBalance_0 = blockchain.getBlockByHash(extraBlockList.get(26).getHash(), false).getInfo().getAmount();
        assertEquals("971.00", poolBalance_0.toDecimal(2, XUnit.XDAG).toString());//1000 - 20 - 1*9  = 971.00
        assertEquals("18.00", addressBalance_0.toDecimal(2, XUnit.XDAG).toString());//0  + (10-0.1) + (1 - 0.1) * 9  = 18   (ps:0.1 is fee)
        assertEquals("9.90", addressBalance_1.toDecimal(2, XUnit.XDAG).toString());//0 + 10 - 0.1 = 9.90
        assertEquals("1025.1" , mainBlockLinkTxBalance_0.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 0.1*11 reward.
        XAmount mainBlockFee_1 = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(26).getHashLow()).getFee();
        assertEquals("1.1",mainBlockFee_1.toDecimal(1, XUnit.XDAG).toString());

        //TODO:test rollback
        blockchain.unSetMain(extraBlockList.get(26));

        XAmount RollBackPoolBalance_1 = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount RollBackAddressBalance_0 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount RollBackAddressBalance_1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
        XAmount RollBackMainBlockLinkTxBalance_1 = blockchain.getBlockByHash(extraBlockList.get(26).getHash(), false).getInfo().getAmount();
        assertEquals("1000.00", RollBackPoolBalance_1.toDecimal(2, XUnit.XDAG).toString());//1000
        assertEquals("0.00", RollBackAddressBalance_0.toDecimal(2, XUnit.XDAG).toString());//rollback is zero
        assertEquals("0.00", RollBackAddressBalance_1.toDecimal(2, XUnit.XDAG).toString());
        assertEquals("0.0" , RollBackMainBlockLinkTxBalance_1.toDecimal(1, XUnit.XDAG).toString());//  rollback is zero

    }

    @Test
    public void DuplicateLink_Rollback(){
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair addrKey1 = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), XAmount.of(1000, XUnit.XDAG));
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();
        Bytes32 ref = addressBlock.getHashLow();  //这个是链的创世区块
        // 2. create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));//ref 为创世区块
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            long xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            assertChainStatus(i + 1, i - 1, 1, i < 2 ? 1 : 0, blockchain);
            ref = extraBlock.getHashLow();   //更新ref为当前区块
            extraBlockList.add(extraBlock);
        }

        //构造一笔交易，用于被两个块连续链接
        Address from = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateNewTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(100, XUnit.XDAG));


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
        // there is 12 blocks ： 10 mainBlocks, 1 txBlock
        assertChainStatus(12, 10, 1, 1, blockchain);


        pending.clear();
        Address TxblockAddress = new Address(txBlock.getHashLow(),false);
        pending.add(TxblockAddress);
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        //高度 11 主块链接交易块，第一次
        generateTime += 64000L;
        pending.add(new Address(ref, XDAG_FIELD_OUT,false));
        pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                XdagField.FieldType.XDAG_FIELD_COINBASE,
                true));
        long time = XdagTime.msToXdagtimestamp(generateTime);
        xdagTime = XdagTime.getEndOfEpoch(time);
        Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
        blockchain.tryToConnect(extraBlock);
        extraBlockList.add(extraBlock);
        pending.clear();


        List<Address> links = extraBlockList.get(10).getLinks();
        Set<String> linkset = new HashSet<>();
        for (Address link : links){  //将主块的链接块都放进Hashset里面，用于确认链接了交易块
            linkset.add(WalletUtils.toBase58(link.getAddress().slice(8, 20).toArray()));
        }
        //确认高度 11 主块链接了 交易块
        assertTrue(linkset.contains(WalletUtils.toBase58(TxblockAddress.getAddress().slice(8, 20).toArray())));

        //为高度12的去看构造一笔属于它的交易：
        from = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
        Address to1 = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey1)), XDAG_FIELD_OUTPUT,true);
        Block txBlock1 = generateNewTransactionBlock(config, poolKey, xdagTime - 2, from, to1, XAmount.of(10, XUnit.XDAG));
        assertTrue(blockchain.canUseInput(txBlock1));
        assertTrue(blockchain.checkMineAndAdd(txBlock1));
        // 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock1.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock1));
        result = blockchain.tryToConnect(txBlock1);
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);

        //高度 12 主块再次链接交易块，第二次
        pending.add(TxblockAddress);
        pending.add(new Address(txBlock1.getHashLow(),false));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        for (int i = 1; i <= 16; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            blockchain.tryToConnect(extraBlock);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
            pending.clear();
        }
        links = extraBlockList.get(11).getLinks();
        linkset = new HashSet<>();
        for (Address link : links){  //将主块的链接块都放进Hashset里面，用于确认链接了两个交易块
            linkset.add(WalletUtils.toBase58(link.getAddress().slice(8, 20).toArray()));
        }
        //确认高度 12 主块链接了 两个交易块
        assertTrue(linkset.contains(WalletUtils.toBase58(TxblockAddress.getAddress().slice(8, 20).toArray())));
        assertTrue(linkset.contains(WalletUtils.toBase58(new Address(txBlock1.getHashLow(),false).getAddress().slice(8, 20).toArray())));
        //16个块确认后，目前高度 11+16 = 27

        //测试重复链接是否影响手续费收取
        XAmount poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount addressBalance1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
        XAmount mainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();

        assertEquals("890.00", poolBalance.toDecimal(2, XUnit.XDAG).toString());//1000 - 100 - 10 = 890.00
        assertEquals("99.90", addressBalance.toDecimal(2, XUnit.XDAG).toString());//100 - 0.1 = 99.90
        assertEquals("9.90", addressBalance1.toDecimal(2, XUnit.XDAG).toString());//10 - 0.1 = 9.90
        assertEquals("1024.1" , mainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 0.1 reward.
        XAmount mainBlockFee = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(10).getHashLow()).getFee();
        assertEquals("0.1",mainBlockFee.toDecimal(1, XUnit.XDAG).toString());

        //重复连接的第12个主块，只有属于自己交易的手续费
        XAmount mainBlock_doubleLink_Balance = blockchain.getBlockByHash(extraBlockList.get(11).getHash(), false).getInfo().getAmount();
        assertEquals("1024.1" , mainBlock_doubleLink_Balance.toDecimal(1, XUnit.XDAG).toString());//double link will not get fee
        XAmount mainBlock_doubleLink_Fee = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(11).getHashLow()).getFee();
        assertEquals("0.1",mainBlock_doubleLink_Fee.toDecimal(1, XUnit.XDAG).toString());

        //TODO:回滚重复链接的第12个主块，
        blockchain.unSetMain(extraBlockList.get(11));
        poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        assertEquals("900.00", poolBalance.toDecimal(2, XUnit.XDAG).toString());//890 + 10 = 900, 只回滚自己的交易，不回滚别的主块的交易
        assertEquals("99.90", addressBalance.toDecimal(2, XUnit.XDAG).toString());//99.90 -99.90 = 0 只回滚自己的交易，不回滚别的主块的交易
    }

    @Test
    public void testTransaction_WithVariableFee() {
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair addrKey1 = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), XAmount.of(1000, XUnit.XDAG));
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

        // 测试各种交易模式，手续费是可变的：make one transaction(100 XDAG) block(from No.1 mainblock to address block)
        Address from = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
        Address to1 = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey1)), XDAG_FIELD_OUTPUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateNewTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(100, XUnit.XDAG), XAmount.of(10, XUnit.XDAG) ); //收10 Xdag 手续费

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
        Address txAddress =  new Address(txBlock.getHashLow(), false);
        pending.add(txAddress);
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
        // 4. confirm transaction block with 16 mainblocks
        for (int i = 1; i <= 16; i++) {
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

        XAmount poolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
        XAmount addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount mainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
        assertEquals("900.00", poolBalance.toDecimal(2, XUnit.XDAG).toString());//1000 - 100  = 900.00
        assertEquals("90.00", addressBalance.toDecimal(2, XUnit.XDAG).toString());//100 - 10 = 90.00
        assertEquals("1034.0" , mainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 10 reward.
        XAmount mainBlockFee = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(10).getHashLow()).getFee();
        assertEquals("10.0",mainBlockFee.toDecimal(1, XUnit.XDAG).toString());

//        blockchain.unSetMain(extraBlockList.get(10));//test rollback
//
//        XAmount RollBackPoolBalance = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
//        XAmount RollBackAddressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
//        XAmount RollBackMainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
//        assertEquals("1000.00", RollBackPoolBalance.toDecimal(2, XUnit.XDAG).toString());//rollback 900 + 100 = 1000
//        assertEquals("0.00", RollBackAddressBalance.toDecimal(2, XUnit.XDAG).toString());//rollback 99.9 -99.9 = 0
//        assertEquals("0.0" , RollBackMainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock reward back 1024 - 1024 = 0.
//
//
//        //TODO:test wallet create txBlock with fee = 0,
//        List<Block> txList = Lists.newLinkedList();
//        for (int i = 1; i <= 10; i++) {
//            Block txBlock_0;
//            if (i == 1){//TODO:test give miners reward with a TX block :one input several output
//                txBlock_0 = generateMinerRewardTxBlock(config, poolKey, xdagTime - i, from, to,to1, XAmount.of(20,XUnit.XDAG),XAmount.of(10,XUnit.XDAG), XAmount.of(10,XUnit.XDAG));
//            }else {
//                txBlock_0 = generateWalletTransactionBlock(config, poolKey, xdagTime - i, from, to, XAmount.of(1,XUnit.XDAG));}
//
//            assertEquals(XAmount.ZERO, txBlock_0.getFee());//fee is zero.
//            // 4. local check
//            assertTrue(blockchain.canUseInput(txBlock_0));
//            assertTrue(blockchain.checkMineAndAdd(txBlock_0));
//            // 5. remote check
//            assertTrue(blockchain.canUseInput(new Block(txBlock_0.getXdagBlock())));
//            assertTrue(blockchain.checkMineAndAdd(txBlock_0));
//
//            result = blockchain.tryToConnect(txBlock_0);
//            // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
//            assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
//            txList.add(txBlock_0);
//        }
//        pending.clear();
//        for (Block tx : txList) {
//            pending.add(new Address(tx.getHashLow(), false));
//        }
//        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
//        // 4. confirm transaction block with 16 mainblocks
//        for (int i = 1; i <= 16; i++) {
//            generateTime += 64000L;
//            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
//            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
//                    XdagField.FieldType.XDAG_FIELD_COINBASE,
//                    true));
//            long time = XdagTime.msToXdagtimestamp(generateTime);
//            xdagTime = XdagTime.getEndOfEpoch(time);
//            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
//            blockchain.tryToConnect(extraBlock);
//            ref = extraBlock.getHashLow();
//            extraBlockList.add(extraBlock);
//            pending.clear();
//        }
//        XAmount poolBalance_0 = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
//        XAmount addressBalance_0 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
//        XAmount addressBalance_1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
//        XAmount mainBlockLinkTxBalance_0 = blockchain.getBlockByHash(extraBlockList.get(26).getHash(), false).getInfo().getAmount();
//        assertEquals("971.00", poolBalance_0.toDecimal(2, XUnit.XDAG).toString());//1000 - 20 - 1*9  = 971.00
//        assertEquals("18.00", addressBalance_0.toDecimal(2, XUnit.XDAG).toString());//0  + (10-0.1) + (1 - 0.1) * 9  = 18   (ps:0.1 is fee)
//        assertEquals("9.90", addressBalance_1.toDecimal(2, XUnit.XDAG).toString());//0 + 10 - 0.1 = 9.90
//        assertEquals("1025.1" , mainBlockLinkTxBalance_0.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 0.1*11 reward.
//        XAmount mainBlockFee_1 = kernel.getBlockStore().getBlockInfoByHash(extraBlockList.get(26).getHashLow()).getFee();
//        assertEquals("1.1",mainBlockFee_1.toDecimal(1, XUnit.XDAG).toString());
//
//        //TODO:test rollback
//        blockchain.unSetMain(extraBlockList.get(26));
//
//        XAmount RollBackPoolBalance_1 = blockchain.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(poolKey));
//        XAmount RollBackAddressBalance_0 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
//        XAmount RollBackAddressBalance_1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
//        XAmount RollBackMainBlockLinkTxBalance_1 = blockchain.getBlockByHash(extraBlockList.get(26).getHash(), false).getInfo().getAmount();
//        assertEquals("1000.00", RollBackPoolBalance_1.toDecimal(2, XUnit.XDAG).toString());//1000
//        assertEquals("0.00", RollBackAddressBalance_0.toDecimal(2, XUnit.XDAG).toString());//rollback is zero
//        assertEquals("0.00", RollBackAddressBalance_1.toDecimal(2, XUnit.XDAG).toString());
//        assertEquals("0.0" , RollBackMainBlockLinkTxBalance_1.toDecimal(1, XUnit.XDAG).toString());//  rollback is zero

    }

    @Test
    public void testIfTxBlockTobeMain() {
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair addrKey1 = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), XAmount.of(1000, XUnit.XDAG));
        ImportResult result = blockchain.tryToConnect(addressBlock);
        // import address block, result must be IMPORTED_BEST
        assertSame(result, IMPORTED_BEST);
        List<Address> pending = Lists.newArrayList();
        List<Block> extraBlockList = Lists.newLinkedList();

        Address from = new Address(addressBlock.getHashLow(), XDAG_FIELD_IN, false);
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT, true);
        Address to1 = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey1)), XDAG_FIELD_OUTPUT, true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));

        Block TxBlockTobeMain = generateOldTransactionBlock(config, poolKey, xdagTime, from, XAmount.of(100, XUnit.XDAG), to, XAmount.of(30, XUnit.XDAG), to1, XAmount.of(70, XUnit.XDAG));
        result = blockchain.tryToConnect(TxBlockTobeMain);
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);

        blockchain.setMain(TxBlockTobeMain);// set the tx block as mainBlock.

        XAmount poolBalance = blockchain.getBlockByHash(addressBlock.getHash(), false).getInfo().getAmount();
        XAmount addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount addressBalance1 = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey1));
        XAmount TxBlockAward =TxBlockTobeMain.getInfo().getAmount();

        assertEquals("900.00", poolBalance.toDecimal(2, XUnit.XDAG).toString());//1000 - 100  = 900.00
        assertEquals("29.90", addressBalance.toDecimal(2, XUnit.XDAG).toString());
        assertEquals("69.90", addressBalance1.toDecimal(2, XUnit.XDAG).toString());

        //Tx block get mainBlock reward 1024 , and get itself fee reward 0.2
        assertEquals("1024.2" , TxBlockAward.toDecimal(1, XUnit.XDAG).toString());
        assertEquals("0.2" , TxBlockTobeMain.getFee().toDecimal(1, XUnit.XDAG).toString());
        Bytes32 ref = TxBlockTobeMain.getHashLow();
        //  create 10 mainblocks
        for (int i = 1; i <= 10; i++) {
            generateTime += 64000L;
            pending.clear();
            pending.add(new Address(ref, XDAG_FIELD_OUT,false));
            pending.add(new Address(keyPair2Hash(wallet.getDefKey()),
                    XdagField.FieldType.XDAG_FIELD_COINBASE,
                    true));
            long time = XdagTime.msToXdagtimestamp(generateTime);
            xdagTime = XdagTime.getEndOfEpoch(time);
            Block extraBlock = generateExtraBlock(config, poolKey, xdagTime, pending);
            result = blockchain.tryToConnect(extraBlock);
            assertSame(result, IMPORTED_BEST);
            ref = extraBlock.getHashLow();
            extraBlockList.add(extraBlock);
        }

        from = new Address(TxBlockTobeMain.getHashLow(), XDAG_FIELD_IN, false);

        xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateOldTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(1000, XUnit.XDAG));

// 4. local check
        assertTrue(blockchain.canUseInput(txBlock));
        assertTrue(blockchain.checkMineAndAdd(txBlock));
// 5. remote check
        assertTrue(blockchain.canUseInput(new Block(txBlock.getXdagBlock())));
        assertTrue(blockchain.checkMineAndAdd(txBlock));

        result = blockchain.tryToConnect(txBlock);
        Block c = blockchain.getBlockByHash(txBlock.getHashLow(), true);
// import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        assertTrue(result == IMPORTED_NOT_BEST || result == IMPORTED_BEST);
// there is 12 blocks and 10 mainblocks

        pending.clear();
        pending.add(new Address(txBlock.getHashLow(), false));
        ref = extraBlockList.get(extraBlockList.size() - 1).getHashLow();
// 4. confirm transaction block with 3 mainblocks
        for (int i = 1; i <= 4; i++) {
            generateTime += 64000L;
            pending.add(new Address(ref, XDAG_FIELD_OUT, false));
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
        XAmount TXBalance = blockchain.getBlockByHash(TxBlockTobeMain.getHash(), false).getInfo().getAmount();
        assertEquals("24.2", TXBalance.toDecimal(1, XUnit.XDAG).toString());
        addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        assertEquals("1029.80", addressBalance.toDecimal(2, XUnit.XDAG).toString());


        // 输出签名只有一个
        SECPSignature signature = TxBlockTobeMain.getOutsig();
        byte[] publicKeyBytes = poolKey.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
        Bytes digest = Bytes.wrap(TxBlockTobeMain.getSubRawData(TxBlockTobeMain.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
        Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
        // use hyperledger besu crypto native secp256k1
        assertTrue(Sign.SECP256K1.verify(hash, signature, poolKey.getPublicKey()));

    }



    @Test
    public void testNew2NewTxAboutRejected() {
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block
        Block addressBlock = generateAddressBlock(config, addrKey, generateTime);
        MockBlockchain blockchain = new MockBlockchain(kernel);
        blockchain.getAddressStore().updateBalance(Keys.toBytesAddress(poolKey), XAmount.of(1000, XUnit.XDAG));
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

        Address from = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(poolKey)), XDAG_FIELD_INPUT,true);
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));

        //0.09 is not enough,expect to  be rejected!
        Block InvalidTxBlock = generateNewTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(90, XUnit.MILLI_XDAG));
        result = blockchain.tryToConnect(InvalidTxBlock);
        assertEquals(INVALID_BLOCK, result);// 0.09 < 0.1, Invalid block!

        KeyPair addrKey1 = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        Address to1 = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey1)), XDAG_FIELD_OUTPUT,true);
        Block txBlock = generateMinerRewardTxBlock(config, poolKey, xdagTime - 1, from, to, to1, XAmount.of(2,XUnit.XDAG),XAmount.of(1901,XUnit.MILLI_XDAG), XAmount.of(99,XUnit.MILLI_XDAG));
        // import transaction block, result may be IMPORTED_NOT_BEST or IMPORTED_BEST
        result = blockchain.tryToConnect(txBlock);
        assertEquals(INVALID_BLOCK, result);
        // there is 12 blocks and 10 mainblocks
    }

    @Test
    public void testOld2NewTransaction(){
        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        // 1. first block get 1024 reward
        Block addressBlock = generateAddressBlock(config, poolKey, generateTime);//get another 1000 amount
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
        Address to = new Address(BytesUtils.arrayToByte32(Keys.toBytesAddress(addrKey)), XDAG_FIELD_OUTPUT,true);
        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));

        //TODO: 0.05 is not enough to pay fee.
        Block InvalidTxBlock = generateOldTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(50, XUnit.MILLI_XDAG));
        result = blockchain.tryToConnect(InvalidTxBlock);
        assertEquals(INVALID_BLOCK, result);//0.05 < 0.1, Invalid block!

        Block txBlock = generateOldTransactionBlock(config, poolKey, xdagTime - 1, from, to, XAmount.of(1000, XUnit.XDAG));

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

        XAmount poolBalance = blockchain.getBlockByHash(addressBlock.getHash(),false).getInfo().getAmount();
        XAmount mainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
        XAmount addressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        assertEquals("1024.0" , poolBalance.toDecimal(1, XUnit.XDAG).toString());//2024 - 1000 = 1024,
        assertEquals("1024.1" , mainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//A mainBlock link a TX get 1024 + 0.1 reward.
        assertEquals("999.9", addressBalance.toDecimal(1, XUnit.XDAG).toString());//1000 - 0.1 = 999.9, A TX subtract 0.1 XDAG fee.


        //Rollback mainBlock 10
        blockchain.unSetMain(extraBlockList.get(10));

        XAmount RollBackPoolBalance = blockchain.getBlockByHash(addressBlock.getHash(),false).getInfo().getAmount();
        XAmount RollBackAddressBalance = kernel.getAddressStore().getBalanceByAddress(Keys.toBytesAddress(addrKey));
        XAmount RollBackMainBlockLinkTxBalance = blockchain.getBlockByHash(extraBlockList.get(10).getHash(), false).getInfo().getAmount();
        assertEquals("2024.00", RollBackPoolBalance.toDecimal(2, XUnit.XDAG).toString());//1024 + 1000  = 2024
        assertEquals("0.00", RollBackAddressBalance.toDecimal(2, XUnit.XDAG).toString());//rollback is zero.
        assertEquals("0.0" , RollBackMainBlockLinkTxBalance.toDecimal(1, XUnit.XDAG).toString());//

    }
    @Test
    public void testCanUseInput() {
//        Date date = fastDateFormat.parse("2020-09-20 23:45:00");
        long generateTime = 1600616700000L;
        KeyPair fromKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair toKey = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
        Block fromAddrBlock = generateAddressBlock(config, fromKey, generateTime);
        Block toAddrBlock = generateAddressBlock(config, toKey, generateTime);

        Address from = new Address(fromAddrBlock.getHashLow(), XDAG_FIELD_IN,true);
        Address to = new Address(toAddrBlock);

        BlockchainImpl blockchain = spy(new BlockchainImpl(kernel));

        long xdagTime = XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(generateTime));
        Block txBlock = generateOldTransactionBlock(config, fromKey, xdagTime - 1, from, to, XAmount.of(100, XUnit.XDAG));

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
        assertEquals("1024.0", blockchain.getStartAmount(1L).toDecimal(1, XUnit.XDAG).toString());
        assertEquals("128.0", blockchain.getStartAmount(config.getApolloForkHeight()).toDecimal(1, XUnit.XDAG).toString());
    }

    @Test
    public void testGetSupply() {
        BlockchainImpl blockchain = new BlockchainImpl(kernel);
        assertEquals("1024.0", blockchain.getSupply(1).toDecimal(1, XUnit.XDAG).toString());
        assertEquals("2048.0", blockchain.getSupply(2).toDecimal(1, XUnit.XDAG).toString());
        assertEquals("3072.0", blockchain.getSupply(3).toDecimal(1, XUnit.XDAG).toString());
        XAmount apolloSypply = blockchain.getSupply(config.getApolloForkHeight());
        assertEquals(String.valueOf(config.getApolloForkHeight() * 1024 - (1024 - 128)),
                apolloSypply.toDecimal(0, XUnit.XDAG).toString());
    }

    @Test
    public void testOriginFork() {
        String firstDiff = "3f4a35eaa6";
        String secondDiff = "1a24b50c9f2";

        KeyPair addrKey = KeyPair.create(secretary_1, Sign.CURVE, Sign.CURVE_NAME);
        KeyPair poolKey = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
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
        KeyPair poolKey = KeyPair.create(secretary_2, Sign.CURVE, Sign.CURVE_NAME);
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
