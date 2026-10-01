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

import static io.xdag.config.Constants.OPEN_NET_FORK_NOT_SCHEDULED;
import static io.xdag.core.ChainHarness.timeIn;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_COINBASE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_INPUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static io.xdag.utils.BasicUtils.keyPair2Hash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PrivateKey;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Gate G1 of the Node 2.0 roadmap: the same set of blocks gives the same state.
 * <p>
 * A random history is generated - competing main block candidates (so there are reorganisations), transfers
 * between accounts, transfers of block balances, link blocks, and transactions that must fail - and delivered to
 * several nodes in different orders. Every order respects "parents first" and nothing else. One node sees the
 * blocks as they were created; the others see forks in a different order, so they confirm and unwind different
 * main blocks on the way. At the end all of them must agree on every block and every account.
 */
public class DeterminismTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<ChainHarness> open = new ArrayList<>();

    @After
    public void tearDown() {
        open.forEach(ChainHarness::close);
    }

    /** A generated history: blocks in the order they were created. */
    private static class History {
        final List<Block> blocks = new ArrayList<>();
        final Map<Bytes32, List<Bytes32>> parents = new HashMap<>();
        final List<ECKeyPair> accounts = new ArrayList<>();

        void add(Block b) {
            List<Bytes32> refs = new ArrayList<>();
            for (Address a : b.getLinks()) {
                if (!a.getIsAddress()) {
                    refs.add(Bytes32.wrap(a.getAddress()));
                }
            }
            parents.put(Bytes32.wrap(b.getHashLow()), refs);
            blocks.add(b);
        }
    }

    private static ECKeyPair key(long seed, int index) {
        return ECKeyPair.fromPrivateKey(PrivateKey.fromBigInteger(
                BigInteger.valueOf(1_000_003L * seed + index).add(BigInteger.ONE.shiftLeft(200))));
    }

    private static XAmount xdag(long n) {
        return XAmount.of(n, XUnit.XDAG);
    }

    /**
     * @param hostile also generate blocks that the legacy execution cannot digest (amount overflow, block without
     *                inputs but with an amount); only meaningful under the hardened rules
     */
    private History generate(ChainHarness maker, long seed, int epochs, boolean hostile) {
        Random r = new Random(seed);
        History h = new History();
        // keys derived from the seed: signatures are deterministic, so a seed always gives the same history
        ECKeyPair[] miners = {key(seed, 1), key(seed, 2)};
        for (int i = 0; i < 4; i++) {
            h.accounts.add(key(seed, 10 + i));
        }
        long[] nextNonce = new long[h.accounts.size()];
        java.util.Arrays.fill(nextNonce, 1);

        // candidates by epoch, with the key that mined them
        List<List<Block>> candidates = new ArrayList<>();
        Map<Bytes32, ECKeyPair> minedBy = new HashMap<>();
        candidates.add(new ArrayList<>()); // epoch 0: nothing
        List<Block> pending = new ArrayList<>();

        for (int e = 1; e <= epochs; e++) {
            // ---- ordinary blocks during the epoch
            int n = r.nextInt(6);
            int offset = 10;
            for (int j = 0; j < n; j++) {
                offset += 1 + r.nextInt(50);
                long time = timeIn(e, offset);
                int kind = r.nextInt(hostile ? 12 : 10);
                Block b = null;
                if (kind <= 3 && e > 3) {
                    // transfer of block balances: spend one or two candidates of an epoch that is settled by now
                    List<Block> old = candidates.get(1 + r.nextInt(e - 3));
                    if (!old.isEmpty()) {
                        Block source = old.get(r.nextInt(old.size()));
                        ECKeyPair owner = minedBy.get(Bytes32.wrap(source.getHashLow()));
                        long[] choices = {1024, 512, 100, 300, 2000, 1};
                        List<Bytes32> sources = Lists.newArrayList(Bytes32.wrap(source.getHashLow()));
                        List<XAmount> amounts = Lists.newArrayList(xdag(choices[r.nextInt(choices.length)]));
                        if (r.nextInt(4) == 0) {
                            // the same block twice
                            sources.add(Bytes32.wrap(source.getHashLow()));
                            amounts.add(xdag(choices[r.nextInt(choices.length)]));
                        }
                        b = maker.spendBlocks(time, owner, h.accounts.get(r.nextInt(h.accounts.size())), sources, amounts);
                    }
                } else if (kind <= 7) {
                    int from = r.nextInt(h.accounts.size());
                    int to = r.nextInt(h.accounts.size());
                    long nonce = nextNonce[from];
                    int twist = r.nextInt(12);
                    if (twist == 0) {
                        nonce += 1;            // a gap: skipped until ... never
                    } else if (twist == 1 && nonce > 1) {
                        nonce -= 1;            // already used
                    } else {
                        nextNonce[from]++;
                    }
                    if (r.nextInt(5) == 0 && !pending.isEmpty()) {
                        // a transfer that also refers to waiting blocks: they are executed below the transaction,
                        // whatever happens to the transaction itself
                        List<Address> fields = Lists.newArrayList();
                        XAmount amount = xdag(1 + r.nextInt(300));
                        fields.add(new Address(keyPair2Hash(h.accounts.get(from)), XDAG_FIELD_INPUT, amount, true));
                        fields.add(new Address(keyPair2Hash(h.accounts.get(to)), XDAG_FIELD_OUTPUT, amount, true));
                        for (int k = 0; k < 2; k++) {
                            Block p = pending.get(r.nextInt(pending.size()));
                            if (p.getTimestamp() < time && fields.stream().noneMatch(f -> !f.getIsAddress()
                                    && f.getAddress().equals(p.getHashLow()))) {
                                fields.add(new Address(p.getHashLow(), XDAG_FIELD_OUT, false));
                            }
                        }
                        b = new Block(maker.config, time, fields, null, false, Lists.newArrayList(h.accounts.get(from)),
                                null, 0, XAmount.ZERO, UInt64.valueOf(nonce));
                        b.signOut(h.accounts.get(from));
                    } else {
                        b = maker.transfer(time, h.accounts.get(from), h.accounts.get(to), xdag(1 + r.nextInt(300)), nonce);
                    }
                } else if (kind == 8 || kind == 9) {
                    // a link block around a few waiting blocks
                    List<Bytes32> wrapped = new ArrayList<>();
                    for (int k = 0; k < 3 && !pending.isEmpty(); k++) {
                        Block p = pending.get(r.nextInt(pending.size()));
                        if (p.getTimestamp() < time && !wrapped.contains(Bytes32.wrap(p.getHashLow()))) {
                            wrapped.add(Bytes32.wrap(p.getHashLow()));
                        }
                    }
                    b = maker.link(time, miners[r.nextInt(2)], "l" + seed + "-" + e + "-" + j, wrapped.toArray(new Bytes32[0]));
                } else if (kind == 10) {
                    // block without inputs whose coinbase field carries an amount
                    List<Address> fields = Lists.newArrayList();
                    if (!pending.isEmpty()) {
                        Block p = pending.get(r.nextInt(pending.size()));
                        if (p.getTimestamp() < time) {
                            fields.add(new Address(p.getHashLow(), XDAG_FIELD_OUT, false));
                        }
                    }
                    fields.add(new Address(keyPair2Hash(miners[0]), XDAG_FIELD_COINBASE, xdag(7), true));
                    b = new Block(maker.config, time, null, fields, false, null, "p" + e + j, -1, XAmount.ZERO, null);
                    b.signOut(miners[0]);
                } else {
                    // outputs that do not fit into an amount when added up
                    int from = r.nextInt(h.accounts.size());
                    List<Address> fields = Lists.newArrayList();
                    fields.add(new Address(keyPair2Hash(h.accounts.get(from)), XDAG_FIELD_INPUT, xdag(1), true));
                    for (int k = 0; k < 5; k++) {
                        fields.add(new Address(keyPair2Hash(h.accounts.get(k % h.accounts.size())), XDAG_FIELD_OUTPUT,
                                xdag(2_000_000_000L), true));
                    }
                    b = new Block(maker.config, time, fields, null, false, Lists.newArrayList(h.accounts.get(from)), null, 0,
                            XAmount.ZERO, UInt64.valueOf(nextNonce[from]++));
                    b.signOut(h.accounts.get(from));
                }
                if (b != null) {
                    h.add(b);
                    pending.add(b);
                }
            }

            // ---- one or two competing main block candidates
            List<Block> mined = new ArrayList<>();
            int competitors = r.nextInt(5) < 2 ? 2 : 1;
            Set<Block> taken = new HashSet<>();
            for (int c = 0; c < competitors; c++) {
                List<Bytes32> links = new ArrayList<>();
                // extend a candidate of the previous epoch - or, now and then, of the one before it
                int back = (e > 2 && r.nextInt(6) == 0) ? 2 : 1;
                List<Block> tips = candidates.get(e - back);
                if (tips.isEmpty() && e - back > 1) {
                    tips = candidates.get(e - back - 1);
                }
                if (!tips.isEmpty()) {
                    // a miner tends to extend its own candidate: that is what makes a fork last a few epochs
                    Block tip = tips.get(r.nextInt(tips.size()));
                    if (tips.size() > c && r.nextInt(3) != 0) {
                        tip = tips.get(c);
                    }
                    links.add(Bytes32.wrap(tip.getHashLow()));
                }
                for (Block p : pending) {
                    if (links.size() < 10 && r.nextInt(3) != 0) {
                        links.add(Bytes32.wrap(p.getHashLow()));
                        taken.add(p);
                    }
                }
                Block cand = maker.candidate(e, miners[c], "s" + seed + "-" + e + "-" + c, links.toArray(new Bytes32[0]));
                minedBy.put(Bytes32.wrap(cand.getHashLow()), miners[c]);
                mined.add(cand);
            }
            for (Block cand : mined) {
                h.add(cand);
            }
            candidates.add(mined);
            // most of what was taken is no longer offered; some blocks are offered (and linked) again later
            pending.removeIf(p -> taken.contains(p) && r.nextInt(5) != 0);
        }
        return h;
    }

    /** A random order in which every block comes after the blocks it refers to. */
    private static List<Block> shuffle(History h, long seed) {
        Random r = new Random(seed);
        List<Block> remaining = new ArrayList<>(h.blocks);
        Set<Bytes32> delivered = new HashSet<>();
        List<Block> order = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<Block> ready = new ArrayList<>();
            for (Block b : remaining) {
                if (delivered.containsAll(h.parents.get(Bytes32.wrap(b.getHashLow())))) {
                    ready.add(b);
                }
            }
            // prefer to run ahead on one branch for a while: that is what makes the forks flip
            Block next = ready.get(r.nextInt(Math.min(ready.size(), 1 + r.nextInt(ready.size()))));
            if (r.nextBoolean()) {
                next = ready.get(ready.size() - 1 - r.nextInt(Math.min(ready.size(), 3)));
            }
            order.add(next);
            remaining.remove(next);
            delivered.add(Bytes32.wrap(next.getHashLow()));
        }
        return order;
    }

    /** What differs between two nodes, with enough context to see why. */
    private static String describe(History history, ChainHarness a, ChainHarness b, String expected, String actual) {
        StringBuilder sb = new StringBuilder("---- state differs ----\n");
        String[] e = expected.split("\n");
        String[] x = actual.split("\n");
        for (int i = 0; i < Math.min(e.length, x.length); i++) {
            if (!e[i].equals(x[i])) {
                sb.append("first : ").append(e[i]).append('\n').append("other : ").append(x[i]).append('\n');
            }
        }
        sb.append("---- first ----\n").append(expected).append("---- other ----\n").append(actual);
        sb.append("---- blocks (creation order) ----\n");
        for (Block blk : history.blocks) {
            sb.append(blk.getHashLow().toUnprefixedHexString(), 16, 32).append(" t=").append(Long.toHexString(blk.getTimestamp()));
            for (Address link : blk.getLinks()) {
                sb.append(' ').append(link.getType().name().replace("XDAG_FIELD_", "")).append(':');
                sb.append(link.getAddress().toUnprefixedHexString(), 16, 32).append('/').append(link.getAmount());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private ChainHarness node(long forkEpoch, ECKeyPair key) throws Exception {
        ChainHarness h = ChainHarness.create(tmp.newFolder(), forkEpoch, key);
        open.add(h);
        return h;
    }

    /**
     * Imports a generated history into a fresh node in creation order and into three more nodes in random
     * parents-first orders and compares the resulting states.
     *
     * @param strict every block must be accepted and every order must give the same state (the hardened rules);
     *               otherwise (the 0.8.x rules, whose validity depends on the state a node happens to be in)
     *               rejected blocks are tolerated, no import may fail with an error, and the result only says
     *               whether the orders agreed
     * @return true if all orders gave the same state
     */
    private boolean run(long forkEpoch, long seed, int epochs, boolean hostile, boolean strict) throws Exception {
        ECKeyPair nodeKey = key(seed, 0);
        ChainHarness first = node(forkEpoch, nodeKey);
        History history = generate(first, seed, epochs, hostile);

        long reorgs = 0;
        for (Block b : history.blocks) {
            long before = first.chain.getXdagStats().nmain;
            ImportResult result = first.add(b);
            if (strict) {
                assertTrue("seed " + seed + ": " + result + " " + result.getErrorInfo(),
                        result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST);
            } else {
                assertTrue("seed " + seed + ": " + result + " " + result.getErrorInfo(), result != ImportResult.ERROR);
            }
            if (first.chain.getXdagStats().nmain < before) {
                reorgs++;
            }
        }
        first.settle();
        String expected = first.dump(history.blocks, history.accounts);
        boolean same = true;

        for (int variant = 1; variant <= 3; variant++) {
            ChainHarness other = node(forkEpoch, nodeKey);
            String trace = System.getProperty("determinism.trace");
            String last = "";
            for (Block b : shuffle(history, seed * 31 + variant)) {
                long before = other.chain.getXdagStats().nmain;
                ImportResult result = other.add(b);
                if (trace != null) {
                    // follow one block through the imports of this node (debugging aid)
                    StringBuilder now = new StringBuilder();
                    for (Block t : history.blocks) {
                        if (t.getHashLow().toUnprefixedHexString().startsWith(trace, 16)) {
                            Block stored = other.chain.getBlockByHash(t.getHashLow(), false);
                            now.append(stored == null ? "absent" : "flags=" + Integer.toHexString(stored.getInfo().getFlags())
                                    + " height=" + stored.getInfo().getHeight());
                        }
                    }
                    byte[] top = other.chain.getXdagTopStatus().getTop();
                    now.append(" nmain=").append(other.chain.getXdagStats().nmain).append(" top=")
                            .append(top == null ? "-" : org.bouncycastle.util.encoders.Hex.toHexString(top).substring(16, 32));
                    if (!now.toString().equals(last)) {
                        System.out.println("order " + variant + " after " + b.getHashLow().toUnprefixedHexString().substring(16, 32)
                                + " (" + result + "): " + now);
                        last = now.toString();
                    }
                }
                if (strict) {
                    assertTrue("seed " + seed + " order " + variant + ": " + result + " " + result.getErrorInfo(),
                            result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST);
                } else {
                    assertTrue("seed " + seed + " order " + variant + ": " + result + " " + result.getErrorInfo(),
                            result != ImportResult.ERROR);
                }
                if (other.chain.getXdagStats().nmain < before) {
                    reorgs++;
                }
            }
            other.settle();
            String actual = other.dump(history.blocks, history.accounts);
            if (!expected.equals(actual)) {
                same = false;
                if (strict) {
                    System.out.println(describe(history, first, other, expected, actual));
                    assertEquals("seed " + seed + ", delivery order " + variant, expected, actual);
                }
            }
            other.close();
            open.remove(other);
        }
        System.out.println("seed " + seed + ": blocks=" + history.blocks.size() + " nmain=" + first.chain.getXdagStats().nmain
                + " reorgs=" + reorgs + (same ? "" : " (orders disagree)"));
        first.close();
        open.remove(first);
        totalReorgs += reorgs;
        return same;
    }

    private long totalReorgs;

    /** Seeds to run, "from-to"; -Ddeterminism.seeds=1-2000 runs a long campaign. */
    private static long[] seeds(String standard) {
        String[] parts = System.getProperty("determinism.seeds", standard).split("-");
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }

    @Test
    public void hardenedRulesGiveTheSameStateInAnyOrder() throws Exception {
        long[] range = seeds("1-40");
        for (long seed = range[0]; seed <= range[1]; seed++) {
            run(0, seed, 14 + (int) (seed % 5), true, true);
        }
        assertTrue("the histories contained reorganisations of confirmed main blocks", totalReorgs > 0);
    }

    /**
     * The 0.8.x rules are not order independent: whether a transaction is valid depends on the state the node
     * is in when it arrives ("input address must exist", C6), and when a main block is confirmed depends on
     * flags that reorganisations clear (BI_REF). This is why they are frozen rather than fixed, and why the
     * hardened rules exist. What must hold even so: no order makes the import fail with an error.
     */
    @Test
    public void legacyRulesNeverFailButDependOnTheOrderOfArrival() throws Exception {
        int disagreeing = 0;
        for (long seed = 101; seed <= 106; seed++) {
            if (!run(OPEN_NET_FORK_NOT_SCHEDULED, seed, 14, false, false)) {
                disagreeing++;
            }
        }
        System.out.println("legacy rules: " + disagreeing + " of 6 histories gave different states in different orders");
    }
}
