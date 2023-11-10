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

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static io.xdag.utils.WalletUtils.WALLET_PASSWORD_PROMPT;
import static java.lang.System.setErr;
import static java.lang.System.setOut;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;

import io.xdag.Network;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.core.BlockHeader;
import io.xdag.core.DagchainImpl;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.rules.TemporaryDatabaseRule;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;

public class XdagCliTest {

    @Rule
    public TemporaryDatabaseRule temporaryDBFactory = new TemporaryDatabaseRule();

    private Config config;
    private DagchainImpl chain;
    private TransactionResult res;

    private final byte[] coinbase = BytesUtils.random(30);
    private byte[] prevHash;

    private final Network network = Network.DEVNET;
    private final KeyPair key = SampleKeys.KEY1;
    private final byte[] from = Keys.toBytesAddress(key);
    private final byte[] to = BytesUtils.random(20);
    private final XAmount value = XAmount.of(20);
    private final XAmount fee = XAmount.of(1);
    private final long nonce = 12345;
    private final byte[] data = BytesUtils.of("test");
    private final long timestamp = TimeUtils.currentTimeMillis() - 60 * 1000;
    private final Transaction tx = new Transaction(network, TransactionType.TRANSFER, to, value, fee, nonce, timestamp,
            data).sign(key);

    private PendingManager pendingMgr;

    @Before
    public void setUp() throws Exception {
        config = new DevnetConfig(Constants.DEFAULT_ROOT_DIR);
        pendingMgr = Mockito.mock(PendingManager.class);
        when(pendingMgr.getPendingTransactions()).thenReturn(
                org.assertj.core.util.Lists.newArrayList(new PendingManager.PendingTransaction(tx, res)));
        chain = new DagchainImpl(config, pendingMgr, temporaryDBFactory);
        res = new TransactionResult();
        prevHash = chain.getLatestMainBlockHash();
    }

    @Test
    public void testMain() throws Exception {
        String[] args = {"arg1", "arg2"};
        XdagCli cli = mock(XdagCli.class);
        XdagCli.main(args, cli);
        verify(cli).start(args);
    }

    @Test
    public void testHelp() throws Exception {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setOut(new PrintStream(captureOutputStream,true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.start(new String[]{"--help"});

        String helpStr = """
                usage: ./xdag.sh [options]
                    --account <action>              init|create|list
                    --changepassword                change wallet password
                    --checkdbaccount                check database account
                    --convertoldwallet <filename>   convert xdag old wallet.dat to private key hex
                    --dumpprivatekey <address>      print hex key
                    --exportsnapshot                export snapshot
                    --help                          print help
                    --importmnemonic <mnemonic>     import HDWallet mnemonic
                    --importprivatekey <key>        import hex key
                    --password <password>           wallet password
                    --version                       show version
                """;
        assertEquals(helpStr, tapSystemOut(xdagCLI::printHelp));
    }

    @Test
    public void testVersion() throws Exception {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setOut(new PrintStream(captureOutputStream, true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.start(new String[]{"--version"});
        assertEquals(Constants.CLIENT_VERSION + "\n", tapSystemOut(xdagCLI::printVersion));
    }

    @Test
    public void testMainnet() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(new MainnetConfig(Constants.DEFAULT_ROOT_DIR));

        // mock accounts
        List<KeyPair> accounts = Lists.newArrayList();
        KeyPair account = Keys.createEcKeyPair();
        accounts.add(account);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.getAccounts()).thenReturn(accounts);
        when(wallet.exists()).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(any());
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        xdagCLI.start(new String[]{""});
        assertTrue(xdagCLI.getConfig() instanceof MainnetConfig);
    }

    @Test
    public void testTestnet() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(new TestnetConfig(Constants.DEFAULT_ROOT_DIR));

        // mock accounts
        List<KeyPair> accounts = Lists.newArrayList();
        KeyPair account = Keys.createEcKeyPair();
        accounts.add(account);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.getAccounts()).thenReturn(accounts);
        when(wallet.exists()).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        xdagCLI.start(new String[]{"-t"});
        assertTrue(xdagCLI.getConfig() instanceof TestnetConfig);
    }

    @Test
    public void testDevnet() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(new DevnetConfig(Constants.DEFAULT_ROOT_DIR));

