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
package io.xdag.rules;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hyperledger.besu.crypto.KeyPair;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import io.xdag.KernelMock;
import io.xdag.Network;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.UnitTestnetConfig;
import io.xdag.config.spec.NodeSpec;
import io.xdag.core.BlockHeader;
import io.xdag.core.DagchainImpl;
import io.xdag.core.Fork;
import io.xdag.core.Genesis;
import io.xdag.core.MainBlock;
import io.xdag.core.PendingManager;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.db.LeveldbDatabase;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * A kernel rule creates a temporary folder as the data directory. Ten accounts
 * will be created automatically and the first one will be used as coinbase.
 */
@Getter
@Setter
public class KernelRule extends TemporaryFolder {

    private Config config;

    private int port;

    private List<KeyPair> keys;

    private Wallet wallet;

    private KeyPair coinbase;

    private Genesis genesis;

    private String password;

    private KernelMock kernel;

    private LeveldbDatabase.LeveldbFactory dbFactory;

    public KernelRule(int port) {
        super();

        this.port = port;

        this.keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {

            try {
                keys.add(Keys.createEcKeyPair());
            } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new RuntimeException(e);
            }
        }
        this.coinbase = keys.get(0);
    }

    @Override
    protected void before() throws Throwable {
        create();

        // generate random password
        this.password = "password";

        // config
        config = mockConfig(port);

        // genesis
        if (genesis == null) {
            genesis = Genesis.load(config.getNodeSpec().getNetwork());
        }

        // wallet
        wallet = new Wallet(config);
        wallet.unlock(password);
        for (KeyPair key : keys) {
            wallet.addAccount(key);
        }
        wallet.initializeHdWallet("door liar oven degree snap history rotate patch portion toddler ethics sting");
        wallet.flush();

        // kernel
        this.kernel = new KernelMock(config, genesis, wallet);
        this.kernel.setPendingManager(mock(PendingManager.class));
    }

    @Override
    protected void after() {
        kernel.stop();
        delete();
    }

    protected Config mockConfig(int port) {
        Config config = spy(new DevnetConfig(getRoot().getAbsolutePath()));
        NodeSpec nodeSpec = Mockito.mock(NodeSpec.class);
        when(config.getNodeSpec()).thenReturn(nodeSpec);
        when(nodeSpec.getNetwork()).thenReturn(Network.DEVNET);
        when(nodeSpec.getNetMaxInboundConnectionsPerIp()).thenReturn(5);
        when(nodeSpec.getNetMaxInboundConnections()).thenReturn(512);
        when(nodeSpec.getNetMaxFrameBodySize()).thenReturn(128 * 1024);
        when(nodeSpec.getNetMaxPacketSize()).thenReturn(16 * 1024 * 1024);
        when(nodeSpec.getNetMaxMessageQueueSize()).thenReturn(4096);
        when(nodeSpec.getNetHandshakeExpiry()).thenReturn(5 * 60 * 1000);
        when(nodeSpec.getNodeIp()).thenReturn("127.0.0.1");
        when(nodeSpec.getNodePort()).thenReturn(port);

        return config;
    }

    public void enableForks(Fork... forks) {
        Map<Fork, Long> map = new HashMap<>();
        for (Fork fork : forks) {
            map.put(fork, 1L);
        }
        when(config.manuallyActivatedForks()).thenReturn(map);
    }

    /**
     * Opens the database.
     */
    public void openDagchain() {
        dbFactory = new LeveldbDatabase.LeveldbFactory(kernel.getConfig().chainDir());
        DagchainImpl chain = new DagchainImpl(kernel.getConfig(), null, dbFactory);
        kernel.setDagchain(chain);
    }

    /**
     * Closes the database.
     */
    public void closeDagchain() {
        dbFactory.close();
    }

    /**
     * Helper method to create a testing block.
     *
     * @param txs
     *            list of transaction
     * @param lastBlock
     *            last block header
     * @return created block
     */
    public MainBlock createMainBlock(List<Transaction> txs, BlockHeader lastBlock) {
        List<TransactionResult> res = txs.stream().map(tx -> new TransactionResult()).collect(Collectors.toList());

        long number;
        byte[] prevHash;
        if (lastBlock == null) {
            number = getKernel().getDagchain().getLatestMainBlock().getNumber() + 1;
            prevHash = getKernel().getDagchain().getLatestMainBlock().getHash();
        } else {
            number = lastBlock.getNumber() + 1;
            prevHash = lastBlock.getHash();
        }
        KeyPair key = SampleKeys.KEY_PAIR;
        byte[] coinbase = Keys.toBytesAddress(key);
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(txs);
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(res);
        byte[] data = {};

        BlockHeader header = new BlockHeader(
                number,
                coinbase,
                prevHash,
                timestamp,
                transactionsRoot,
                resultsRoot,
                data);

        return new MainBlock(header, txs, res);
    }

    public MainBlock createMainBlock(List<Transaction> txs) {
        return createMainBlock(txs, null);
    }
}
