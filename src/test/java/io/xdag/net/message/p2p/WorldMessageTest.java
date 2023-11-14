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
package io.xdag.net.message.p2p;

import static io.xdag.core.XAmount.ZERO;
import static io.xdag.utils.WalletUtils.toBase58;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.junit.Test;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.DevnetConfig;
import io.xdag.core.BlockHeader;
import io.xdag.core.MainBlock;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.net.CapabilityTreeSet;
import io.xdag.net.Peer;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;

public class WorldMessageTest {

    private Config config = new DevnetConfig(Constants.DEFAULT_ROOT_DIR);
    private long number = 2;
    private byte[] coinbase = BytesUtils.random(20);
    private byte[] prevHash = BytesUtils.random(32);
    private long timestamp = TimeUtils.currentTimeMillis();
    private long nonce = 0;
    private byte[] data = BytesUtils.of("data");

    private Transaction tx = new Transaction(Network.DEVNET, TransactionType.TRANSFER, BytesUtils.random(20), ZERO,
            config.getDagSpec().getMinTransactionFee(),
            1, TimeUtils.currentTimeMillis(), BytesUtils.EMPTY_BYTES).sign(SampleKeys.KEY1);
    private TransactionResult res = new TransactionResult();
    private List<Transaction> transactions = Collections.singletonList(tx);
    private List<TransactionResult> results = Collections.singletonList(res);

    private byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(transactions);
    private byte[] resultsRoot = MerkleUtils.computeResultsRoot(results);

    @Test
    public void testCodec() {
        Config config = new DevnetConfig(Constants.DEFAULT_ROOT_DIR);

        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        String peerId = toBase58(Keys.toBytesAddress(key));
        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot, nonce, data);
        MainBlock mainBlock = new MainBlock(header, transactions, results);
        WorldMessage msg = new WorldMessage(config.getNodeSpec().getNetwork(), config.getNodeSpec().getNetworkVersion(), peerId, 8001,
                config.getClientId(), config.getClientCapabilities().toArray(), mainBlock,
                SecureRandomProvider.publicSecureRandom().generateSeed(InitMessage.SECRET_LENGTH), key);
        assertTrue(msg.validate(config));

        msg = new WorldMessage(msg.getBody());
        assertTrue(msg.validate(config));

        String ip = "127.0.0.2";
        Peer peer = msg.getPeer(ip);
        assertEquals(config.getNodeSpec().getNetwork(), peer.getNetwork());
        assertEquals(config.getNodeSpec().getNetworkVersion(), peer.getNetworkVersion());
        assertEquals(toBase58(Keys.toBytesAddress(key)), peer.getPeerId());
        assertEquals(ip, peer.getIp());
        assertEquals(config.getNodeSpec().getNodePort(), peer.getPort());
        assertEquals(config.getClientId(), peer.getClientId());
        assertEquals(config.getClientCapabilities(), CapabilityTreeSet.of(peer.getCapabilities()));
        assertEquals(2, peer.getLatestMainBlock().getNumber());
    }
}
