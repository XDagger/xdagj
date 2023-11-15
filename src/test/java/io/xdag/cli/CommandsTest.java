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

import static io.xdag.core.XUnit.MILLI_XDAG;
import static io.xdag.core.XUnit.XDAG;
import static io.xdag.utils.WalletUtils.toBase58;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.util.Lists;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.xdag.DagKernel;
import io.xdag.Network;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.core.BlockHeader;
import io.xdag.core.DagchainImpl;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.core.state.Account;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.net.ChannelManager;
import io.xdag.net.NetDB;
import io.xdag.net.NetDBManager;
import io.xdag.net.Peer;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;
import io.xdag.utils.WalletUtils;

public class CommandsTest {

    @Rule
    public TemporaryDatabaseRule temporaryDBFactory = new TemporaryDatabaseRule();

    private Config config;
    private DagchainImpl chain;
    private TransactionResult res;

    private final byte[] coinbase = BytesUtils.random(20);
    private byte[] prevHash;

    private final Network network = Network.DEVNET;
    private final KeyPair key = SampleKeys.KEY1;
    private final byte[] from = Keys.toBytesAddress(key);
    private final byte[] to = BytesUtils.random(20);
    private final XAmount value = XAmount.of(20, XDAG);
    private final XAmount fee = XAmount.of(100, MILLI_XDAG);
    private final long nonce = 12345;
    private final byte[] data = BytesUtils.of("test");
    private final long timestamp = TimeUtils.currentTimeMillis() - 60 * 1000;
    private final Transaction tx = new Transaction(network, TransactionType.TRANSFER, to, value, fee, nonce, timestamp,
            data).sign(key);
    private PendingManager pendingMgr;
    private DagKernel kernel;
    private Wallet wallet;
    private String pwd;
    private Commands commands;

    @Before
    public void setUp() {
        config = new UnitTestnetConfig(Constants.DEFAULT_ROOT_DIR);
        pendingMgr = Mockito.mock(PendingManager.class);
        kernel = Mockito.mock(DagKernel.class);
        when(pendingMgr.getPendingTransactions()).thenReturn(Lists.newArrayList(new PendingManager.PendingTransaction(tx, res)));
        chain = new DagchainImpl(config, pendingMgr, temporaryDBFactory);
        when(kernel.getDagchain()).thenReturn(chain);

        res = new TransactionResult();
        prevHash = chain.getLatestMainBlockHash();
        commands = new Commands(kernel);

        pwd = "password";
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();
        wallet.lock();

        when(kernel.getWallet()).thenReturn(wallet);
        when(kernel.getPendingManager()).thenReturn(pendingMgr);
        when(kernel.getConfig()).thenReturn(config);
    }

    @Test
    public void testPrintBlock() {
        assertEquals(0, chain.getLatestMainBlockNumber());
        assertNotNull(chain.getLatestMainBlockHash());
        assertNotNull(chain.getLatestMainBlock());

        String pstr = Commands.printBlock(chain.getLatestMainBlock(), true);
        assertEquals("0x339e606c89b26eb7e00193c77f1f820b5ba7d1b5d32517e831ee357a6834bf4a   00000000", pstr);
    }

    @Test
    public void testAccount() {
        wallet.unlock(pwd);
        assertEquals("""
                ------------------------------------------------------------
                address                                                 XDAG
                ------------------------------------------------------------
                N3RC53vbaDNrziTdWmctBEeQ4fo38moXu                          0
                """, commands.account(1));
    }

    @Test
    public void testBalance() {
        wallet.unlock(pwd);
        assertEquals("Balance: 0 XDAG", commands.balance(""));
    }

    @Test
    public void testStats() {
        NetDB netDB = new NetDB();
        netDB.addNewIP("127.0.0.1:8001");
        netDB.addNewIP("127.0.0.1:8002");
        netDB.addNewIP("127.0.0.1:8003");
        netDB.addNewIP("127.0.0.1:8004");

        NetDBManager netDBManager = Mockito.mock(NetDBManager.class);
        when(netDBManager.getNetDB()).thenReturn(netDB);
        when(kernel.getNetDBManager()).thenReturn(netDBManager);

        Peer peer1 = Mockito.mock(Peer.class);
        Peer peer2 = Mockito.mock(Peer.class);
        when(peer1.getLatestMainBlock()).thenReturn(chain.getLatestMainBlock());
        when(peer2.getLatestMainBlock()).thenReturn(chain.getLatestMainBlock());

        List<Peer> peersList = Lists.newArrayList(peer1, peer2);
        ChannelManager channelManager = Mockito.mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(peersList);
        when(kernel.getChannelManager()).thenReturn(channelManager);

        String actual = commands.stats();
        String expected = """
                        Statistics for ours and maximum known parameters:
                                    hosts: 2 of 4
                              main blocks: 0 of 0
                               pending tx: 0
                              xdag supply: 0.000000000 of 0.000000000""";
        assertEquals(expected, actual);
    }

    @Test
    public void testPrintBlockInfo() {
        MainBlock mainBlock = createMainBlock(1);

        String str = commands.printBlockInfo(mainBlock);
        assertEquals(String.format("""
                Block Header:
                  number: 1
                  previous hash: 0x339e606c89b26eb7e00193c77f1f820b5ba7d1b5d32517e831ee357a6834bf4a
                  hash: %s
                  coinbase: %s
                  timestamp: %s
                  transactions root: %s
                  result root: 0x4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a
                  difficulty target (nBits): 545259519
                  nonce: %s
                Block Body:
                  Transaction [type=TRANSFER, hash=%s, from=%s, to=%s, value=20.00, fee=0.10, nonce=12345, timestamp=%s, data=0x74657374]
                """,

                Bytes.wrap(mainBlock.getHash()).toHexString(),
                WalletUtils.toBase58(coinbase),
                mainBlock.getTimestamp(),
                Bytes.wrap(mainBlock.getTransactionsRoot()).toHexString(),
                mainBlock.getNonce(),
                Bytes.wrap(tx.getHash()).toHexString(),
                toBase58(Keys.toBytesAddress(key)),
                toBase58(to),
                tx.getTimestamp()
                ), str);
    }

