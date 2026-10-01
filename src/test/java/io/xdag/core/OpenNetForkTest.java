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

import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.OPEN_NET_FORK_NOT_SCHEDULED;
import static io.xdag.core.ChainHarness.candidateTime;
import static io.xdag.core.ChainHarness.timeIn;
import static io.xdag.core.ImportResult.IMPORTED_BEST;
import static io.xdag.core.ImportResult.IMPORTED_NOT_BEST;
import static io.xdag.core.ImportResult.INVALID_BLOCK;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_COINBASE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_INPUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static io.xdag.utils.BasicUtils.keyPair2Hash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.utils.XdagTime;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The open-network hardening fork, rule by rule. Every hole the fork closes is shown twice: the legacy rules
 * (what xdagj 0.8.x does, and what is kept for the history before the fork) and the hardened rules.
 */
public class OpenNetForkTest {

    private static final long LEGACY = OPEN_NET_FORK_NOT_SCHEDULED;
    private static final long HARDENED = 0;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ECKeyPair miner = ECKeyPair.generate();
    private final ECKeyPair attacker = ECKeyPair.generate();
    private final ECKeyPair alice = ECKeyPair.generate();
    private final ECKeyPair bob = ECKeyPair.generate();

    private final List<ChainHarness> open = new ArrayList<>();

    private ChainHarness node(long forkEpoch) throws Exception {
        ChainHarness h = ChainHarness.create(tmp.newFolder(), forkEpoch, miner);
        open.add(h);
        return h;
    }

    @After
    public void tearDown() {
        open.forEach(ChainHarness::close);
    }

    /** Candidates for epochs from..to, each linking its predecessor; returns them in order. */
    private List<Block> mine(ChainHarness h, Block parent, int from, int to) {
        List<Block> blocks = new ArrayList<>();
        Block prev = parent;
        for (int e = from; e <= to; e++) {
            Block c = prev == null ? h.candidate(e, miner, "m") : h.candidate(e, miner, "m", prev.getHashLow());
            assertSame("candidate of epoch " + e, IMPORTED_BEST, h.add(c));
            blocks.add(c);
            prev = c;
        }
        return blocks;
    }

    private static XAmount xdag(long n) {
        return XAmount.of(n, XUnit.XDAG);
    }

    // ------------------------------------------------------------------------------------------------------
    // C1: only proof of work gives a block a difficulty of its own
    // ------------------------------------------------------------------------------------------------------

