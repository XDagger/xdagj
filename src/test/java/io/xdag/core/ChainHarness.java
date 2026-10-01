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

import static io.xdag.config.Constants.BI_EXTRA;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.BI_REMARK;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_COINBASE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_INPUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static io.xdag.utils.BasicUtils.keyPair2Hash;

import com.google.common.collect.Lists;
import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.AbstractConfig;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.db.AddressStore;
import io.xdag.db.BlockStore;
import io.xdag.db.OrphanBlockStore;
import io.xdag.db.rocksdb.AddressStoreImpl;
import io.xdag.db.rocksdb.BlockStoreImpl;
import io.xdag.db.rocksdb.DatabaseFactory;
import io.xdag.db.rocksdb.DatabaseName;
import io.xdag.db.rocksdb.OrphanBlockStoreImpl;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.utils.XdagTime;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;

/**
 * A chain in a temporary directory plus the helpers the consensus tests need to build blocks at chosen times.
 * <p>
 * Nothing here runs on a timer and nothing mines: a test creates every block itself and decides in which order a
 * node sees them, which is what makes the determinism tests possible.
 */
public class ChainHarness {

    /** Time of "epoch 0" of the tests (2020-09-20, well after the devnet era and well before now). */
    public static final long BASE_MS = 1600616700000L;

    public final AbstractConfig config;
    public final Kernel kernel;
    public final Wallet wallet;
    public final BlockchainImpl chain;
    public final DatabaseFactory dbFactory;

    private ChainHarness(AbstractConfig config, Kernel kernel, Wallet wallet, DatabaseFactory dbFactory) {
        this.config = config;
        this.kernel = kernel;
        this.wallet = wallet;
        this.dbFactory = dbFactory;
        this.chain = new QuietBlockchain(kernel);
    }

    /**
     * @param dir       empty directory for the databases and the wallet
     * @param forkEpoch activation epoch of the open-network hardening fork (0 = from genesis,
     *                  Long.MAX_VALUE = legacy rules)
     * @param nodeKey   the node's wallet key
     */
    public static ChainHarness create(File dir, long forkEpoch, ECKeyPair nodeKey) {
        return create(dir, forkEpoch, nodeKey, c -> { });
    }

    public static ChainHarness create(File dir, long forkEpoch, ECKeyPair nodeKey,
            java.util.function.Consumer<AbstractConfig> customizer) {
        AbstractConfig config = new DevnetConfig();
        config.setOpenNetForkEpoch(forkEpoch);
        config.setStoreDir(new File(dir, "db").getAbsolutePath());
        config.setStoreBackupDir(new File(dir, "backup").getAbsolutePath());
        config.setWalletFilePath(new File(dir, "wallet.data").getAbsolutePath());
        customizer.accept(config);

        Wallet wallet = new Wallet(config);
        wallet.unlock("password");
        wallet.setAccounts(Collections.singletonList(nodeKey));
        wallet.flush();

        Kernel kernel = new Kernel(config, nodeKey);
        DatabaseFactory dbFactory = new RocksdbFactory(config);
        BlockStore blockStore = new BlockStoreImpl(
                dbFactory.getDB(DatabaseName.INDEX),
                dbFactory.getDB(DatabaseName.TIME),
                dbFactory.getDB(DatabaseName.BLOCK),
                dbFactory.getDB(DatabaseName.TXHISTORY));
        blockStore.reset();
        OrphanBlockStore orphanBlockStore = new OrphanBlockStoreImpl(dbFactory.getDB(DatabaseName.ORPHANIND), kernel);
        orphanBlockStore.reset();
        AddressStore addressStore = new AddressStoreImpl(dbFactory.getDB(DatabaseName.ADDRESS));
        addressStore.reset();

        kernel.setBlockStore(blockStore);
        kernel.setOrphanBlockStore(orphanBlockStore);
        kernel.setAddressStore(addressStore);
        kernel.setWallet(wallet);
        return new ChainHarness(config, kernel, wallet, dbFactory);
    }

    public void close() {
        chain.stopCheckMain();
        dbFactory.close();
    }

    // ---------------------------------------------------------------- time

    /** Timestamp of the main block candidate of test epoch {@code i} (the last tick of the epoch). */
    public static long candidateTime(int i) {
        return XdagTime.getEndOfEpoch(XdagTime.msToXdagtimestamp(BASE_MS + i * 64000L));
    }

    /** A timestamp inside test epoch {@code i}; {@code offset} in [1, 0xfffe] orders blocks within the epoch. */
    public static long timeIn(int i, int offset) {
        return candidateTime(i) - 0xffff + offset;
    }

    // ---------------------------------------------------------------- blocks

    /**
     * A main block candidate for test epoch {@code epoch}: end-of-epoch timestamp, nonce field, coinbase, links.
     * {@code salt} makes competing candidates of the same epoch differ.
     */
    public Block candidate(int epoch, ECKeyPair miner, String salt, Bytes32... links) {
        List<Address> pending = Lists.newArrayList();
        for (Bytes32 link : links) {
            pending.add(new Address(link, XDAG_FIELD_OUT, false));
        }
        pending.add(new Address(keyPair2Hash(miner), XDAG_FIELD_COINBASE, true));
        Block b = new Block(config, candidateTime(epoch), null, pending, true, null, null, -1, XAmount.ZERO, null);
        b.signOut(miner);
        b.setNonce(HashUtils.sha256(Bytes.wrap(salt.getBytes(StandardCharsets.UTF_8))));
        return b;
    }

