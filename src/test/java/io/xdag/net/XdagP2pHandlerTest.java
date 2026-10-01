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
package io.xdag.net;

import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.xdag.Network;
import io.xdag.consensus.SyncManager;
import io.xdag.consensus.XdagSync;
import io.xdag.core.Block;
import io.xdag.core.ChainHarness;
import io.xdag.core.XdagStats;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.net.message.consensus.BlocksRequestMessage;
import io.xdag.net.message.consensus.NewBlockMessage;
import io.xdag.net.message.consensus.SumRequestMessage;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The XDAG protocol handler against a peer that does not play by the rules.
 */
public class XdagP2pHandlerTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    private ChainHarness h;
    private ECKeyPair nodeKey;
    private RecordingTransport transport;
    private Channel channel;
    private XdagP2pHandler handler;

    /** A transport that records what is sent instead of writing to a socket. */
    static final class RecordingTransport extends io.xdag.p2p.channel.Channel {
        final List<Bytes> sent = new ArrayList<>();

        RecordingTransport() {
            super(null);
            setInetSocketAddress(new InetSocketAddress("127.0.0.1", 12345));
            setInetAddress(getInetSocketAddress().getAddress());
        }

        @Override
        public void send(Bytes data) {
            sent.add(data);
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        List<Byte> sentCodes() {
            List<Byte> codes = new ArrayList<>();
            for (Bytes b : sent) {
                codes.add(b.get(0));
            }
            return codes;
        }
    }

    @Before
    public void setUp() throws Exception {
        nodeKey = ECKeyPair.generate();
        h = ChainHarness.create(root.newFolder(), 0, nodeKey);
        h.kernel.setBlockchain(h.chain);
        h.kernel.setSyncMgr(new SyncManager(h.kernel));
        h.kernel.setSync(new XdagSync(h.kernel));
        transport = new RecordingTransport();
        Peer peer = new Peer(Network.DEVNET, h.config.getNetworkVersion(), "peer", "127.0.0.1", 12345, "test",
                new String[0], 0, false, "tag");
        channel = new Channel(transport, peer, h.kernel);
        handler = channel.getP2pHandler();
    }

    @After
    public void tearDown() {
        h.close();
    }

    private static Bytes wire(Message m) {
        return Bytes.concatenate(Bytes.of(m.getCode().toByte()), Bytes.wrap(m.getBody()));
    }

    @Test
    public void malformedMessageDisconnectsAndBansThePeer() {
        handler.onMessage(Bytes.of(MessageCode.NEW_BLOCK.toByte(), 1, 2, 3));
        assertTrue(handler.getMisbehaviourScore() >= XdagP2pHandler.BAN_SCORE);
        assertFalse(channel.isActive());
    }

    @Test
    public void unknownCodeDisconnectsThePeer() {
        handler.onMessage(Bytes.of((byte) 0x7f, 0));
        assertFalse(channel.isActive());
    }

    @Test
    public void blocksRequestBeyondTheProtocolSpanIsNotAnswered() {
        handler.onMessage(wire(new BlocksRequestMessage(0, REQUEST_BLOCKS_MAX_TIME + 1, h.chain.getXdagStats())));
        assertTrue(transport.sent.isEmpty());
        assertEquals(XdagP2pHandler.SCORE_BAD_REQUEST, handler.getMisbehaviourScore());

        handler.onMessage(wire(new BlocksRequestMessage(ChainHarness.candidateTime(0),
                ChainHarness.candidateTime(0) + REQUEST_BLOCKS_MAX_TIME, h.chain.getXdagStats())));
        assertTrue("a request of the right shape is answered", transport.sentCodes().contains(MessageCode.BLOCKS_REPLY.toByte()));
    }

    @Test
    public void sumsRequestWithImpossibleSpanIsRefusedWithoutHanging() {
        // a span of 2^63 passes a power-of-two test and used to loop forever in the store
        handler.onMessage(wire(new SumRequestMessage(0, Long.MIN_VALUE, h.chain.getXdagStats())));
        assertTrue(transport.sent.isEmpty());
        handler.onMessage(wire(new SumRequestMessage(0, 1L << 20, h.chain.getXdagStats())));
        assertTrue(transport.sentCodes().contains(MessageCode.SUMS_REPLY.toByte()));
        handler.onMessage(wire(new SumRequestMessage(0, 1L << 21, h.chain.getXdagStats())));
        assertEquals("not a power of sixteen times the block span", 1, transport.sent.size());
    }

    @Test
    public void impossibleNetworkStatisticsAreIgnored() {
        XdagStats lies = new XdagStats(BigInteger.ONE, 10, Long.MAX_VALUE / 2, 3, 0);
        long before = h.chain.getXdagStats().getTotalnmain();
        handler.onMessage(wire(new BlocksRequestMessage(0, 1, lies)));
        assertEquals(before, h.chain.getXdagStats().getTotalnmain());
        assertTrue(handler.getMisbehaviourScore() > 0);
    }

    @Test
    public void validBlockIsImported() {
        Block b = h.candidate(0, nodeKey, "one");
        handler.onMessage(wire(new NewBlockMessage(b, 5)));
        assertNotNull(h.chain.getBlockByHash(b.getHashLow(), false));
        assertEquals(0, handler.getMisbehaviourScore());
    }

    @Test
    public void invalidBlockCountsAgainstThePeer() {
        // a transfer whose signature was damaged: no node can ever accept it, whatever its state
        ECKeyPair other = ECKeyPair.generate();
        Block b = h.transfer(ChainHarness.timeIn(0, 10), nodeKey, other, io.xdag.core.XAmount.of(1, io.xdag.core.XUnit.XDAG), 1);
        byte[] data = b.toBytes();
        io.xdag.core.XdagBlock parsed = new io.xdag.core.XdagBlock(data);
        int signatureField = -1;
        for (int i = 0; i < io.xdag.core.XdagBlock.XDAG_BLOCK_FIELDS; i++) {
            if (parsed.getField(i).getType() == io.xdag.core.XdagField.FieldType.XDAG_FIELD_SIGN_OUT) {
                signatureField = i;
                break;
            }
        }
        assertTrue(signatureField > 0);
        data[signatureField * 32 + 5] ^= 1;
        Block damaged = new Block(new io.xdag.core.XdagBlock(data));
        handler.onMessage(wire(new NewBlockMessage(damaged, 5)));
        assertTrue(handler.getMisbehaviourScore() > 0);
        assertEquals(null, h.chain.getBlockByHash(damaged.getHashLow(), false));
    }
}
