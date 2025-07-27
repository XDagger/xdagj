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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.xdag.Kernel;
import io.xdag.core.*;
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.exception.AddressFormatException;
import io.xdag.crypto.keys.AddressUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.net.Channel;
import io.xdag.pool.ChannelSupervise;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.xdag.config.Constants.*;
import static io.xdag.core.BlockState.MAIN;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.WalletUtils.*;

/**
 * Command line interface for XDAG operations
 */
@Getter
@Slf4j
public class Commands {

    private final Kernel kernel;

    public Commands(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Print header for block list display
     */
    public static String printHeaderBlockList() {
        return """
                ---------------------------------------------------------------------------------------------------------
                height        address                            time                      state     mined by           \s
                ---------------------------------------------------------------------------------------------------------
                """;
    }

    /**
     * Print block info without print_only_addresses flag
     */
    public static String printBlock(Block block) {
        return printBlock(block, false);
    }

    /**
     * Print block info with optional print_only_addresses flag
     */
    public static String printBlock(Block block, boolean print_only_addresses) {
        StringBuilder sbd = new StringBuilder();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        if (print_only_addresses) {
            sbd.append(String.format("%s   %08d",
                    hash2Address(block.getHash()),
                    block.getInfo().getHeight()));
        } else {
            byte[] remark = block.getInfo().getRemark();
            sbd.append(String.format("%08d   %s   %s   %-8s  %-32s",
                    block.getInfo().getHeight(),
                    hash2Address(block.getHash()),
                    FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                    getStateByFlags(block.getInfo().getFlags()),
                    new String(remark == null ? "".getBytes(StandardCharsets.UTF_8) : remark, StandardCharsets.UTF_8)));
        }
        return sbd.toString();
    }

    /**
     * Get block state description from flags
     */
    public static String getStateByFlags(int flags) {
        int flag = flags & ~(BI_OURS | BI_REMARK);
        // 1F
        if (flag == (BI_REF | BI_MAIN_REF | BI_APPLIED | BI_MAIN | BI_MAIN_CHAIN)) {
            return MAIN.getDesc();
        }
        // 1C
        if (flag == (BI_REF | BI_MAIN_REF | BI_APPLIED)) {
            return BlockState.ACCEPTED.getDesc();
        }
        // 18
        if (flag == (BI_REF | BI_MAIN_REF)) {
            return BlockState.REJECTED.getDesc();
        }
        return BlockState.PENDING.getDesc();
    }

    /**
     * List addresses, balances and current transaction quantity
     * @param num Number of addresses to display
     */
    public String account(int num) {
        StringBuilder str = new StringBuilder();
        List<ECKeyPair> list = kernel.getWallet().getAccounts();

        // Sort by balance descending, then by key index descending
        list.sort((o1, o2) -> {
            int compareResult = compareAmountTo(kernel.getAddressStore().getBalanceByAddress(toBytesAddress(o2).toArray()),
                    kernel.getAddressStore().getBalanceByAddress(toBytesAddress(o1).toArray()));
            if (compareResult >= 0) {
                return 1;
            } else {
                return -1;
            }

        });

        for (ECKeyPair keyPair : list) {
            if (num == 0) {
                break;
            }

            UInt64 txQuantity = kernel.getAddressStore().getTxQuantity(toBytesAddress(keyPair).toArray());
            UInt64 exeTxNonceNum = kernel.getAddressStore().getExecutedNonceNum(toBytesAddress(keyPair).toArray());

            str.append(AddressUtils.toBase58Address(keyPair))
                    .append(" ")
                    .append(kernel.getAddressStore().getBalanceByAddress(toBytesAddress(keyPair).toArray()).toDecimal(9, XUnit.XDAG).toPlainString())
                    .append(" XDAG")
                    .append("  [Current TX Quantity: ")
                    .append(txQuantity.toUInt64())
                    .append(", Confirmed TX Quantity: ")
                    .append(exeTxNonceNum.toUInt64())
                    .append("]")
                    .append("\n");
            num--;
        }

        return str.toString();
    }

    /**
     * Get balance for address
     * @param address Address to check balance for, or null for total balance
     */
    public String balance(String address) throws AddressFormatException {
        if (StringUtils.isEmpty(address)) {
            XAmount ourBalance = XAmount.ZERO;
            List<ECKeyPair> list = kernel.getWallet().getAccounts();
            for (ECKeyPair k : list) {
                ourBalance = ourBalance.add(kernel.getAddressStore().getBalanceByAddress(toBytesAddress(k).toArray()));
            }
            return String.format("Balance: %s XDAG", ourBalance.toDecimal(9, XUnit.XDAG).toPlainString());
        } else {
            Bytes32 hash;
            MutableBytes32 key = MutableBytes32.create();
            if (checkAddress(address)) {
                hash = pubAddress2Hash(address);
                key.set(8, Objects.requireNonNull(hash).slice(8, 20));
                XAmount balance = kernel.getAddressStore().getBalanceByAddress(fromBase58(address).toArray());
                return String.format("Account balance: %s XDAG", balance.toDecimal(9, XUnit.XDAG).toPlainString());
            } else {
                if (StringUtils.length(address) == 32) {
                    hash = address2Hash(address);
                } else {
                    hash = getHash(address);
                }
                key.set(8, Objects.requireNonNull(hash).slice(8, 24));
                Block block = kernel.getBlockStore().getBlockInfoByHash(Bytes32.wrap(key));
                return String.format("Block balance: %s XDAG", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString());
            }
        }
    }

    public String txQuantity(String address) throws AddressFormatException {
        if (StringUtils.isEmpty(address)) {
            UInt64 ourTxQuantity = UInt64.ZERO;
            UInt64 exeTxQuantit = UInt64.ZERO;
            List<ECKeyPair> list = kernel.getWallet().getAccounts();
            for (ECKeyPair key : list) {
                ourTxQuantity = ourTxQuantity.add(kernel.getAddressStore().getTxQuantity(toBytesAddress(key).toArray()));
                exeTxQuantit = exeTxQuantit.add(kernel.getAddressStore().getExecutedNonceNum(toBytesAddress(key).toArray()));
            }
            return String.format("Current Transaction Quantity: %s, executed Transaction Quantity: %s \n", ourTxQuantity.toLong(), exeTxQuantit.toLong());
        } else {
            UInt64 addressTxQuantity = UInt64.ZERO;
            UInt64 addressExeTxQuantity = UInt64.ZERO;
            if (checkAddress(address)) {
                addressTxQuantity = addressTxQuantity.add(kernel.getAddressStore().getTxQuantity(fromBase58(address).toArray()));
                addressExeTxQuantity = addressExeTxQuantity.add(kernel.getAddressStore().getExecutedNonceNum(fromBase58(address).toArray()));
                return String.format("Current Transaction Quantity: %s, executed Transaction Quantity: %s \n", addressTxQuantity.toLong(), addressExeTxQuantity.toLong());
            } else {
                return "The account address format is incorrect! \n";
            }
        }
    }

    /**
     * Transfer XDAG to address
     * @param sendAmount Amount to send
     * @param address Recipient address
     * @param remark Optional transaction remark
     */
    public String xfer(double sendAmount, Bytes32 address, String remark) {
        StringBuilder str = new StringBuilder();
        str.append("Transaction :{ ").append("\n");

        XAmount amount = XAmount.of(BigDecimal.valueOf(sendAmount), XUnit.XDAG);
        MutableBytes32 to = MutableBytes32.create();
        to.set(8, address.slice(8, 20));

        // Track remaining amount to send
        AtomicReference<XAmount> remain = new AtomicReference<>(amount);

        // Collect input accounts
        Map<Address, ECKeyPair> ourAccounts = Maps.newHashMap();
        List<ECKeyPair> accounts = kernel.getWallet().getAccounts();
        UInt64 txNonce = null;

        for (ECKeyPair account : accounts) {
            byte[] addr = toBytesAddress(account).toArray();
            XAmount addrBalance = kernel.getAddressStore().getBalanceByAddress(addr);
            UInt64 currentTxQuantity = kernel.getAddressStore().getTxQuantity(addr);
            txNonce = currentTxQuantity.add(UInt64.ONE);

            if (compareAmountTo(remain.get(), addrBalance) <= 0) {
                ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, remain.get(), true), account);
                remain.set(XAmount.ZERO);
                break;
            } else {
                if (compareAmountTo(addrBalance, XAmount.ZERO) > 0) {
                    remain.set(remain.get().subtract(addrBalance));
                    ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, addrBalance, true), account);
                }
            }
        }

        // Check if enough balance
        if (compareAmountTo(remain.get(), XAmount.ZERO) > 0) {
            return "Balance not enough.";
        }

        // Create and broadcast transaction blocks
        List<BlockWrapper> txs = createTransactionBlock(ourAccounts, to, remark, txNonce);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                Block block = new Block(new XdagBlock(blockWrapper.getBlock().getXdagBlock().getData().toArray()));
                List<Address> inputs = block.getInputs();
                UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();
                for (Address input : inputs) {
                    if (input.getType() == XDAG_FIELD_INPUT) {
                        Bytes addr = BytesUtils.byte32ToArray(input.getAddress());
                        kernel.getAddressStore().updateTxQuantity(addr.toArray(), blockNonce);
                    }
                }
                str.append(hash2Address(blockWrapper.getBlock().getHashLow())).append("\n");
            } else if (result == ImportResult.INVALID_BLOCK) {
                str.append(result.getErrorInfo());
            }
        }

        return str.append("}, it will take several minutes to complete the transaction. \n").toString();
    }

    /**
     * Create transaction blocks from inputs to recipient
     */
    private List<BlockWrapper> createTransactionBlock(Map<Address, ECKeyPair> ourKeys, Bytes32 to, String remark, UInt64 txNonce) {
        // Check if remark exists
        int hasRemark = remark == null ? 0 : 1;

        List<BlockWrapper> res = Lists.newArrayList();

        // Process inputs in stack
        LinkedList<Map.Entry<Address, ECKeyPair>> stack = Lists.newLinkedList(ourKeys.entrySet());

        // Track keys used per block
        Map<Address, ECKeyPair> keys = Maps.newHashMap();
        Set<ECKeyPair> keysPerBlock = Sets.newHashSet();
        keysPerBlock.add(kernel.getWallet().getDefKey());

        int base;
        if (txNonce != null) {
            // base count a block <header + transaction nonce + send address + defKey signature>
            base = 1 + 1 + 1 + 2 + hasRemark;
        } else {
            // base count a block <header + send address + defKey signature>
            base = 1 + 1 + 2 + hasRemark;
        }
        XAmount amount = XAmount.ZERO;

        while (!stack.isEmpty()) {
            Map.Entry<Address, ECKeyPair> key = stack.peek();
            base += 1;
            int originSize = keysPerBlock.size();
            keysPerBlock.add(key.getValue());
            
            // New unique key added
            if (keysPerBlock.size() > originSize) {
                base += 3; // Public key + 2 signatures
            }
            
            // Can fit in current block
            if (base < 16) {
                amount = amount.add(key.getKey().getAmount());
                keys.put(key.getKey(), key.getValue());
                stack.poll();
            } else {
                // Create block and reset for next
                res.add(createTransaction(to, amount, keys, remark, txNonce));
                keys = new HashMap<>();
                keysPerBlock = new HashSet<>();
                keysPerBlock.add(kernel.getWallet().getDefKey());
                if (txNonce != null) {
                    base = 1 + 1 + 1 + 2 + hasRemark;
                } else {
                    base = 1 + 1 + 2 + hasRemark;
                }
                amount = XAmount.ZERO;
            }
        }
        
        // Create final block if needed
        if (!keys.isEmpty()) {
            res.add(createTransaction(to, amount, keys, remark, txNonce));
        }
        return res;
    }

    /**
     * Create single transaction block
     */
    private BlockWrapper createTransaction(Bytes32 to, XAmount amount, Map<Address, ECKeyPair> keys, String remark, UInt64 txNonce) {
        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUTPUT, amount, true));
        Block block = kernel.getBlockchain().createNewBlock(new HashMap<>(keys), tos, false, remark,
                XAmount.of(100, XUnit.MILLI_XDAG), txNonce);

        if (block == null) {
            return null;
        }

        ECKeyPair defaultKey = kernel.getWallet().getDefKey();

        boolean isDefaultKey = false;
        // Sign inputs
        for (ECKeyPair ecKey : Set.copyOf(new HashMap<>(keys).values())) {
            if (ecKey.equals(defaultKey)) {
                isDefaultKey = true;
            } else {
                block.signIn(ecKey);
            }
        }
        // Sign outputs
        if (isDefaultKey) {
            block.signOut(defaultKey);
        } else {
            block.signOut(kernel.getWallet().getDefKey());
        }

        return new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL());
    }

    /**
     * Get current blockchain stats
     */
    public String stats() {
        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();

        // Calculate difficulties
        BigInteger currentDiff = xdagTopStatus.getTopDiff() != null ? xdagTopStatus.getTopDiff() : BigInteger.ZERO;
        BigInteger netDiff = xdagStats.getMaxdifficulty() != null ? xdagStats.getMaxdifficulty() : BigInteger.ZERO;
        BigInteger maxDiff = netDiff.max(currentDiff);

        return String.format("""
                        Statistics for ours and maximum known parameters:
                                    hosts: %d of %d
                                   blocks: %d of %d
                              main blocks: %d of %d
                             extra blocks: %d
                            orphan blocks: %d
                         wait sync blocks: %d
                         chain difficulty: %s of %s
                              XDAG supply: %s of %s
                          XDAG in address: %s
                        4 hr hashrate KHs: %.9f of %.9f
                        Number of Address: %d""",
                kernel.getNetDB().getSize(), kernel.getNetDBMgr().getWhiteDB().getSize(),
                xdagStats.getNblocks(), Math.max(xdagStats.getTotalnblocks(), xdagStats.getNblocks()),
                xdagStats.getNmain(), Math.max(xdagStats.getTotalnmain(), xdagStats.getNmain()),
                xdagStats.nextra,
                xdagStats.nnoref,
                xdagStats.nwaitsync,
                currentDiff.toString(16),
                maxDiff.toString(16),
                kernel.getBlockchain().getSupply(xdagStats.nmain).toDecimal(9, XUnit.XDAG).toPlainString(),
                kernel.getBlockchain().getSupply(Math.max(xdagStats.nmain, xdagStats.totalnmain)).toDecimal(9, XUnit.XDAG).toPlainString(),
                kernel.getAddressStore().getAllBalance().toDecimal(9, XUnit.XDAG).toPlainString(),
                xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateOurs()),
                xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateTotal()),
                kernel.getAddressStore().getAddressSize().toLong()
        );
    }

    /**
     * Connect to remote node
     */
    public void connect(String server, int port) {
        kernel.getNodeMgr().doConnect(server, port);
    }

    /**
     * Get block info by hash
     */
    public String block(Bytes32 blockhash) {
        try {
            MutableBytes32 hashLow = MutableBytes32.create();
            hashLow.set(8, blockhash.slice(8, 24));
            Block block = kernel.getBlockStore().getRawBlockByHash(hashLow);
            if (block == null) {
                block = kernel.getBlockStore().getBlockInfoByHash(hashLow);
                return printBlockInfo(block, false);
            } else {
                return printBlockInfo(block, true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "error, please check log";
    }

    /**
     * Get block info by address
     */
    public String block(String address) {
        Bytes32 hashlow = address2Hash(address);
        return block(hashlow);
    }

    /**
     * Print detailed block information
     */
    public String printBlockInfo(Block block, boolean raw) {
        block.parse();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        String heightFormat = ((block.getInfo().getFlags() & BI_MAIN) == 0 ? "" : "    height: %08d\n");
        String otherFormat = """
                      time: %s
                 timestamp: %s
                     flags: %s
                     state: %s
                      hash: %s
                    remark: %s
                difficulty: %s
                   balance: %s  %s
                -----------------------------------------------------------------------------------------------------------------------------
                                               block as transaction: details
                 direction  address                                    amount
                       fee: %s           %s""";
        StringBuilder inputs = null;
        StringBuilder outputs = null;
        if (raw) {
            if (!block.getInputs().isEmpty()) {
                inputs = new StringBuilder();
                for (Address input : block.getInputs()) {
                    inputs.append(String.format("     input: %s           %s%n",
                            input.getIsAddress() ? Base58.encodeCheck(hash2byte(input.getAddress())) : hash2Address(input.getAddress()),
                            input.getAmount().toDecimal(9, XUnit.XDAG).toPlainString()
                    ));
                }
            }
            if (!block.getOutputs().isEmpty()) {
                outputs = new StringBuilder();
                for (Address output : block.getOutputs()) {
                    if (output.getType().equals(XDAG_FIELD_COINBASE)) continue;
                    outputs.append(String.format("    output: %s           %s%n",
                            output.getIsAddress() ? Base58.encodeCheck(
                                hash2byte(output.getAddress())) : hash2Address(output.getAddress()),
                            getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) ? output.getAmount().toDecimal(9, XUnit.XDAG).toPlainString() :
                                    block.getInputs().isEmpty() ? XAmount.ZERO.toDecimal(9, XUnit.XDAG).toPlainString() :
                                            output.getAmount().subtract(MIN_GAS).toDecimal(9, XUnit.XDAG).toPlainString()
                    ));
                }
            }
        }

        String txHisFormat = """
                -----------------------------------------------------------------------------------------------------------------------------
                                               block as address: details
                 direction  address                                    amount                 time
                """;
        StringBuilder tx = new StringBuilder();
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) && block.getInfo().getHeight() > kernel.getConfig().getSnapshotSpec().getSnapshotHeight()) {
            tx.append(String.format("    earn: %s           %s   %s%n", hash2Address(block.getHashLow()),
                            kernel.getBlockchain().getReward(block.getInfo().getHeight()).toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(XdagTime.xdagTimestampToMs(block.getTimestamp()))))
                    .append(String.format("fee earn: %s           %s   %s%n", hash2Address(block.getHashLow()),
                            kernel.getBlockStore().getBlockInfoByHash(block.getHashLow()).getFee().toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(XdagTime.xdagTimestampToMs(block.getTimestamp()))));
        }
        for (TxHistory txHistory : kernel.getBlockchain().getBlockTxHistoryByAddress(block.getHashLow(), 1)) {
            Address address = txHistory.getAddress();
            BlockInfo blockInfo = kernel.getBlockchain().getBlockByHash(address.getAddress(), false).getInfo();
            if ((blockInfo.flags & BI_APPLIED) == 0) {
                continue;
            }
            if (address.getType().equals(XDAG_FIELD_IN)) {
                tx.append(String.format("    input: %s           %s  %s%n", hash2Address(address.getAddress()),
                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(txHistory.getTimestamp())));
            } else if (address.getType().equals(XDAG_FIELD_OUT)) {
                tx.append(String.format("   output: %s           %s   %s%n", hash2Address(address.getAddress()),
                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(txHistory.getTimestamp())));
            } else {
                tx.append(String.format(" snapshot: %s           %s   %s%n",
                        hash2Address(address.getAddress()),
                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(txHistory.getTimestamp())));
            }
        }

        return String.format(heightFormat, block.getInfo().getHeight()) + String.format(otherFormat,
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                Long.toHexString(block.getTimestamp()),
                Integer.toHexString(block.getInfo().getFlags()),
                getStateByFlags(block.getInfo().getFlags()),
                Hex.toHexString(block.getInfo().getHash()),
                block.getInfo().getRemark() == null ? StringUtils.EMPTY : new String(block.getInfo().getRemark(), StandardCharsets.UTF_8),
                block.getInfo().getDifficulty().toString(16),
                hash2Address(block.getHash()), block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" : hash2Address(Bytes32.wrap(block.getInfo().getRef())),
                block.getInfo().getRef() == null ? XAmount.ZERO.toDecimal(9, XUnit.XDAG).toPlainString() :
                        (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) ? kernel.getBlockStore().getBlockInfoByHash(block.getHashLow()).getFee().toDecimal(9, XUnit.XDAG).toPlainString() :
                                (block.getInputs().isEmpty() ? XAmount.ZERO.toDecimal(9, XUnit.XDAG).toPlainString() :
                                        MIN_GAS.multiply(block.getOutputs().size()).toDecimal(9, XUnit.XDAG).toPlainString()))
        )
                + "\n"
                + (inputs == null ? "" : inputs.toString()) + (outputs == null ? "" : outputs.toString())
                + "\n"
                + txHisFormat
                + "\n"
                + tx
                ;
    }

    /**
     * List main blocks
     * @param n Number of blocks to list
     */
    public String mainblocks(int n) {
        List<Block> blocks = kernel.getBlockchain().listMainBlocks(n);
        if (CollectionUtils.isEmpty(blocks)) {
            return "empty";
        }
        return printHeaderBlockList() +
                blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
    }

    /**
     * List mined blocks
     * @param n Number of blocks to list
     */
    public String minedBlocks(int n) {
        List<Block> blocks = kernel.getBlockchain().listMinedBlocks(n);
        if (CollectionUtils.isEmpty(blocks)) {
            return "empty";
        }
        return printHeaderBlockList() +
                blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
    }

    /**
     * Start test mode
     */
    public void run() {
        try {
            kernel.testStart();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Stop test mode
     */
    public void stop() {
        kernel.testStop();
    }

    /**
     * List active connections
     */
    public String listConnect() {
        List<Channel> channelList = kernel.getChannelMgr().getActiveChannels();
        StringBuilder stringBuilder = new StringBuilder();
        for (Channel channel : channelList) {
            stringBuilder.append(channel).append(" ")
                    .append(System.lineSeparator());
        }

        return stringBuilder.toString();
    }

    /**
     * Show websocket channel pool
     */
    public String pool() {
        return ChannelSupervise.showChannel();
    }

    /**
     * Generate new key pair
     */
    public String keygen() {
        kernel.getXdagState().tempSet(XdagState.KEYS);
        kernel.getWallet().addAccountRandom();

        kernel.getWallet().flush();
        int size = kernel.getWallet().getAccounts().size();
        kernel.getXdagState().rollback();
        return "Key " + (size - 1) + " generated and set as default,now key size is:" + size;
    }

    /**
     * Get current XDAG state
     */
    public String state() {
        return kernel.getXdagState().toString();
    }

    /**
     * Get maximum transferable balance
     */
    public String balanceMaxXfer() {
        return getBalanceMaxXfer(kernel);
    }

    /**
     * Calculate maximum transferable balance
     */
    public static String getBalanceMaxXfer(Kernel kernel) {
        final XAmount[] balance = {XAmount.ZERO};

        kernel.getBlockStore().fetchOurBlocks(pair -> {
            Block block = pair.getValue();
            if (XdagTime.getCurrentEpoch() < XdagTime.getEpoch(block.getTimestamp()) + 2 * CONFIRMATIONS_COUNT) {
                return false;
            }
            if (compareAmountTo(block.getInfo().getAmount(), XAmount.ZERO) > 0) {
                balance[0] = balance[0].add(block.getInfo().getAmount());
            }
            return false;
        });
        return String.format("%s", balance[0].toDecimal(9, XUnit.XDAG).toPlainString());
    }

    /**
     * Get address details and transaction history
     * @param wrap Address bytes
     * @param page Page number for transaction history
     */
    public String address(Bytes32 wrap, int page) {
        String ov = " OverView" + "\n"
                + String.format(" address: %s", Base58.encodeCheck(hash2byte(wrap.mutableCopy()))) + "\n"
                + String.format(" balance: %s", kernel.getAddressStore().getBalanceByAddress(hash2byte(wrap.mutableCopy()).toArray()).toDecimal(9, XUnit.XDAG).toPlainString()) + "\n";

        String txHisFormat = """
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                 direction  address                                    amount                 time
                """;
        StringBuilder tx = new StringBuilder();

        for (TxHistory txHistory : kernel.getBlockchain().getBlockTxHistoryByAddress(wrap, page)) {
            Address address = txHistory.getAddress();
            Block block = kernel.getBlockchain().getBlockByHash(address.getAddress(), false);
            if (block != null) {
                BlockInfo blockInfo = block.getInfo();
                if ((blockInfo.flags & BI_APPLIED) == 0) {
                    continue;
                }
                if (address.getType().equals(XDAG_FIELD_INPUT)) {
                    tx.append(String.format("    input: %s           %s   %s%n", hash2Address(address.getAddress()),
                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(txHistory.getTimestamp())));
                } else if (address.getType().equals(XDAG_FIELD_OUTPUT)) {
                    tx.append(String.format("   output: %s           %s   %s%n", hash2Address(address.getAddress()),
                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(txHistory.getTimestamp())));
                } else if (address.getType().equals(XDAG_FIELD_COINBASE) && (blockInfo.flags & BI_MAIN) != 0) {
                    tx.append(String.format(" coinbase: %s           %s   %s%n", hash2Address(address.getAddress()),
                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(txHistory.getTimestamp())));
                } else {
                    tx.append(String.format(" snapshot: %s           %s  %s%n", hash2Address(address.getAddress()),
                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(txHistory.getTimestamp())));
                }
            } else {
                tx.append(String.format(" snapshot: %s           %s   %s%n", (Base58.encodeCheck(BytesUtils.byte32ToArray(address.getAddress()))),
                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(txHistory.getTimestamp())));
            }
        }

        return ov + "\n" + txHisFormat + "\n" + tx;
    }

    /**
     * Transfer balance to a new address
     * @return Transaction result message
     */
    public String xferToNew() {
        StringBuilder str = new StringBuilder();
        str.append("Transaction :{ ").append("\n");

        MutableBytes32 to = MutableBytes32.create();
        Bytes32 accountHash = keyPair2Hash(kernel.getWallet().getDefKey());
        to.set(8, accountHash.slice(8, 20));

        String remark = "block balance to new address";

        // Transaction inputs
        Map<Address, ECKeyPair> ourBlocks = Maps.newHashMap();

        // Select our blocks for transaction
        kernel.getBlockStore().fetchOurBlocks(pair -> {
            int index = pair.getKey();
            Block block = pair.getValue();
            // Skip if block is too recent (less than 2 * CONFIRMATIONS_COUNT epochs old)
            if (XdagTime.getCurrentEpoch() < XdagTime.getEpoch(block.getTimestamp()) + 2 * CONFIRMATIONS_COUNT) {
                return false;
            }

            // Add block if it has positive balance
            if (compareAmountTo(XAmount.ZERO, block.getInfo().getAmount()) < 0) {
                ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, block.getInfo().getAmount(), false),
                        kernel.getWallet().getAccounts().get(index));
                return false;
            }
            return false;
        });

        // Generate multiple transaction blocks
        List<BlockWrapper> txs = createTransactionBlock(ourBlocks, to, remark, null);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                str.append(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow())).append("\n");
            }
        }
        return str.append("}, it will take several minutes to complete the transaction.").toString();
    }

    /**
     * Distribute block rewards to node
     * @param paymentsToNodesMap Map of addresses and keypairs for node payments
     * @return StringBuilder containing transaction result message
     */
    public StringBuilder xferToNode(Map<Address, ECKeyPair> paymentsToNodesMap) {
        StringBuilder str = new StringBuilder("Tx hash paid to the node :{");
        MutableBytes32 to = MutableBytes32.create();
        Bytes32 accountHash = keyPair2Hash(kernel.getWallet().getDefKey());
        to.set(8, accountHash.slice(8, 20));
        String remark = "Pay to " + kernel.getConfig().getNodeSpec().getNodeTag();
        
        // Generate transaction blocks to reward node
        List<BlockWrapper> txs = createTransactionBlock(paymentsToNodesMap, to, remark, null);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                str.append(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow()));
            } else {
                return new StringBuilder("This transaction block is invalid. Tx hash:")
                        .append(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow()));
            }
        }
        return str.append("}");
    }
}
