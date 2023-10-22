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

import static io.xdag.crypto.Keys.toBytesAddress;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.compareAmountTo;
import static io.xdag.utils.BasicUtils.pubAddress2Hash;
import static io.xdag.utils.WalletUtils.checkAddress;
import static io.xdag.utils.WalletUtils.fromBase58;
import static io.xdag.utils.WalletUtils.toBase58;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.KeyPair;

import com.google.common.collect.Lists;

import io.xdag.DagKernel;
import io.xdag.core.Dagchain;
import io.xdag.core.MainBlock;
import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import io.xdag.core.state.Account;
import io.xdag.core.state.AccountState;
import io.xdag.net.Channel;
import io.xdag.net.Peer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Commands {

    @Getter
    private final DagKernel kernel;

    public Commands(DagKernel kernel) {
        this.kernel = kernel;
    }

    public static String printHeaderBlockList() {
        return """
                ---------------------------------------------------------------------------------------------------------
                height        hash                            time                      coinbase           \s
                ---------------------------------------------------------------------------------------------------------
                """;
    }

    public static String printBlock(MainBlock block) {
        return printBlock(block, false);
    }

    public static String printBlock(MainBlock block, boolean print_only_addresses) {
        StringBuilder sbd = new StringBuilder();
        long time = block.getTimestamp();
        if (print_only_addresses) {
            sbd.append(String.format("%s   %08d", Bytes32.wrap(block.getHash()).toHexString(), block.getNumber()));
        } else {
            sbd.append(String.format("%08d   %s   %s   %-32s",
                    block.getNumber(),
                    Bytes32.wrap(block.getHash()).toHexString(),
                    FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                    toBase58(block.getCoinbase())));
        }
        return sbd.toString();
    }

    /**
     * list address + balance
     *
     * @param num Number of prints
     * @return address + balance
     */
    public String account(int num) {
        StringBuilder str = new StringBuilder();
        List<KeyPair> list = kernel.getWallet().getAccounts();

        MainBlock latestMainBlock = kernel.getDagchain().getLatestMainBlock();
        AccountState as = kernel.getDagchain().getAccountState(latestMainBlock.getHash(), latestMainBlock.getNumber());
        // 按balance降序排序，按key index降序排序
        list.sort((o1, o2) -> {
            Account a1 = as.getAccount(toBytesAddress(o1));
            Account a2 = as.getAccount(toBytesAddress(o2));
            int compareResult = compareAmountTo(a2.getAvailable(), a1.getAvailable());
            if (compareResult >= 0) {
                return 1;
            } else {
                return -1;
            }

        });

        for (KeyPair keyPair : list) {
            if (num == 0) {
                break;
            }
            str.append(toBase58(toBytesAddress(keyPair)))
                    .append(" ")
                    .append(as.getAccount(toBytesAddress(keyPair)).getAvailable().toDecimal(9, XUnit.XDAG).toPlainString())
                    .append(" XDAG")
                    .append("\n");
            num--;
        }

        return str.toString();
    }

    /**
     * Search Balance by Address
     *
     * @param address for search balance
     * @return balance of give address
     */
    public String balance(String address) {
        MainBlock latestMainBlock = kernel.getDagchain().getLatestMainBlock();
        AccountState as = kernel.getDagchain().getAccountState(latestMainBlock.getHash(), latestMainBlock.getNumber());
        if (StringUtils.isEmpty(address)) {
            XAmount ourBalance = XAmount.ZERO;
            List<KeyPair> list = kernel.getWallet().getAccounts();
            for (KeyPair k : list) {
                ourBalance = ourBalance.add(as.getAccount(toBytesAddress(k)).getAvailable());
            }
            return String.format("Balance: %s XDAG", ourBalance.toDecimal(9, XUnit.XDAG).toPlainString());
        } else {
            Bytes32 hash;
            MutableBytes32 key = MutableBytes32.create();
            if (checkAddress(address)) {
                hash = pubAddress2Hash(address);
                key.set(8, Objects.requireNonNull(hash).slice(8, 20));
                XAmount balance = as.getAccount(fromBase58(address)).getAvailable();
                return String.format("Account balance: %s XDAG", balance.toDecimal(9, XUnit.XDAG).toPlainString());
            } else {
//                if (StringUtils.length(address) == 32) {
//                    hash = address2Hash(address);
//                } else {
//                    hash = getHash(address);
//                }
//                key.set(8, Objects.requireNonNull(hash).slice(8, 24));
//                MainBlock block = kernel.getDagchain().getMainBlockByHash(key.toArray());
//                return String.format("Block balance: %s XDAG", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString());
                return "TODO";
            }

        }
    }


//    /**
//     * Real make a transaction for given amount and address
//     *
//     * @param sendAmount amount
//     * @param address    receiver address
//     * @return Transaction hash
//     */
//    public String xfer(double sendAmount, Bytes32 address, String remark) {
//        StringBuilder str = new StringBuilder();
//        str.append("Transaction :{ ").append("\n");
//
//        XAmount amount = XAmount.of(BigDecimal.valueOf(sendAmount), XUnit.XDAG);
//        MutableBytes32 to = MutableBytes32.create();
//        to.set(8, address.slice(8, 20));
//
//        // 待转账余额
//        AtomicReference<XAmount> remain = new AtomicReference<>(amount);
//
//        // 转账输入
//        Map<Address, KeyPair> ourAccounts = Maps.newHashMap();
//        List<KeyPair> accounts = kernel.getWallet().getAccounts();
//        for (KeyPair account : accounts) {
//            byte[] addr = toBytesAddress(account);
//            XAmount addrBalance = kernel.getAddressStore().getBalanceByAddress(addr);
//
//            if (compareAmountTo(remain.get(), addrBalance) <= 0) {
//                ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, remain.get(), true), account);
//                remain.set(XAmount.ZERO);
//                break;
//            } else {
//                if (compareAmountTo(addrBalance, XAmount.ZERO) > 0) {
//                    remain.set(remain.get().subtract(addrBalance));
//                    ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, addrBalance, true), account);
//                }
//            }
//        }
//
//        // 余额不足
//        if (compareAmountTo(remain.get(), XAmount.ZERO) > 0) {
//            return "Balance not enough.";
//        }
//
//        // 生成多个交易块
//        List<BlockWrapper> txs = createTransactionBlock(ourAccounts, to, remark);
//        for (BlockWrapper blockWrapper : txs) {
//            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
//            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
//                kernel.getChannelMgr().sendNewBlock(blockWrapper);
//                str.append(hash2Address(blockWrapper.getBlock().getHashLow())).append("\n");
//            }
//        }
//
//        return str.append("}, it will take several minutes to complete the transaction.").toString();
//
//    }


//    private List<BlockWrapper> createTransactionBlock(Map<Address, KeyPair> ourKeys, Bytes32 to, String remark) {
//        // 判断是否有remark
//        int hasRemark = remark == null ? 0 : 1;
//
//        List<BlockWrapper> res = Lists.newArrayList();
//
//        // 遍历ourKeys 计算每个区块最多能放多少个
//        // int res = 1 + pairs.size() + to.size() + 3*keys.size() + (defKeyIndex == -1 ? 2 : 0);
//
//        LinkedList<Map.Entry<Address, KeyPair>> stack = Lists.newLinkedList(ourKeys.entrySet());
//
//        // 每次创建区块用到的keys
//        Map<Address, KeyPair> keys = Maps.newHashMap();
//        // 保证key的唯一性
//        Set<KeyPair> keysPerBlock = Sets.newHashSet();
//        // 放入defkey
//        keysPerBlock.add(kernel.getWallet().getDefKey());
//
//        // base count a block <header + send address + defKey signature>
//        int base = 1 + 1 + 2 + hasRemark;
//        XAmount amount = XAmount.ZERO;
//
//        while (stack.size() > 0) {
//            Map.Entry<Address, KeyPair> key = stack.peek();
//            base += 1;
//            int originSize = keysPerBlock.size();
//            keysPerBlock.add(key.getValue());
//            // 说明新增加的key没有重复
//            if (keysPerBlock.size() > originSize) {
//                // 一个字段公钥加两个字段签名
//                base += 3;
//            }
//            // 可以将该输入 放进一个区块
//            if (base < 16) {
//                amount = amount.add(key.getKey().getAmount());
//                keys.put(key.getKey(), key.getValue());
//                stack.poll();
//            } else {
//                res.add(createTransaction(to, amount, keys, remark));
//                // 清空keys，准备下一个
//                keys = new HashMap<>();
//                keysPerBlock = new HashSet<>();
//                keysPerBlock.add(kernel.getWallet().getDefKey());
//                base = 1 + 1 + 2 + hasRemark;
//                amount = XAmount.ZERO;
//            }
//        }
//        if (keys.size() != 0) {
//            res.add(createTransaction(to, amount, keys, remark));
//        }
//
//        return res;
//    }

//    private BlockWrapper createTransaction(Bytes32 to, XAmount amount, Map<Address, KeyPair> keys, String remark) {
//        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUTPUT, amount, true));
//        Block block = kernel.getBlockchain().createNewBlock(new HashMap<>(keys), tos, false, remark);
//
//        if (block == null) {
//            return null;
//        }
//
//        KeyPair defaultKey = kernel.getWallet().getDefKey();
//
//        boolean isDefaultKey = false;
//        // signature
//        for (KeyPair ecKey : Set.copyOf(new HashMap<>(keys).values())) {
//            if (ecKey.equals(defaultKey)) {
//                isDefaultKey = true;
//            } else {
//                block.signIn(ecKey);
//            }
//        }
//        // signOut. If the default key is changed, the output signature needs to be re-signed.
//        if (isDefaultKey) {
//            block.signOut(defaultKey);
//        } else {
//            block.signOut(kernel.getWallet().getDefKey());
//        }
//
//        return new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL());
//    }

    /**
     * Current Dagchain Status
     */
    public String stats() {
        List<Peer> activePeers = kernel.getChannelManager().getActivePeers();

        int hostsSize = kernel.getNetDBManager().getNetDB().getSize();
        int activePeersSize = activePeers.size();
        long localBlockHeight = kernel.getDagchain().getLatestMainBlockNumber();
        int pendingSize = kernel.getPendingManager().getQueue().size();
        XAmount localSupply = kernel.getConfig().getDagSpec().getMainBlockSupply(localBlockHeight);

        long[] remoteNumbers = activePeers.stream()
                .mapToLong(c -> c.getLatestMainBlock().getNumber())
                .sorted()
                .toArray();
        XAmount remoteSupply = kernel.getConfig().getDagSpec().getMainBlockSupply(NumberUtils.max(remoteNumbers));

        return String.format("""
                        Statistics for ours and maximum known parameters:
                                    hosts: %d of %d
                              main blocks: %d of %d
                               pending tx: %d
                              xdag supply: %s of %s""",
                activePeersSize, hostsSize,
                localBlockHeight, localBlockHeight,
                pendingSize,
                localSupply.toDecimal(9, XUnit.XDAG).toPlainString(), remoteSupply.toDecimal(9, XUnit.XDAG).toPlainString()
        );
    }

    /**
     * Connect to Node
     */
    public void connect(String server, int port) {
        kernel.getNodeManager().doConnect(server, port);
    }

    /**
     * Query block by hash
     *
     * @param blockhash blockhash
     * @return block info
     */
    public String block(Bytes32 blockhash) {
        try {
            MutableBytes32 hashLow = MutableBytes32.create();
            hashLow.set(8, blockhash.slice(8, 24));
            MainBlock block = kernel.getDagchain().getMainBlockByHash(blockhash.toArray());
//            if (block == null) {
//                block = kernel.getBlockStore().getBlockInfoByHash(hashLow);
//                return printBlockInfo(block, false);
//            } else {
//                return printBlockInfo(block, true);
//            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "error, please check log";
    }

    public String block(String address) {
        Bytes32 hashlow = address2Hash(address);
        return block(hashlow);
    }

//    public String printBlockInfo(MainBlock block) {
////        block.parse();
//        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
//        String heightFormat = "    height: %08d\n";
//        String otherFormat = """
//                      time: %s
//                 timestamp: %s
//                     flags: %s
//                     state: %s
//                      hash: %s
//                    remark: %s
//                difficulty: %s
//                   balance: %s  %s
//                -----------------------------------------------------------------------------------------------------------------------------
//                                               block as transaction: details
//                 direction  address                                    amount
//                       fee: %s           %s""";
//        StringBuilder inputs = null;
//        StringBuilder outputs = null;
//        if (raw) {
//            if (block.getInputs().size() != 0) {
//                inputs = new StringBuilder();
//                for (Address input : block.getInputs()) {
//                    inputs.append(String.format("     input: %s           %s%n",
//                            input.getIsAddress() ? toBase58(hash2byte(input.getAddress())) : hash2Address(input.getAddress()),
//                            input.getAmount().toDecimal(9, XUnit.XDAG).toPlainString()
//                    ));
//                }
//            }
//            if (block.getOutputs().size() != 0) {
//                outputs = new StringBuilder();
//                for (Address output : block.getOutputs()) {
//                    if (output.getType().equals(XDAG_FIELD_COINBASE)) continue;
//                    outputs.append(String.format("    output: %s           %s%n",
//                            output.getIsAddress() ? toBase58(hash2byte(output.getAddress())) : hash2Address(output.getAddress()),
//                            output.getAmount().toDecimal(9, XUnit.XDAG).toPlainString()
//                    ));
//                }
//            }
//        }
//
//        String txHisFormat = """
//                -----------------------------------------------------------------------------------------------------------------------------
//                                               block as address: details
//                 direction  address                                    amount                 time
//                       """;
//        StringBuilder tx = new StringBuilder();
//        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) && block.getInfo().getHeight() > kernel.getConfig().getSnapshotSpec().getSnapshotHeight()) {
//            tx.append(String.format("    earn: %s           %s   %s%n", hash2Address(block.getHashLow()),
//                    kernel.getBlockchain().getReward(block.getInfo().getHeight()).toDecimal(9, XUnit.XDAG).toPlainString(),
//                    FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                            .format(XdagTime.xdagTimestampToMs(block.getTimestamp()))));
//        }
//        for (TxHistory txHistory : kernel.getBlockchain().getBlockTxHistoryByAddress(block.getHashLow(), 1)) {
//            Address address = txHistory.getAddress();
//            BlockInfo blockInfo = kernel.getBlockchain().getBlockByHash(address.getAddress(), false).getInfo();
//            if ((blockInfo.flags & BI_APPLIED) == 0) {
//                continue;
//            }
//            if (address.getType().equals(XDAG_FIELD_IN)) {
//                tx.append(String.format("    input: %s           %s  %s%n", hash2Address(address.getAddress()),
//                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                .format(txHistory.getTimestamp())));
//            } else if (address.getType().equals(XDAG_FIELD_OUT)) {
//                tx.append(String.format("   output: %s           %s   %s%n", hash2Address(address.getAddress()),
//                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                .format(txHistory.getTimestamp())));
//            } else {
//                tx.append(String.format(" snapshot: %s           %s   %s%n",
//                        hash2Address(address.getAddress()),
//                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                .format(txHistory.getTimestamp())));
//            }
//        }
//
//        // TODO need add block as transaction
//        return String.format(heightFormat, block.getNumber()) + String.format(otherFormat,
//                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
//                Long.toHexString(block.getTimestamp()),
//                Integer.toHexString(block.getInfo().getFlags()),
//                getStateByFlags(block.getInfo().getFlags()),
//                Hex.toHexString(block.getHash()),
//                block.getData() == null ? StringUtils.EMPTY : new String(block.getData(), StandardCharsets.UTF_8),
//                block.getInfo().getDifficulty().toString(16),
//                hash2Address(Bytes32.wrap(block.getHash())), block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                // fee目前为0
//                block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" : hash2Address(Bytes32.wrap(block.getInfo().getRef())),
//                XAmount.ZERO.toDecimal(9, XUnit.XDAG).toPlainString()
//        )
//                + "\n"
//                + (inputs == null ? "" : inputs.toString()) + (outputs == null ? "" : outputs.toString())
//
//                + "\n"
//                + txHisFormat
//                + "\n"
//                + tx
//                ;
//    }

    /**
     * Print Main blocks by given number
     *
     * @param n Number of prints
     * @return Mainblock info
     */
    public String mainblocks(int n) {
        Dagchain dagchain = kernel.getDagchain();
        long latestHeight = dagchain.getLatestMainBlockNumber();
        List<MainBlock> blocks = Lists.newArrayList();
        for(long i = latestHeight, j = 0; i > 0 && j < n; i--, j++) {
            MainBlock block = dagchain.getMainBlockByNumber(i);
            blocks.add(block);
        }
        if (CollectionUtils.isEmpty(blocks)) {
            return "empty";
        }
        return printHeaderBlockList() + blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
    }

//    /**
//     * Print Mined Block by given number
//     *
//     * @param n Number of prints
//     * @return minedblock info
//     */
//    public String minedBlocks(int n) {
//        List<Block> blocks = kernel.getBlockchain().listMinedBlocks(n);
//        if (CollectionUtils.isEmpty(blocks)) {
//            return "empty";
//        }
//        return printHeaderBlockList() +
//                blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
//    }

    public void run() {
        try {
            kernel.start();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void stop() {
        kernel.stop();
    }

    public String listConnect() {
        List<Channel> channelList = kernel.getChannelManager().getActiveChannels();
        StringBuilder stringBuilder = new StringBuilder();
        for (Channel channel : channelList) {
            stringBuilder.append(channel).append(" ")
                    .append(System.getProperty("line.separator"));
        }

        return stringBuilder.toString();
    }

//    public String keygen()
//            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
//        kernel.getXdagState().tempSet(XdagState.KEYS);
//        kernel.getWallet().addAccountRandom();
//
//        kernel.getWallet().flush();
//        int size = kernel.getWallet().getAccounts().size();
//        kernel.getXdagState().rollback();
//        return "Key " + (size - 1) + " generated and set as default,now key size is:" + size;
//    }

    public String state() {
        return kernel.getState().name();
    }

//    public String balanceMaxXfer() {
//        return getBalanceMaxXfer(kernel);
//    }

//    public static String getBalanceMaxXfer(Kernel kernel) {
//        final XAmount[] balance = {XAmount.ZERO};
//
//        kernel.getBlockStore().fetchOurBlocks(pair -> {
//            Block block = pair.getValue();
//            if (XdagTime.getCurrentEpoch() < XdagTime.getEpoch(block.getTimestamp()) + 2 * CONFIRMATIONS_COUNT) {
//                return false;
//            }
//            if (compareAmountTo(block.getInfo().getAmount(), XAmount.ZERO) > 0) {
//                balance[0] = balance[0].add(block.getInfo().getAmount());
//            }
//            return false;
//        });
//        return String.format("%s", balance[0].toDecimal(9, XUnit.XDAG).toPlainString());
//    }

    public String address(Bytes32 wrap, int page) {
//        String ov = " OverView" + "\n"
//                + String.format(" address: %s", toBase58(hash2byte(wrap.mutableCopy()))) + "\n"
//                + String.format(" balance: %s", kernel.getAddressStore().getBalanceByAddress(hash2byte(wrap.mutableCopy())).toDecimal(9, XUnit.XDAG).toPlainString()) + "\n";

        String txHisFormat = """
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                 direction  address                                    amount                 time
                       """;
        StringBuilder tx = new StringBuilder();

//        for (TxHistory txHistory : kernel.getBlockchain().getBlockTxHistoryByAddress(wrap, page)) {
//            Address address = txHistory.getAddress();
//            Block block = kernel.getBlockchain().getBlockByHash(address.getAddress(), false);
//            if (block != null) {
//                BlockInfo blockInfo = block.getInfo();
//                if ((blockInfo.flags & BI_APPLIED) == 0) {
//                    continue;
//                }
//                if (address.getType().equals(XDAG_FIELD_INPUT)) {
//                    tx.append(String.format("    input: %s           %s   %s%n", hash2Address(address.getAddress()),
//                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                    .format(txHistory.getTimestamp())));
//                } else if (address.getType().equals(XDAG_FIELD_OUTPUT)) {
//                    tx.append(String.format("   output: %s           %s   %s%n", hash2Address(address.getAddress()),
//                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                    .format(txHistory.getTimestamp())));
//                } else if (address.getType().equals(XDAG_FIELD_COINBASE) && (blockInfo.flags & BI_MAIN) != 0) {
//                    tx.append(String.format(" coinbase: %s           %s   %s%n", hash2Address(address.getAddress()),
//                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                    .format(txHistory.getTimestamp())));
//                } else {
//                    tx.append(String.format(" snapshot: %s           %s  %s%n", hash2Address(address.getAddress()),
//                            address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                    .format(txHistory.getTimestamp())));
//                }
//            } else {
//                tx.append(String.format(" snapshot: %s           %s   %s%n", (toBase58(BytesUtils.byte32ToArray(address.getAddress()))),
//                        address.getAmount().toDecimal(9, XUnit.XDAG).toPlainString(),
//                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
//                                .format(txHistory.getTimestamp())));
//            }
//        }
//
//        return ov + "\n" + txHisFormat + "\n" + tx;
        return "TODO";
    }

//    public String xferToNew() {
//        StringBuilder str = new StringBuilder();
//        str.append("Transaction :{ ").append("\n");
//
//        MutableBytes32 to = MutableBytes32.create();
//        Bytes32 accountHash = keyPair2Hash(kernel.getWallet().getDefKey());
//        to.set(8, accountHash.slice(8, 20));
//
//        String remark = "old balance to new address";
//
//        // 转账输入
//        Map<Address, KeyPair> ourBlocks = Maps.newHashMap();
//
//        // our block select
//        kernel.getBlockStore().fetchOurBlocks(pair -> {
//            int index = pair.getKey();
//            Block block = pair.getValue();
//            if (XdagTime.getCurrentEpoch() < XdagTime.getEpoch(block.getTimestamp()) + 2 * CONFIRMATIONS_COUNT) {
//                return false;
//            }
//            if (compareAmountTo(XAmount.ZERO, block.getInfo().getAmount()) < 0) {
//                ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, block.getInfo().getAmount(), false),
//                        kernel.getWallet().getAccounts().get(index));
//                return false;
//            }
//            return false;
//        });
//
//        // 生成多个交易块
//        List<BlockWrapper> txs = createTransactionBlock(ourBlocks, to, remark);
//        for (BlockWrapper blockWrapper : txs) {
//            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
//            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
//                kernel.getChannelMgr().sendNewBlock(blockWrapper);
//                str.append(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow())).append("\n");
//            }
//        }
//        return str.append("}, it will take several minutes to complete the transaction.").toString();
//    }
}
