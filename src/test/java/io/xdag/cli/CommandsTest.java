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
package io.xdag.cli;


import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_SNAPSHOT;
import static junit.framework.TestCase.assertEquals;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.consensus.SyncManager;
import io.xdag.BlockBuilder;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockInfo;
import io.xdag.core.Blockchain;
import io.xdag.core.TxHistory;
import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import io.xdag.core.XdagExtStats;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.core.XdagTopStatus;
import io.xdag.crypto.Keys;
import io.xdag.crypto.Sign;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.miner.Miner;
import io.xdag.net.manager.NetDBManager;
import io.xdag.net.message.NetDB;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;

public class CommandsTest {

    Block mainblock;

    Config config = new DevnetConfig();

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    BigInteger private_2 = new BigInteger("10a55f0c18c46873ddbf9f15eddfc06f10953c601fd144474131199e04148046", 16);

    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);

    SECPPrivateKey secretkey_2 = SECPPrivateKey.create(private_2, Sign.CURVE_NAME);

    KeyPair keyPair_1 = KeyPair.create(secretkey_1, Sign.CURVE, Sign.CURVE_NAME);
    KeyPair keyPair_2 = KeyPair.create(secretkey_2, Sign.CURVE, Sign.CURVE_NAME);

    Kernel kernel;

    Wallet wallet;

    AddressStore addressStore;

    BlockStore blockStore;

    Blockchain blockchain;

    SyncManager syncManager;

    Commands commands;

    long generateTime = 1600616700000L;
    long xdagTime = XdagTime.getEndOfEpoch(generateTime);

    @Before
    public void setUp() throws Exception {
        List<Address> pending = Lists.newArrayList();
        mainblock = BlockBuilder.generateExtraBlock(config, keyPair_1, xdagTime, "xdagj_test", pending);
        List<KeyPair> accounts = Lists.newArrayList();
        accounts.add(keyPair_1);
        accounts.add(keyPair_2);

        kernel = Mockito.mock(Kernel.class);
        addressStore = Mockito.mock(AddressStore.class);
        wallet = Mockito.mock(Wallet.class);
        blockchain = Mockito.mock(Blockchain.class);
        syncManager = Mockito.mock(SyncManager.class);
        blockStore = Mockito.mock(BlockStore.class);

        Mockito.when(kernel.getAddressStore()).thenReturn(addressStore);
        Mockito.when(kernel.getWallet()).thenReturn(wallet);
        Mockito.when(kernel.getBlockchain()).thenReturn(blockchain);
        Mockito.when(kernel.getSyncMgr()).thenReturn(syncManager);
        Mockito.when(kernel.getBlockStore()).thenReturn(blockStore);

        Mockito.when(wallet.getAccounts()).thenReturn(accounts);
        Mockito.when(addressStore.getBalanceByAddress(Keys.toBytesAddress(keyPair_1))).thenReturn(XAmount.of(9999, XUnit.XDAG));
        Mockito.when(addressStore.getBalanceByAddress(Keys.toBytesAddress(keyPair_2))).thenReturn(XAmount.of(8888, XUnit.XDAG));

        commands = new Commands(kernel);
    }

    @Test
    public void testPrintBlock() {
        String pstr = Commands.printBlock(mainblock, true);
        assertEquals("5H1B51l0jPyaOaS1f0LMsudJV52iglYG   00000000", pstr);

        pstr = Commands.printBlock(mainblock, false);
        long time = XdagTime.xdagTimestampToMs(mainblock.getTimestamp());
        assertEquals(String.format("00000000   5H1B51l0jPyaOaS1f0LMsudJV52iglYG   %s   Pending   xdagj_test\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(time)), pstr);
    }

    @Test
    public void testAccount() {
        String str = commands.account(2);
        assertEquals("""
                PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas 9999.000000000 XDAG
                35KpNArHncGduckwbaW27tAfwzN4rNtX2 8888.000000000 XDAG
                """, str);
    }

    @Test
    public void testBalance() {
        String str = commands.balance("PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas");
        assertEquals("Account balance: 9999.000000000 XDAG", str);

        str = commands.balance("35KpNArHncGduckwbaW27tAfwzN4rNtX2");
        assertEquals("Account balance: 8888.000000000 XDAG", str);

        // test empty address for all wallet balance
        str = commands.balance(StringUtils.EMPTY);
        assertEquals("Balance: 18887.000000000 XDAG", str);
    }

    @Test
    public void testXfer() {
        XAmount xAmount = XAmount.of(100, XUnit.XDAG);
        String str = commands.xfer(xAmount.toDecimal(2, XUnit.XDAG).doubleValue(), BasicUtils.pubAddress2Hash("PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas"), null);
        System.out.println(str);
        assertEquals("Transaction :{ \n"
                + "}, it will take several minutes to complete the transaction.", str);
    }

    @Test
    public void testStats() {
        NetDB netDB = new NetDB();
        netDB.addNewIP("127.0.0.1:7001");
        NetDBManager netDBManager = new NetDBManager(config);

        Mockito.when(blockchain.getXdagTopStatus()).thenReturn(new XdagTopStatus());
        Mockito.when(blockchain.getXdagStats()).thenReturn(new XdagStats());
        Mockito.when(blockchain.getXdagExtStats()).thenReturn(new XdagExtStats());
        Mockito.when(blockchain.getSupply(Mockito.anyLong())).thenReturn(XAmount.of(1400000000, XUnit.XDAG));
        Mockito.when(addressStore.getAllBalance()).thenReturn(XAmount.of(100000, XUnit.XDAG));
        Mockito.when(addressStore.getAddressSize()).thenReturn(UInt64.valueOf(100));
        Mockito.when(kernel.getNetDB()).thenReturn(netDB);
        Mockito.when(kernel.getNetDBMgr()).thenReturn(netDBManager);
        String str = commands.stats();
        assertEquals("""
                Statistics for ours and maximum known parameters:
                            hosts: 1 of 0
                           blocks: 0 of 0
                      main blocks: 0 of 0
                     extra blocks: 0
                    orphan blocks: 0
                 wait sync blocks: 0
                 chain difficulty: 0 of 0
                      XDAG supply: 1400000000.000000000 of 1400000000.000000000
                  XDAG in address: 100000.000000000
                4 hr hashrate KHs: 0.000000000 of 0.000000000
                Number of Address: 100""", str);
    }

    @Test
    public void testPrintBlockInfo() {
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setDifficulty(BigInteger.ZERO);

        mainblock.setInfo(blockInfo);
        String str = commands.printBlockInfo(mainblock, false);
        assertEquals("""
                      time: 1970-01-01 08:00:00.000
                 timestamp: 0
                     flags: 0
                     state: Pending
                      hash: 3529400c8dd30a759b7fffe8931f5e9e1c57ce53648f5f4a937ec7c2254f98e7
                    remark:\s
                difficulty: 0
                   balance: 55hPJcLHfpNKX49kU85XHJ5eH5Po/3+b  0.000000000
                -----------------------------------------------------------------------------------------------------------------------------
                                               block as transaction: details
                 direction  address                                    amount
                       fee: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA           0.000000000

                -----------------------------------------------------------------------------------------------------------------------------
                                               block as address: details
                 direction  address                                    amount                 time

                """, str);
    }

    @Test
    public void testMainblocks() {
        List<Block> blocks = Lists.newArrayList();
        long mainblockTime = generateTime;
        for (int i = 1; i <= 2; i++) {
            Block block = BlockBuilder.generateExtraBlock(config, keyPair_1, mainblockTime, null);
            block.getInfo().setHeight(i);
            blocks.add(block);
            mainblockTime += 64000L;


        }
        Mockito.when(blockchain.listMainBlocks(Mockito.anyInt())).thenReturn(blocks);
        String str = commands.mainblocks(2);
        long time1 = XdagTime.xdagTimestampToMs(blocks.get(0).getTimestamp());
        long time2 = XdagTime.xdagTimestampToMs(blocks.get(1).getTimestamp());
        String st1 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(time1);
        String st2 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(time2);

        assertEquals(String.format("""
                ---------------------------------------------------------------------------------------------------------
                height        address                            time                      state     mined by           \s
                ---------------------------------------------------------------------------------------------------------
                00000001   jIC5NLnZ9PRkodqO2/qoLtSUVkegE28S   %s   Pending                                  \s
                00000002   mEa+M9+o6uakriOCoGw3rqaWBmE+TGUe   %s   Pending                                  \s""",
                st1, st2), str);
    }

    @Test
    public void testMinedBlocks() {
        List<Block> blocks = Lists.newArrayList();
        long mainblockTime = generateTime;
        for (int i = 1; i <= 2; i++) {
            Block block = BlockBuilder.generateExtraBlock(config, keyPair_1, mainblockTime, null);
            block.getInfo().setHeight(i);
            blocks.add(block);
            mainblockTime += 64000L;
        }
        Mockito.when(blockchain.listMinedBlocks(Mockito.anyInt())).thenReturn(blocks);
        String str = commands.minedBlocks(2);
        long time1 = XdagTime.xdagTimestampToMs(blocks.get(0).getTimestamp());
        long time2 = XdagTime.xdagTimestampToMs(blocks.get(1).getTimestamp());
        String st1 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(time1);
        String st2 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(time2);
        assertEquals(String.format("""
                ---------------------------------------------------------------------------------------------------------
                height        address                            time                      state     mined by           \s
                ---------------------------------------------------------------------------------------------------------
                00000001   jIC5NLnZ9PRkodqO2/qoLtSUVkegE28S   %s   Pending                                  \s
                00000002   mEa+M9+o6uakriOCoGw3rqaWBmE+TGUe   %s   Pending                                  \s""",
        st1, st2), str);
    }

    @Test
    public void testKeygen()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        Mockito.when(kernel.getXdagState()).thenReturn(XdagState.INIT);
        String str = commands.keygen();
        assertEquals("Key 1 generated and set as default,now key size is:2", str);
    }

    @Test
    public void testMiners() {
        Miner mockPoolMiner = new Miner(BytesUtils.arrayToByte32(Keys.toBytesAddress(keyPair_1.getPublicKey())));
        Miner mockMiner2 = new Miner(BytesUtils.arrayToByte32(Keys.toBytesAddress(keyPair_2.getPublicKey())));
        Map<Bytes, Miner> mockActivateMiners = Maps.newHashMap();
        mockActivateMiners.put(mockPoolMiner.getAddressHash(), mockPoolMiner);
        mockActivateMiners.put(mockMiner2.getAddressHash(), mockMiner2);

        MinerManager mockMinerManager = Mockito.mock(MinerManager.class);
        Mockito.when(kernel.getPoolMiner()).thenReturn(mockPoolMiner);
        Mockito.when(kernel.getMinerManager()).thenReturn(mockMinerManager);
        Mockito.when(mockMinerManager.getActivateMiners()).thenReturn(mockActivateMiners);

        String str = commands.miners();
        assertEquals("fee:PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas\n", str);
    }

    @Test
    public void testState() {
        Mockito.when(kernel.getXdagState()).thenReturn(XdagState.INIT);
        String str = commands.state();
        assertEquals("Pool Initializing....", str);
    }

    @Test
    public void testDisConnectMinerChannel() {
        Map<InetSocketAddress, MinerChannel> mockMinerChannels = Maps.newHashMap();
        MinerChannel mc = Mockito.mock(MinerChannel.class);
        InetSocketAddress host = new InetSocketAddress("127.0.0.1", 10001);

        MinerManager mockMinerManager = Mockito.mock(MinerManager.class);
        Mockito.when(mockMinerManager.getActivateMinerChannels()).thenReturn(mockMinerChannels);
        Mockito.when(kernel.getMinerManager()).thenReturn(mockMinerManager);
        Mockito.when(mockMinerManager.getChannelByHost(host)).thenReturn(mc);

        String str = commands.disConnectMinerChannel("127.0.0.1:10001");
        assertEquals("disconnect a channel：127.0.0.1:10001", str);
        str = commands.disConnectMinerChannel("127.0.0.1:10002");
        assertEquals("Can't find the corresponding channel, please check", str);
        str = commands.disConnectMinerChannel("all");
        assertEquals("disconnect all channels...", str);
    }

    @Test
    public void testBalanceMaxXfer() {
        String str = commands.balanceMaxXfer();
        assertEquals("0.000000000", str);
    }

    @Test
    public void testAddress() {
        Bytes32 addrByte32 = BytesUtils.arrayToByte32(Keys.toBytesAddress(keyPair_1.getPublicKey()));
        List<TxHistory> txHistoryList = Lists.newArrayList();
        Address addr = new Address(BasicUtils.keyPair2Hash(keyPair_1), XDAG_FIELD_SNAPSHOT, XAmount.of(9999, XUnit.XDAG),true);
        txHistoryList.add(new TxHistory(addr, Bytes32.random().toHexString(), generateTime, "xdagj_test"));
        Mockito.when(blockchain.getBlockTxHistoryByAddress(addrByte32, 1)).thenReturn(txHistoryList);
        String str = commands.address(addrByte32, 1);

        String st = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone("GMT+08:00")).format(generateTime);

        assertEquals(String.format("""
                 OverView
                 address: PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas
                 balance: 9999.000000000
                                
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                 direction  address                                    amount                 time
                                
                 snapshot: PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas           9999.000000000   %s
                """, st), str);
    }

    @Test
    public void testXferToNew() {
        Mockito.when(wallet.getDefKey()).thenReturn(keyPair_1);
        String str = commands.xferToNew();
        assertEquals("""
                 Transaction :{\s
                 }, it will take several minutes to complete the transaction.""", str);
    }

}