    /** A block without inputs that only refers to other blocks (or to nothing: an "address block"). */
    public Block link(long time, ECKeyPair key, String remark, Bytes32... links) {
        List<Address> pending = Lists.newArrayList();
        for (Bytes32 link : links) {
            pending.add(new Address(link, XDAG_FIELD_OUT, false));
        }
        Block b = new Block(config, time, null, pending, false, null, remark, -1, XAmount.ZERO, null);
        b.signOut(key);
        return b;
    }

    /** The fee of a transaction with one output and no extra fee in its header: MIN_GAS. */
    public static final XAmount FEE = XAmount.of(100, XUnit.MILLI_XDAG);

    /** An account transaction: {@code amount} leaves the account of {@code from}; the recipient gets amount - FEE. */
    public Block transfer(long time, ECKeyPair from, ECKeyPair to, XAmount amount, long nonce) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(keyPair2Hash(from), XDAG_FIELD_INPUT, amount, true));
        refs.add(new Address(keyPair2Hash(to), XDAG_FIELD_OUTPUT, amount, true));
        Block b = new Block(config, time, refs, null, false, Lists.newArrayList(from), null, 0,
                XAmount.ZERO, UInt64.valueOf(nonce));
        b.signOut(from);
        return b;
    }

    /**
     * A transfer of block balances ("main transaction"): every entry of {@code sources} becomes an IN link with
     * the given amount, the sum goes to the account of {@code to}.
     */
    public Block spendBlocks(long time, ECKeyPair owner, ECKeyPair to, List<Bytes32> sources, List<XAmount> amounts) {
        List<Address> refs = Lists.newArrayList();
        XAmount total = XAmount.ZERO;
        for (int i = 0; i < sources.size(); i++) {
            refs.add(new Address(sources.get(i), XDAG_FIELD_IN, amounts.get(i), false));
            total = total.add(amounts.get(i));
        }
        refs.add(new Address(keyPair2Hash(to), XDAG_FIELD_OUTPUT, total, true));
        Block b = new Block(config, time, refs, null, false, Lists.newArrayList(owner), null, 0,
                XAmount.ZERO, null);
        b.signOut(owner);
        return b;
    }

    // ---------------------------------------------------------------- import

    /** Import a block the way a node receives it: parsed from its 512 bytes. */
    public ImportResult add(Block block) {
        return chain.tryToConnect(wire(block));
    }

    public static Block wire(Block block) {
        return new Block(new XdagBlock(block.getXdagBlock().getData().toArray()));
    }

    /** Confirm every main block that can be confirmed (checkNewMain confirms one per call). */
    public void settle() {
        long before;
        do {
            before = chain.getXdagStats().nmain;
            chain.checkNewMain();
        } while (chain.getXdagStats().nmain != before);
    }

    public XAmount balance(ECKeyPair key) {
        return kernel.getAddressStore().getBalanceByAddress(key.toAddress().toArray());
    }

    public XAmount blockBalance(Block block) {
        return chain.getBlockByHash(block.getHashLow(), false).getInfo().getAmount();
    }

    public int flags(Block block) {
        return chain.getBlockByHash(block.getHashLow(), false).getInfo().getFlags();
    }

    // ---------------------------------------------------------------- state

    /**
     * The consensus state of the given blocks and accounts as text, one line per item, for comparing two nodes.
     * Left out on purpose: BI_OURS / BI_REMARK (wallet, cosmetics), BI_EXTRA (whether a candidate is still only
     * in memory) and BI_REF (whether a block has been referred to yet - the legacy code clears it again on
     * transactions of an unwound main block so that the local miner links them once more).
     */
    public String dump(Collection<Block> blocks, Collection<ECKeyPair> accounts) {
        StringBuilder sb = new StringBuilder();
        XdagStats stats = chain.getXdagStats();
        sb.append("nmain=").append(stats.nmain).append('\n');
        byte[] top = chain.getXdagTopStatus().getTop();
        sb.append("top=").append(top == null ? "-" : Hex.toHexString(top))
                .append(" diff=").append(chain.getXdagTopStatus().getTopDiff().toString(16)).append('\n');
        for (Block b : blocks) {
            Block stored = chain.getBlockByHash(b.getHashLow(), false);
            sb.append(b.getHashLow().toUnprefixedHexString().substring(16, 32)).append(' ');
            if (stored == null) {
                sb.append("absent\n");
                continue;
            }
            BlockInfo info = stored.getInfo();
            sb.append("flags=").append(Integer.toHexString(info.getFlags() & ~(BI_OURS | BI_REMARK | BI_EXTRA | BI_REF) & 0xff))
                    .append(" height=").append(info.getHeight())
                    .append(" amount=").append(info.getAmount())
                    .append(" fee=").append(info.getFee())
                    .append(" diff=").append(info.getDifficulty() == null ? "-" : info.getDifficulty().toString(16))
                    .append(" maxlink=").append(info.getMaxDiffLink() == null ? "-" : Hex.toHexString(info.getMaxDiffLink()).substring(16, 32))
                    .append(" ref=").append(info.getRef() == null ? "-" : Hex.toHexString(info.getRef()).substring(16, 32))
                    .append('\n');
        }
        for (ECKeyPair account : accounts) {
            byte[] address = account.toAddress().toArray();
            sb.append(Hex.toHexString(address)).append(" balance=")
                    .append(kernel.getAddressStore().getBalanceByAddress(address))
                    .append(" nonce=").append(kernel.getAddressStore().getExecutedNonceNum(address).toLong())
                    .append('\n');
        }
        return sb.toString();
    }

    /** BlockchainImpl without its background threads. */
    static class QuietBlockchain extends BlockchainImpl {

        QuietBlockchain(Kernel kernel) {
            super(kernel);
        }

        @Override
        public void startCheckMain(long period) {
        }

        @Override
        public void addOurBlock(int keyIndex, Block block) {
        }
    }
}