    @Test
    public void testBlock() {
        Bytes hash = Bytes.fromHexString("0x339e606c89b26eb7e00193c77f1f820b5ba7d1b5d32517e831ee357a6834bf4a");
        assertEquals("""
                        Block Header:
                          number: 0
                          previous hash: 0x0000000000000000000000000000000000000000000000000000000000000000
                          hash: 0x339e606c89b26eb7e00193c77f1f820b5ba7d1b5d32517e831ee357a6834bf4a
                          coinbase: 111111111111111111117K4nzc
                          timestamp: 1604742400000
                          transactions root: 0x0000000000000000000000000000000000000000000000000000000000000000
                          result root: 0x0000000000000000000000000000000000000000000000000000000000000000
                          difficulty target (nBits): 545259519
                          nonce: 0
                        Block Body:
                          empty
                        """,
                commands.block(Bytes32.wrap(hash)));
    }

    @Test
    public void testMainblocks() {
        assertEquals("empty", commands.mainblocks(50));

        MainBlock mainBlock = createMainBlock(1);
        BlockState bs = chain.getLatestBlockState().clone();
        chain.addMainBlock(mainBlock, bs);

        assertEquals(String.format("""
                                -------------------------------------------------------------------------------------------------------------------------------------------
                                  height                                                                 hash                      time                            coinbase
                                -------------------------------------------------------------------------------------------------------------------------------------------
                                00000001   %s   %s   %s""",
                Bytes.wrap(mainBlock.getHash()).toHexString(),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(mainBlock.getTimestamp()),
                toBase58(mainBlock.getCoinbase())),
                commands.mainblocks(50));
    }

    @Test
    public void testMinedBlocks() {
        wallet.unlock(pwd);
        assertEquals("empty", commands.minedBlocks(50));

        MainBlock mainBlock = createMainBlock(1, Keys.toBytesAddress(wallet.getDefKey()), BytesUtils.EMPTY_BYTES, Collections.singletonList(tx), Collections.singletonList(res));
        BlockState bs = chain.getLatestBlockState().clone();
        chain.addMainBlock(mainBlock, bs);

        assertEquals(String.format("""
                                -------------------------------------------------------------------------------------------------------------------------------------------
                                  height                                                                 hash                      time                            coinbase
                                -------------------------------------------------------------------------------------------------------------------------------------------
                                00000001   %s   %s   %s""",
                        Bytes.wrap(mainBlock.getHash()).toHexString(),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(mainBlock.getTimestamp()),
                        toBase58(mainBlock.getCoinbase())),
                commands.minedBlocks(50));
    }

    @Test
    public void testState() {
        Mockito.when(kernel.getState()).thenReturn(DagKernel.State.RUNNING);
        assertEquals("RUNNING", commands.state());
    }

    @Test
    public void testAddress() {
        wallet.unlock(pwd);
        String str = commands.address(from, 1);
        assertEquals("""
                 OverView
                 address: PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas
                 balance: 0.000000000
                                 
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                                 
                """, str);

        Keys.toBytesAddress(wallet.getDefKey());

        AccountState as = chain.getLatestAccountState().clone();
        BlockState bs = chain.getLatestBlockState().clone();

        byte[] fromAddress = Keys.toBytesAddress(key);
        Account account = as.getAccount(fromAddress);
        as.adjustAvailable(fromAddress, XAmount.of(1000, XDAG));
        Transaction tx = new Transaction(network, TransactionType.TRANSFER, to, value, fee, account.getNonce(), timestamp,
                data).sign(key);

        MainBlock mainBlock = createMainBlock(1, coinbase, BytesUtils.EMPTY_BYTES, Collections.singletonList(tx), Collections.singletonList(res));

        assertTrue(chain.importBlock(mainBlock, as, bs));

        str = commands.address(from, 0);
        assertEquals(String.format("""
                 OverView
                 address: PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas
                 balance: 979.900000000
                                 
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                                 
                Transaction [type=TRANSFER, hash=%s, from=PbwjuQP3y9F3ZnbbWUvue4zpgkQv3DHas, to=%s, value=20.00, fee=0.10, nonce=0, timestamp=%s, data=0x74657374]
                """, Bytes.wrap(tx.getHash()).toHexString(), toBase58(to), tx.getTimestamp()), str);
    }

    private MainBlock createMainBlock(long number) {
        return createMainBlock(number, Collections.singletonList(tx), Collections.singletonList(res));
    }

    private MainBlock createMainBlock(long number, List<Transaction> transactions, List<TransactionResult> results) {
        return createMainBlock(number, coinbase, BytesUtils.EMPTY_BYTES, transactions, results);
    }

    private MainBlock createMainBlock(long number, byte[] coinbase, byte[] data, List<Transaction> transactions,
            List<TransactionResult> results) {
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(transactions);
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(results);
        long timestamp = TimeUtils.currentTimeMillis();

        BlockHeader header = BlockUtils.createProofOfWorkHeader(prevHash, number, coinbase, timestamp, transactionsRoot, resultsRoot, 0L, data);
        List<Bytes32> txHashs = new ArrayList<>();
        transactions.forEach(t-> txHashs.add(Bytes32.wrap(t.getHash())));
        return new MainBlock(header, transactions, txHashs, results);
    }

}