    @Test
    public void legacyRulesLetAnOrdinaryBlockTakeTheTop() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 3);
        // not mined: a block in the middle of epoch 4 that merely refers to the top
        Block link = h.link(timeIn(4, 100), attacker, null, chain.get(2).getHashLow());
        assertSame(IMPORTED_BEST, h.add(link));
        assertTrue(h.chain.getBlockByHash(link.getHashLow(), false).getInfo().getDifficulty()
                .compareTo(h.chain.getBlockByHash(chain.get(2).getHashLow(), false).getInfo().getDifficulty()) > 0);
    }

    @Test
    public void hardenedRulesGiveAnOrdinaryBlockNoDifficulty() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 3);
        BigInteger topDiff = h.chain.getXdagTopStatus().getTopDiff();

        Block link = h.link(timeIn(4, 100), attacker, null, chain.get(2).getHashLow());
        assertSame(IMPORTED_NOT_BEST, h.add(link));
        assertEquals(topDiff, h.chain.getXdagTopStatus().getTopDiff());
        assertEquals(chain.get(2).getHashLow(), Bytes32.wrap(h.chain.getXdagTopStatus().getTop()));
        // it inherits the weight of what it refers to and adds nothing
        assertEquals(topDiff, h.chain.getBlockByHash(link.getHashLow(), false).getInfo().getDifficulty());
        // a block that refers to nothing weighs nothing
        Block lonely = h.link(timeIn(4, 200), attacker, "x");
        assertSame(IMPORTED_NOT_BEST, h.add(lonely));
        assertEquals(BigInteger.ZERO, h.chain.getBlockByHash(lonely.getHashLow(), false).getInfo().getDifficulty());
    }

    /**
     * Grind an ordinary block attached to the first main block until it alone outweighs everything mined since.
     * With sha256 hardware this is cheap on the real network; here the chain is short so a few tries do.
     */
    private Block grindAgainst(ChainHarness h, Block base, int epoch, BigInteger toBeat) {
        BigInteger baseDiff = h.chain.getBlockByHash(base.getHashLow(), false).getInfo().getDifficulty();
        for (int i = 0; i < 200_000; i++) {
            Block b = h.link(timeIn(epoch, 1 + (i % 60000)), attacker, "g" + i, base.getHashLow());
            if (baseDiff.add(h.chain.getDiffByRawHash(b.getHash())).compareTo(toBeat) > 0) {
                return b;
            }
        }
        throw new AssertionError("could not grind a heavy enough block");
    }

    @Test
    public void legacyRulesLetAGroundBlockUnwindTheChain() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 6);
        h.settle();
        long mainBefore = h.chain.getXdagStats().nmain;
        assertTrue(mainBefore >= 4);

        Block ground = grindAgainst(h, chain.get(0), 2, h.chain.getXdagTopStatus().getTopDiff());
        assertSame(IMPORTED_BEST, h.add(ground));
        // everything after the first block is no longer on the main chain
        assertTrue(h.chain.getXdagStats().nmain < mainBefore);
        assertEquals(0, h.flags(chain.get(3)) & BI_MAIN);
    }

    @Test
    public void hardenedRulesIgnoreAGroundBlock() throws Exception {
        ChainHarness legacy = node(LEGACY);
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 6);
        for (Block b : chain) {
            legacy.add(b);
        }
        h.settle();
        long mainBefore = h.chain.getXdagStats().nmain;
        byte[] topBefore = h.chain.getXdagTopStatus().getTop();

        // the very block that unwinds the chain of a legacy node
        Block ground = grindAgainst(legacy, chain.get(0), 2, legacy.chain.getXdagTopStatus().getTopDiff());
        assertSame(IMPORTED_BEST, legacy.add(ground));
        assertSame(IMPORTED_NOT_BEST, h.add(ground));
        h.settle();
        assertEquals(mainBefore, h.chain.getXdagStats().nmain);
        assertEquals(Bytes32.wrap(topBefore), Bytes32.wrap(h.chain.getXdagTopStatus().getTop()));
        assertNotEquals(0, h.flags(chain.get(3)) & BI_MAIN);
    }

    /** A transfer of a block balance that also refers to the top block. */
    private Block spendAndLinkTop(ChainHarness h, long time, Block source, Block top) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(source.getHashLow(), XDAG_FIELD_IN, xdag(10), false));
        refs.add(new Address(keyPair2Hash(alice), XDAG_FIELD_OUTPUT, xdag(10), true));
        refs.add(new Address(top.getHashLow(), XDAG_FIELD_OUT, false));
        Block b = new Block(h.config, time, refs, null, false, Lists.newArrayList(miner), null, 0,
                XAmount.ZERO, null);
        b.signOut(miner);
        return b;
    }

    @Test
    public void legacyRulesLetATransactionBecomeTheTop() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 3);
        // nobody mined epoch 4; a transaction that refers to the top weighs "top + 1"
        assertSame(IMPORTED_BEST, h.add(spendAndLinkTop(h, timeIn(4, 5), chain.get(0), chain.get(2))));
    }

    @Test
    public void hardenedRulesNeverMakeATransactionTheTop() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 3);
        Block tx = spendAndLinkTop(h, timeIn(4, 5), chain.get(0), chain.get(2));
        assertSame(IMPORTED_NOT_BEST, h.add(tx));
        assertEquals(chain.get(2).getHashLow(), Bytes32.wrap(h.chain.getXdagTopStatus().getTop()));
        // ... and it is still an ordinary transaction: a later main block executes it
        Block c4 = h.candidate(5, miner, "m", chain.get(2).getHashLow(), tx.getHashLow());
        assertSame(IMPORTED_BEST, h.add(c4));
        mine(h, c4, 6, 7);
        h.settle();
        assertNotEquals(0, h.flags(tx) & BI_APPLIED);
        assertEquals(xdag(10).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(alice));
    }

    // ------------------------------------------------------------------------------------------------------
    // C2: the same block spent twice in one transaction
    // ------------------------------------------------------------------------------------------------------

    /** Spends 600 + 600 from the first main block (which holds its 1024 XDAG reward) in a single transaction. */
    private Block doubleSpend(ChainHarness h, Block source, long time) {
        return h.spendBlocks(time, miner, alice,
                List.of(source.getHashLow(), source.getHashLow()), List.of(xdag(600), xdag(600)));
    }

    private Block confirm(ChainHarness h, Block top, int epoch, Block... txs) {
        Bytes32[] links = new Bytes32[txs.length + 1];
        links[0] = top.getHashLow();
        for (int i = 0; i < txs.length; i++) {
            links[i + 1] = txs[i].getHashLow();
        }
        Block main = h.candidate(epoch, miner, "m", links);
        assertSame(IMPORTED_BEST, h.add(main));
        mine(h, main, epoch + 1, epoch + 2);
        h.settle();
        assertNotEquals("main block of epoch " + epoch + " confirmed", 0, h.flags(main) & BI_MAIN);
        return main;
    }

    @Test
    public void legacyRulesMintMoneyFromADuplicateInput() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        assertEquals(xdag(1024), h.blockBalance(chain.get(0)));

        Block tx = doubleSpend(h, chain.get(0), timeIn(5, 10));
        assertSame(IMPORTED_NOT_BEST, h.add(tx));
        confirm(h, chain.get(3), 5, tx);

        // each link was compared with the untouched balance: 1024 - 600 - 600
        assertEquals(xdag(-176), h.blockBalance(chain.get(0)));
        assertEquals(xdag(1200).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(alice));
    }

    @Test
    public void hardenedRulesRejectADuplicateInputThatOverdraws() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();

        Block tx = doubleSpend(h, chain.get(0), timeIn(5, 10));
        assertSame(IMPORTED_NOT_BEST, h.add(tx));
        confirm(h, chain.get(3), 5, tx);

        assertEquals(xdag(1024), h.blockBalance(chain.get(0)));
        assertEquals(XAmount.ZERO, h.balance(alice));
        assertNotEquals(0, h.flags(tx) & BI_MAIN_REF);
        assertEquals("rejected", 0, h.flags(tx) & BI_APPLIED);
    }

    @Test
    public void hardenedRulesStillAllowTwoInputsThatFit() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();

        Block tx = h.spendBlocks(timeIn(5, 10), miner, alice,
                List.of(chain.get(0).getHashLow(), chain.get(0).getHashLow()), List.of(xdag(500), xdag(524)));
        assertSame(IMPORTED_NOT_BEST, h.add(tx));
        confirm(h, chain.get(3), 5, tx);

        assertEquals(XAmount.ZERO, h.blockBalance(chain.get(0)));
        assertEquals(xdag(1024).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(alice));
    }

    // ------------------------------------------------------------------------------------------------------
    // C4 and friends: nothing a block contains may abort the confirmation of a main block half-way
    // ------------------------------------------------------------------------------------------------------

    /** alice gets 100 XDAG from the first main block; returns the chain tip afterwards. */
    private Block fundAlice(ChainHarness h, List<Block> chain, int epoch) {
        Block fund = h.spendBlocks(timeIn(epoch, 10), miner, alice, List.of(chain.get(0).getHashLow()), List.of(xdag(100)));
        assertSame(IMPORTED_NOT_BEST, h.add(fund));
        Block main = h.candidate(epoch, miner, "m", chain.get(chain.size() - 1).getHashLow(), fund.getHashLow());
        assertSame(IMPORTED_BEST, h.add(main));
        List<Block> more = mine(h, main, epoch + 1, epoch + 2);
        h.settle();
        assertEquals(xdag(100).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(alice));
        return more.get(1);
    }

    /** An account transaction whose five outputs add up to more than fits into an amount. */
    private Block overflowingTransfer(ChainHarness h, long time) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(keyPair2Hash(alice), XDAG_FIELD_INPUT, xdag(1), true));
        for (int i = 0; i < 5; i++) {
            refs.add(new Address(keyPair2Hash(bob), XDAG_FIELD_OUTPUT, xdag(2_000_000_000L), true));
        }
        Block b = new Block(h.config, time, refs, null, false, Lists.newArrayList(alice), null, 0,
                XAmount.ZERO, UInt64.ONE);
        b.signOut(alice);
        return b;
    }

    @Test
    public void legacyRulesAbortTheMainBlockOnOverflowingAmounts() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        Block tip = fundAlice(h, chain, 5);

        Block bad = overflowingTransfer(h, timeIn(8, 10));
        assertSame(IMPORTED_NOT_BEST, h.add(bad));
        Block main = h.candidate(8, miner, "m", tip.getHashLow(), bad.getHashLow());
        assertSame(IMPORTED_BEST, h.add(main));
        Block next = h.candidate(9, miner, "m", main.getHashLow());
        assertSame(IMPORTED_BEST, h.add(next));
        long mainBefore = h.chain.getXdagStats().nmain;
        // The next block to arrive triggers the confirmation of the main block of epoch 8. That blows up in the
        // middle, and the block that happened to be arriving - an innocent one - is what gets refused.
        Block innocent = h.candidate(10, miner, "m", next.getHashLow());
        assertSame(ImportResult.ERROR, h.add(innocent));
        assertTrue(ImportResult.ERROR.getErrorInfo(), ImportResult.ERROR.getErrorInfo().startsWith("ArithmeticException"));
        assertNull(h.chain.getBlockByHash(innocent.getHashLow(), false));
        // ... and the main block is left half confirmed: counted and rewarded, but without its fee and marker
        assertEquals(mainBefore + 1, h.chain.getXdagStats().nmain);
        assertNotEquals(0, h.flags(main) & BI_MAIN);
        assertNull(h.chain.getBlockByHash(main.getHashLow(), false).getInfo().getRef());
    }

    @Test
    public void hardenedRulesRejectOverflowingAmountsAndCarryOn() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        Block tip = fundAlice(h, chain, 5);

        Block bad = overflowingTransfer(h, timeIn(8, 10));
        assertSame(IMPORTED_NOT_BEST, h.add(bad));
        // a good transaction right behind the bad one in the same main block
        Block good = h.spendBlocks(timeIn(8, 20), miner, bob, List.of(chain.get(0).getHashLow()), List.of(xdag(50)));
        assertSame(IMPORTED_NOT_BEST, h.add(good));
        Block main = confirm(h, tip, 8, bad, good);

        assertEquals("rejected", 0, h.flags(bad) & BI_APPLIED);
        assertNotEquals(0, h.flags(bad) & BI_MAIN_REF);
        // rejected like any transaction whose amounts do not match: the nonce is used up, nothing moved
        assertEquals(1, h.kernel.getAddressStore().getExecutedNonceNum(alice.toAddress().toArray()).toLong());
        assertEquals(xdag(100).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(alice));
        // the rest of the main block was executed
        assertNotEquals(0, h.flags(good) & BI_APPLIED);
        assertEquals(xdag(50).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(bob));
        assertEquals(xdag(1024).add(XAmount.of(100, XUnit.MILLI_XDAG)), h.blockBalance(main));
    }

    /** A block without inputs whose coinbase field carries an amount. */
    private Block linkWithAmount(ChainHarness h, long time, Bytes32... links) {
        List<Address> pending = Lists.newArrayList();
        for (Bytes32 link : links) {
            pending.add(new Address(link, XDAG_FIELD_OUT, false));
        }
        pending.add(new Address(keyPair2Hash(attacker), XDAG_FIELD_COINBASE, xdag(5), true));
        Block b = new Block(h.config, time, null, pending, false, null, null, -1, XAmount.ZERO, null);
        b.signOut(attacker);
        return b;
    }

    @Test
    public void legacyRulesAbortTheMainBlockOnALinkBlockWithAnAmount() throws Exception {
        ChainHarness h = node(LEGACY);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        Block poison = linkWithAmount(h, timeIn(5, 10));
        ImportResult imported = h.add(poison);
        assertTrue(imported == IMPORTED_NOT_BEST || imported == IMPORTED_BEST);
        Block top = h.chain.getBlockByHash(Bytes32.wrap(h.chain.getXdagTopStatus().getTop()), false);
        Block main = h.candidate(5, miner, "m", top.getHashLow(), poison.getHashLow());
        h.add(main);
        Block next = h.candidate(6, miner, "m", main.getHashLow());
        h.add(next);
        Block innocent = h.candidate(7, miner, "m", next.getHashLow());
        assertSame(ImportResult.ERROR, h.add(innocent));
        assertTrue(ImportResult.ERROR.getErrorInfo(), ImportResult.ERROR.getErrorInfo().contains("IndexOutOfBounds"));
        assertNull(h.chain.getBlockByHash(main.getHashLow(), false).getInfo().getRef());
    }

    @Test
    public void hardenedRulesTreatALinkBlockWithAnAmountAsALinkBlock() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        // the poisoned block wraps a good transaction: its fee must still reach the main block
        Block good = h.spendBlocks(timeIn(5, 5), miner, bob, List.of(chain.get(0).getHashLow()), List.of(xdag(50)));
        assertSame(IMPORTED_NOT_BEST, h.add(good));
        Block poison = linkWithAmount(h, timeIn(5, 10), good.getHashLow());
        assertSame(IMPORTED_NOT_BEST, h.add(poison));
        Block main = confirm(h, chain.get(3), 5, poison);

        assertNotEquals(0, h.flags(poison) & BI_APPLIED);
        assertNotEquals(0, h.flags(good) & BI_APPLIED);
        assertEquals(XAmount.ZERO, h.balance(attacker));
        assertEquals(xdag(50).subtract(XAmount.of(100, XUnit.MILLI_XDAG)), h.balance(bob));
        assertEquals(xdag(1024).add(XAmount.of(100, XUnit.MILLI_XDAG)), h.blockBalance(main));
    }

    // ------------------------------------------------------------------------------------------------------
    // C6: whether a block is valid must not depend on what this node has executed so far
    // ------------------------------------------------------------------------------------------------------

    @Test
    public void legacyRulesRefuseATransferFromAnAccountWithoutARecord() throws Exception {
        ChainHarness h = node(LEGACY);
        mine(h, null, 1, 3);
        assertSame(INVALID_BLOCK, h.add(h.transfer(timeIn(4, 10), alice, bob, xdag(1), 1)));
        assertFalse("the sender may be honest: another node can have the record", INVALID_BLOCK.isMisbehavior());
    }

    @Test
    public void hardenedRulesAcceptTheTransferAndLetExecutionDecide() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();

        // the spend arrives before the funding has been executed (or even seen)
        Block spend = h.transfer(timeIn(5, 20), alice, bob, xdag(40), 1);
        assertSame(IMPORTED_NOT_BEST, h.add(spend));
        Block fund = h.spendBlocks(timeIn(5, 10), miner, alice, List.of(chain.get(0).getHashLow()), List.of(xdag(100)));
        assertSame(IMPORTED_NOT_BEST, h.add(fund));
        // the main block lists the funding first
        confirm(h, chain.get(3), 5, fund, spend);

        assertNotEquals(0, h.flags(fund) & BI_APPLIED);
        assertNotEquals(0, h.flags(spend) & BI_APPLIED);
        XAmount fee = XAmount.of(100, XUnit.MILLI_XDAG);
        assertEquals(xdag(100).subtract(fee).subtract(xdag(40)), h.balance(alice));
        assertEquals(xdag(40).subtract(fee), h.balance(bob));
    }

    @Test
    public void hardenedRulesRejectAtExecutionWhenTheAccountHasNoFunds() throws Exception {
        ChainHarness h = node(HARDENED);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();
        Block spend = h.transfer(timeIn(5, 20), alice, bob, xdag(40), 1);
        assertSame(IMPORTED_NOT_BEST, h.add(spend));
        confirm(h, chain.get(3), 5, spend);
        assertEquals(0, h.flags(spend) & BI_APPLIED);
        assertEquals(XAmount.ZERO, h.balance(bob));
    }

    // ------------------------------------------------------------------------------------------------------
    // import: nothing is changed before the block has been checked completely
    // ------------------------------------------------------------------------------------------------------

    @Test
    public void aBlockWithoutOutputSignatureIsRefusedBeforeItTouchesAnything() throws Exception {
        for (long rules : new long[]{LEGACY, HARDENED}) {
            ChainHarness h = node(rules);
            List<Block> chain = mine(h, null, 1, 3);
            Block waiting = h.link(timeIn(4, 1), alice, "waiting");
            h.add(waiting);
            int flagsBefore = h.flags(waiting);
            Bytes32 topBefore = Bytes32.wrap(h.chain.getXdagTopStatus().getTop());
            long blocksBefore = h.chain.getXdagStats().nblocks;

            // refers to the waiting block but is not signed at all
            List<Address> pending = Lists.newArrayList(new Address(waiting.getHashLow(), XDAG_FIELD_OUT, false));
            Block unsigned = new Block(h.config, timeIn(4, 50), null, pending, false, null, null, -1, XAmount.ZERO, null);
            byte[] raw = unsigned.getXdagBlock().getData().toArray();
            // the constructor reserved two SIGN_OUT fields; turn them into plain nonce fields (type 0)
            Block stripped = new Block(new XdagBlock(stripSignatureTypes(raw)));
            assertNull(stripped.getOutsig());

            assertSame(INVALID_BLOCK, h.chain.tryToConnect(stripped));
            assertTrue(INVALID_BLOCK.isMisbehavior());
            // the block it referred to was not marked as referred to
            assertEquals(flagsBefore, h.flags(waiting));
            assertEquals(topBefore, Bytes32.wrap(h.chain.getXdagTopStatus().getTop()));
            assertEquals(blocksBefore, h.chain.getXdagStats().nblocks);
            assertEquals(3, chain.size());
        }
    }

    private static byte[] stripSignatureTypes(byte[] raw) {
        byte[] out = raw.clone();
        // field types: 16 nibbles, little endian, at bytes 8..15 of the header
        for (int field = 0; field < 16; field++) {
            int nibble = (out[8 + field / 2] >> ((field % 2) * 4)) & 0xf;
            if (nibble == 5) {
                out[8 + field / 2] &= (byte) ~(0xf << ((field % 2) * 4));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------
    // activation
    // ------------------------------------------------------------------------------------------------------

    @Test
    public void theForkLatchesWithTheFirstMainBlockAtItsEpochAndCoversOldTimestampsAfterwards() throws Exception {
        long forkEpoch = XdagTime.getEpoch(candidateTime(5));
        ChainHarness h = node(forkEpoch);
        assertFalse(h.chain.isOpenNetLatched());

        List<Block> chain = mine(h, null, 1, 3);
        // before the fork an ordinary block still weighs something (legacy rules reproduce history)
        Block legacyLink = h.link(timeIn(4, 100), attacker, null, chain.get(2).getHashLow());
        assertSame(IMPORTED_BEST, h.add(legacyLink));

        // candidates from epoch 5 on are hardened blocks; the chain passes the fork
        List<Block> more = mine(h, legacyLink, 5, 8);
        assertFalse("not latched before a main block at the fork epoch is confirmed", h.chain.isOpenNetLatched()
                && h.chain.getXdagStats().nmain == 0);
        h.settle();
        assertTrue(h.chain.isOpenNetLatched());
        assertTrue(h.kernel.getBlockStore().isOpenNetForkLatched());

        // a block that claims a timestamp from before the fork gets no legacy treatment any more
        Block late = h.link(timeIn(4, 200), attacker, "late", chain.get(2).getHashLow());
        assertSame(IMPORTED_NOT_BEST, h.add(late));
        assertEquals(h.chain.getBlockByHash(chain.get(2).getHashLow(), false).getInfo().getDifficulty(),
                h.chain.getBlockByHash(late.getHashLow(), false).getInfo().getDifficulty());
        assertEquals(more.get(3).getHashLow(), Bytes32.wrap(h.chain.getXdagTopStatus().getTop()));
    }

    @Test
    public void executionRulesFollowTheMainBlockNotTheTransaction() throws Exception {
        long forkEpoch = XdagTime.getEpoch(candidateTime(8));
        ChainHarness h = node(forkEpoch);
        List<Block> chain = mine(h, null, 1, 4);
        h.settle();

        // confirmed by the main block of epoch 5, before the fork: legacy execution, the balance goes negative
        Block early = doubleSpend(h, chain.get(0), timeIn(5, 10));
        h.add(early);
        Block main5 = confirm(h, chain.get(3), 5, early);
        assertEquals(xdag(-176), h.blockBalance(chain.get(0)));

        // a transaction with a timestamp from before the fork, confirmed by a main block after it
        Block late = doubleSpend(h, chain.get(1), timeIn(6, 10));
        h.add(late);
        Block tip = h.chain.getBlockByHash(Bytes32.wrap(h.chain.getXdagTopStatus().getTop()), false);
        confirm(h, tip, 8, late);
        assertTrue(h.chain.isOpenNetLatched());
        assertEquals("hardened execution: rejected", xdag(1024), h.blockBalance(chain.get(1)));
        assertNotEquals(0, h.flags(main5) & BI_MAIN);
    }

    @Test
    public void peerSuppliedExecutionStatusIsDroppedOnceTheForkIsInForce() throws Exception {
        ChainHarness legacy = node(LEGACY);
        ChainHarness h = node(HARDENED);
        Bytes32 hash = Bytes32.random();
        legacy.chain.putSyncTxStatus(hash, (byte) 2);
        h.chain.putSyncTxStatus(hash, (byte) 2);
        assertEquals(Byte.valueOf((byte) 2), legacy.chain.getSyncTxStatus(hash));
        assertNull(h.chain.getSyncTxStatus(hash));
    }
}