        // mock accounts
        List<KeyPair> accounts = Lists.newArrayList();
        KeyPair account = Keys.createEcKeyPair();
        accounts.add(account);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.getAccounts()).thenReturn(accounts);
        when(wallet.exists()).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        xdagCLI.start(new String[]{"-d"});
        assertTrue(xdagCLI.getConfig() instanceof DevnetConfig);
    }

    @Test
    public void testLoadAndUnlockWalletWithWrongPassword() {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock(any())).thenReturn(false);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock password
        when(xdagCLI.getPassword()).thenReturn("password");

    }

    @Test
    public void testStartKernelWithEmptyWallet() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.exists()).thenReturn(false);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        doReturn(new ArrayList<KeyPair>(), // returns empty wallet
                Collections.singletonList(Keys.createEcKeyPair()) // returns wallet with a newly created account
        ).when(wallet).getAccounts();
        when(wallet.addAccount(any(KeyPair.class))).thenReturn(true);
        when(wallet.flush()).thenReturn(true);
        when(wallet.isHdWalletInitialized()).thenReturn(true);

        // mock CLI
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // mock new account
        KeyPair newAccount = Keys.createEcKeyPair();
        when(wallet.addAccountRandom()).thenReturn(newAccount);
        when(wallet.addAccountWithNextHdKey()).thenReturn(newAccount);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn("oldpassword").when(xdagCLI).readNewPassword(any(), any());
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.start();

        // verifies that a new account is added the empty wallet
        verify(wallet).unlock("oldpassword");
        verify(wallet, times(1)).getAccounts();
        verify(wallet).addAccountWithNextHdKey();

        verify(wallet, atLeastOnce()).flush();

        // verifies that kernel starts
        verify(xdagCLI).startKernel(any(), any());
    }

    @Test
    public void testStartKernelWithEmptyWalletInvalidNewPassword() throws Exception {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setErr(new PrintStream(captureOutputStream, true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.exists()).thenReturn(false);

        // mock CLI
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // mock password
        doReturn("a").doReturn("b").when(xdagCLI).readPassword(any());

        // execution
        assertEquals(-1, catchSystemExit(xdagCLI::start));
    }

    @Test
    public void testAccountInit() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        Mockito.doNothing().when(xdagCLI).initHDAccount();
        xdagCLI.start(new String[]{"--account", "init"});
        verify(xdagCLI).initHDAccount();
    }

    @Test
    public void testAccountCreate() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        Mockito.doNothing().when(xdagCLI).createAccount();
        xdagCLI.start(new String[]{"--account", "create"});
        verify(xdagCLI).createAccount();
    }

    @Test
    public void testAccountList() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        Mockito.doNothing().when(xdagCLI).listAccounts();
        xdagCLI.start(new String[]{"--account", "list"});
        verify(xdagCLI).listAccounts();
    }

    @Test
    public void testCreateAccount() throws Exception {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setOut(new PrintStream(captureOutputStream, true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.isHdWalletInitialized()).thenReturn(true);
        when(wallet.addAccount(any(KeyPair.class))).thenReturn(true);
        when(wallet.flush()).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock account
        KeyPair newAccount = Keys.createEcKeyPair();
        when(wallet.addAccountRandom()).thenReturn(newAccount);
        when(wallet.addAccountWithNextHdKey()).thenReturn(newAccount);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.createAccount();

        // verification
        verify(wallet).addAccountWithNextHdKey();

        verify(wallet).flush();
    }

    @Test
    public void testListAccounts() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock accounts
        List<KeyPair> accounts = Lists.newArrayList();
        KeyPair account = Keys.createEcKeyPair();
        accounts.add(account);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.getAccounts()).thenReturn(accounts);
        when(xdagCLI.loadWallet()).thenReturn(wallet);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.listAccounts();

        // verification
        verify(wallet).getAccounts();
    }

    @Test
    public void testChangePasswordIncorrectConfirmation() {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setErr(new PrintStream(captureOutputStream, true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(wallet.flush()).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.isHdWalletInitialized()).thenReturn(true);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn("a").doReturn("b").when(xdagCLI).readPassword(any());
        doNothing().when(xdagCLI).exit(anyInt());

        // execution
        xdagCLI.changePassword();
    }

    @Test
    public void testDumpPrivateKey() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock account
        KeyPair account = spy(Keys.createEcKeyPair());
        String address = BytesUtils.toHexString(Keys.toBytesAddress(account));
        byte[] addressBytes = Keys.toBytesAddress(account);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.getAccount(addressBytes)).thenReturn(account);
        when(wallet.isHdWalletInitialized()).thenReturn(true);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.dumpPrivateKey(address);

        // verification
        verify(wallet).getAccount(addressBytes);
        verify(account).getPrivateKey();
    }

    @Test
    public void testDumpPrivateKeyNotFound() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);
        // mock address
        String address = "c583b6ad1d1cccfc00ae9113db6408f022822b20";

        byte[] addressBytes = BytesUtils.hexStringToBytes(address);

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.getAccount(addressBytes)).thenReturn(null);
        when(wallet.isHdWalletInitialized()).thenReturn(true);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.dumpPrivateKey(address);
    }

    @Test
    public void testImportPrivateKeyExisted() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock private key
        KeyPair keypair = Keys.createEcKeyPair();
        String key = BytesUtils.toHexString(Keys.toBytesAddress(keypair));

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.addAccount(any(KeyPair.class))).thenReturn(false);
        when(wallet.isHdWalletInitialized()).thenReturn(true);
        when(wallet.exists()).thenReturn(true);
        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.importPrivateKey(key);
    }

    @Test
    public void testImportPrivateKeyFailedToFlushWalletFile() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock private key
        KeyPair keypair = Keys.createEcKeyPair();
        String key = BytesUtils.toHexString(Keys.toBytesAddress(keypair));

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.addAccount(any(KeyPair.class))).thenReturn(true);
        when(wallet.flush()).thenReturn(false);
        when(wallet.isHdWalletInitialized()).thenReturn(true);
        when(wallet.exists()).thenReturn(true);
        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.importPrivateKey(key);
    }

    @Test
    public void testImportPrivateKey() throws Exception {
        ByteArrayOutputStream captureOutputStream = new ByteArrayOutputStream();
        setOut(new PrintStream(captureOutputStream, true, Charset.defaultCharset()));
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock private key
        final String key = "99136d0be3f8cf02f024e86542491d276d518b9813997b8e4585e652d119fbca";

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.addAccount(any(KeyPair.class))).thenReturn(true);
        when(wallet.flush()).thenReturn(true);
        when(wallet.isHdWalletInitialized()).thenReturn(true);

        when(wallet.exists()).thenReturn(false);
        //when(xdagCLI.readPassword())
        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readNewPassword("EnterNewPassword:", "ReEnterNewPassword:");
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        xdagCLI.importPrivateKey(key);
    }

    @Test
    public void testImportMnemonic() throws Exception {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        // mock private key
        final String errorMnemonic = "aaa bbb ccc";
        final String rightMnemonic = "view cycle bag maple hill famous black doll episode fine congress april";

        // mock wallet
        Wallet wallet = mock(Wallet.class);
        when(wallet.unlock("oldpassword")).thenReturn(true);
        when(xdagCLI.loadWallet()).thenReturn(wallet);
        when(wallet.flush()).thenReturn(true);

        // mock passwords
        doReturn("oldpassword").when(xdagCLI).readNewPassword("EnterNewPassword:", "ReEnterNewPassword:");
        doReturn("oldpassword").when(xdagCLI).readPassword(WALLET_PASSWORD_PROMPT);
        doReturn(null).when(xdagCLI).startKernel(any(), any());

        // execution
        assertFalse(xdagCLI.importMnemonic(errorMnemonic));
        assertTrue(xdagCLI.importMnemonic(rightMnemonic));
    }

    @Test
    public void testExportSnapshot() throws IOException {
        XdagCli xdagCLI = spy(new XdagCli());
        xdagCLI.setConfig(config);

        MainBlock newBlock = createMainBlock(1);
        BlockState bsTrack = chain.getLatestBlockState().clone();
        chain.addMainBlock(newBlock, bsTrack);
        bsTrack.commit();

        when(xdagCLI.getDefaultDatabaseFactory(config)).thenReturn(temporaryDBFactory);
        File file = xdagCLI.exportSnapshot();
        assertNotNull(file);
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