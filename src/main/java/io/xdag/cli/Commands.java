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

import static io.xdag.config.BlockState.MAIN;
import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.BI_MAIN_CHAIN;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.BI_REMARK;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.xdag2amount;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.xdag.Kernel;
import io.xdag.config.BlockState;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ImportResult;
import io.xdag.core.TxHistory;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.core.XdagTopStatus;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerCalculate;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.node.Node;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.XdagTime;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class Commands {

    @Getter
    private final Kernel kernel;

    public Commands(Kernel kernel) {
        this.kernel = kernel;
    }

    public static String printHeaderBlockList() {
        return """
                ---------------------------------------------------------------------------------------------------------
                height        address                            time                      state     mined by           \s
                ---------------------------------------------------------------------------------------------------------
                """;
    }

    public static String printBlock(Block block) {
        return printBlock(block, false);
    }

    public static String printBlock(Block block, boolean print_only_addresses) {
        StringBuilder sbd = new StringBuilder();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        if (print_only_addresses) {
            sbd.append(String.format("%s   %08d",
                    BasicUtils.hash2Address(block.getHash()),
                    block.getInfo().getHeight()));
        } else {
            byte[] remark = block.getInfo().getRemark();
            sbd.append(String.format("%08d   %s   %s   %-8s  %-32s",
                    block.getInfo().getHeight(),
                    BasicUtils.hash2Address(block.getHash()),
                    FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                    getStateByFlags(block.getInfo().getFlags()),
                    new String(remark == null ? "".getBytes(StandardCharsets.UTF_8) : remark, StandardCharsets.UTF_8)));
        }
        return sbd.toString();
    }

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
     * list address + balance
     *
     * @param num Number of prints
     * @return address + balance
     */
    public String account(int num) {
        // account in memory, do not store in rocksdb, do not show in terminal
        StringBuilder str = new StringBuilder();

        Map<Block, Integer> ours = new HashMap<>();
        kernel.getBlockStore().fetchOurBlocks(pair -> {
            Integer index = pair.getKey();
            Block block = pair.getValue();
            ours.putIfAbsent(block, index);
            return false;
        });

        List<Map.Entry<Block, Integer>> list = new ArrayList<>(ours.entrySet());

        // 按balance降序排序，按key index降序排序
        list.sort((o1, o2) -> {
            // TODO
            if (o2.getKey().getInfo().getAmount() > o1.getKey().getInfo().getAmount()) {
                return 1;
            } else if (o2.getKey().getInfo().getAmount() == o1.getKey().getInfo().getAmount()) {
                return o2.getValue().compareTo(o1.getValue());
            } else {
                return -1;
            }

        });

        for (Map.Entry<Block, Integer> mapping : list) {
            if (num == 0) {
                break;
            }
            str.append(hash2Address(mapping.getKey().getHash()))
                    .append(" ")
                    .append(String.format("%.9f", amount2xdag(mapping.getKey().getInfo().getAmount())))
                    .append(" XDAG")
                    .append(" key ")
                    .append(mapping.getValue()).append("\n");
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
        if (StringUtils.isEmpty(address)) {
            return "Balance:" + String.format("%.9f", amount2xdag(kernel.getBlockchain().getXdagStats().getBalance()))
                    + " XDAG";
        } else {
//            byte[] key = new byte[32];
            Bytes32 hash;
            MutableBytes32 key = MutableBytes32.create();
            if (StringUtils.length(address) == 32) {
                hash = address2Hash(address);
            } else {
                hash = BasicUtils.getHash(address);
            }
//            System.arraycopy(Objects.requireNonNull(hash), 8, key, 8, 24);
            key.set(8, Objects.requireNonNull(hash).slice(8, 24));
            Block block = kernel.getBlockStore().getBlockInfoByHash(Bytes32.wrap(key));
            return "Balance:" + String.format("%.9f", amount2xdag(block.getInfo().getAmount())) + " XDAG";
        }
    }

    /**
     * Real make a transaction for given amount and address
     *
     * @param sendAmount amount
     * @param address receiver address
     * @return Transaction hash
     */
    public String xfer(double sendAmount, Bytes32 address, String remark) {

        StringBuilder str = new StringBuilder();
        str.append("Transaction :{ ").append("\n");

        long amount = xdag2amount(sendAmount);
        MutableBytes32 to = MutableBytes32.create();
//        System.arraycopy(address, 8, to, 8, 24);
        to.set(8, address.slice(8, 24));

        // 待转账余额
        AtomicLong remain = new AtomicLong(amount);
        // 转账输入
        Map<Address, KeyPair> ourBlocks = Maps.newHashMap();

        // our block select
        kernel.getBlockStore().fetchOurBlocks(pair -> {
            int index = pair.getKey();
            Block block = pair.getValue();
            if (remain.get() <= block.getInfo().getAmount()) {
                ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, remain.get()),
                        kernel.getWallet().getAccounts().get(index));
                remain.set(0);
                return true;
            } else {
                if (block.getInfo().getAmount() > 0) {
                    remain.set(remain.get() - block.getInfo().getAmount());
                    ourBlocks.put(new Address(block.getHashLow(), XDAG_FIELD_IN, block.getInfo().getAmount()),
                            kernel.getWallet().getAccounts().get(index));
                    return false;
                }
                return false;
            }
        });

        // 余额不足
        if (remain.get() > 0) {
            return "Balance not enough.";
        }

        // 生成多个交易块
        List<BlockWrapper> txs = createTransactionBlock(ourBlocks, to, remark);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                str.append(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow())).append("\n");
            }
        }

        return str.append("}, it will take several minutes to complete the transaction.").toString();

    }

    private List<BlockWrapper> createTransactionBlock(Map<Address, KeyPair> ourKeys, Bytes32 to, String remark) {
        // 判断是否有remark
        int hasRemark = remark == null ? 0 : 1;

        List<BlockWrapper> res = new ArrayList<>();

        // 遍历ourKeys 计算每个区块最多能放多少个
        // int res = 1 + pairs.size() + to.size() + 3*keys.size() + (defKeyIndex == -1 ? 2 : 0);
        LinkedList<Map.Entry<Address, KeyPair>> stack = new LinkedList<>(ourKeys.entrySet());

        // 每次创建区块用到的keys
        Map<Address, KeyPair> keys = new HashMap<>();
        // 保证key的唯一性
        Set<KeyPair> keysPerBlock = new HashSet<>();
        // 放入defkey
        keysPerBlock.add(kernel.getWallet().getDefKey());

        // base count a block <header + send address + defKey signature>
        int base = 1 + 1 + 2 + hasRemark;
        long amount = 0;

        while (stack.size() > 0) {
            Map.Entry<Address, KeyPair> key = stack.peek();
            base += 1;
            int originSize = keysPerBlock.size();
            keysPerBlock.add(key.getValue());
            // 说明新增加的key没有重复
            if (keysPerBlock.size() > originSize) {
                // 一个字段公钥加两个字段签名
                base += 3;
            }
            // 可以将该输入 放进一个区块
            if (base < 16) {
                amount += key.getKey().getAmount().longValue();
                keys.put(key.getKey(), key.getValue());
                stack.poll();
            } else {
                res.add(createTransaction(to, amount, keys, remark));
                // 清空keys，准备下一个
                keys = new HashMap<>();
                keysPerBlock = new HashSet<>();
                keysPerBlock.add(kernel.getWallet().getDefKey());
                base = 1 + 1 + 2 + hasRemark;
                amount = 0;
            }
        }
        if (keys.size() != 0) {
            res.add(createTransaction(to, amount, keys, remark));
        }

        return res;
    }

    private BlockWrapper createTransaction(Bytes32 to, long amount, Map<Address, KeyPair> keys, String remark) {

        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUT, amount));

        Block block = kernel.getBlockchain().createNewBlock(new HashMap<>(keys), tos, false, remark);

        if (block == null) {
            return null;
        }

        KeyPair defaultKey = kernel.getWallet().getDefKey();

        boolean isdefaultKey = false;
        // 签名
        for (KeyPair ecKey : Set.copyOf(new HashMap<>(keys).values())) {
            if (ecKey.equals(defaultKey)) {
                isdefaultKey = true;
                block.signOut(ecKey);
            } else {
                block.signIn(ecKey);
            }
        }
        // 如果默认密钥被更改，需要重新对输出签名签属
        if (!isdefaultKey) {
            block.signOut(kernel.getWallet().getDefKey());
        }

        return new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL());
    }

    /**
     * Current Blockchain Status
     */
    public String stats() {
        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        XdagTopStatus xdagTopStatus = kernel.getBlockchain().getXdagTopStatus();

        //diff
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
                              XDAG supply: %.9f of %.9f
                        4 hr hashrate MHs: %.9f of %.9f""",
                kernel.getNetDB().getSize(), kernel.getNetDBMgr().getWhiteDB().getSize(),
                xdagStats.getNblocks(), Math.max(xdagStats.getTotalnblocks(), xdagStats.getNblocks()),
                xdagStats.getNmain(), Math.max(xdagStats.getTotalnmain(), xdagStats.getNmain()),
                xdagStats.nextra,
                xdagStats.nnoref,
                xdagStats.nwaitsync,
//                xdagTopStatus.getTopDiff()!=null?xdagTopStatus.getTopDiff().toString(16):"",
//                xdagStats.getMaxdifficulty()!=null?xdagStats.getMaxdifficulty().toString(16):"",
                currentDiff.toString(16),
                maxDiff.toString(16),
                amount2xdag(kernel.getBlockchain().getSupply(xdagStats.nmain)),
                amount2xdag(kernel.getBlockchain().getSupply(Math.max(xdagStats.nmain, xdagStats.totalnmain))),
                BasicUtils.xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateOurs()),
                BasicUtils.xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateTotal())
        );
    }

    /**
     * Connect to Node
     */
    public void connect(String server, int port) {
        kernel.getNodeMgr().doConnect(server, port);
    }

    public void connectbylibp2p(String server, int port, String ip) {
        //       连接格式 ("/ip4/192.168.3.5/tcp/11112/ipfs/16Uiu2HAmRfT8vNbCbvjQGsfqWUtmZvrj5y8XZXiyUz6HVSqZW8gy")
        kernel.getLibp2pNetwork().dail("/ip4/" + server + "/tcp/" + port + "/ipfs/" + ip.replaceAll(":", ""));
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
            Block block = kernel.getBlockStore().getRawBlockByHash(hashLow);
            if (block == null) {
                block = kernel.getBlockStore().getBlockInfoByHash(hashLow);
                return printBlockInfo(block, false);
            } else {
                return printBlockInfo(block, true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    public String block(String address) {
        Bytes32 hashlow = address2Hash(address);
        if (hashlow != null) {
            return block(hashlow);
        } else {
            return "Argument is incorrect.";
        }
    }

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
                   balance: %s  %.9f
                -----------------------------------------------------------------------------------------------------------------------------
                                               block as transaction: details
                 direction  address                                    amount
                       fee: %s           %.9f""";
        StringBuilder inputs = null;
        StringBuilder outputs = null;
        if (raw) {
            if (block.getInputs().size() != 0) {
                inputs = new StringBuilder();
                for (int i = 0; i < block.getInputs().size(); i++) {
                    inputs.append(String.format("     input: %s           %.9f\n",
                            hash2Address(Bytes32.wrap(
                                    kernel.getBlockchain().getBlockByHash(block.getInputs().get(i).getHashLow(), false)
                                            .getInfo().getHash())),
                            amount2xdag(block.getInputs().get(i).getAmount().longValue())
                    ));
                }
            }
            if (block.getOutputs().size() != 0) {
                outputs = new StringBuilder();
                for (int i = 0; i < block.getOutputs().size(); i++) {
                    outputs.append(String.format("    output: %s           %.9f\n",
                            hash2Address(Bytes32.wrap(
                                    kernel.getBlockchain().getBlockByHash(block.getOutputs().get(i).getHashLow(), false)
                                            .getInfo().getHash())),
                            amount2xdag(block.getOutputs().get(i).getAmount().longValue())
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
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc())) {
            tx.append(String.format("    earn: %s           %.9f   %s\n", hash2Address(block.getHashLow()),
                    amount2xdag(kernel.getBlockchain().getReward(block.getInfo().getHeight())),
                    FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                            .format(XdagTime.xdagTimestampToMs(block.getTimestamp()))));
        }
        for (TxHistory txHistory : kernel.getBlockchain().getBlockTxHistoryByAddress(block.getHashLow())) {
            Address address = txHistory.getAddress();
            if (address.getType().equals(XDAG_FIELD_IN)) {
                tx.append(String.format("    input: %s           %.9f   %s\n", hash2Address(address.getHashLow()),
                        amount2xdag(address.getAmount().longValue()),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(XdagTime.xdagTimestampToMs(txHistory.getTimeStamp()))));
            } else {
                tx.append(String.format("    output: %s           %.9f   %s\n", hash2Address(address.getHashLow()),
                        amount2xdag(address.getAmount().longValue()),
                        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS")
                                .format(XdagTime.xdagTimestampToMs(txHistory.getTimeStamp()))));
            }
        }

        //TODO need add block as transaction
        return String.format(heightFormat, block.getInfo().getHeight()) + String.format(otherFormat,
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                Long.toHexString(block.getTimestamp()),
                Integer.toHexString(block.getInfo().getFlags()),
                getStateByFlags(block.getInfo().getFlags()),
                Hex.toHexString(block.getInfo().getHash()),
                block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(), StandardCharsets.UTF_8),
                block.getInfo().getDifficulty().toString(16),
                hash2Address(block.getHash()), amount2xdag(block.getInfo().getAmount()),
                //fee目前为0
                block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        : hash2Address(Bytes32.wrap(block.getInfo().getRef())), 0.0
        )
                + "\n"
                + (inputs == null ? "" : inputs.toString()) + (outputs == null ? "" : outputs.toString())

                + "\n"
                + txHisFormat
                + "\n"
                + tx.toString()
                ;
    }

    /**
     * Print Main blocks by given number
     *
     * @param n Number of prints
     * @return Mainblock info
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
     * Print Mined Block by given number
     *
     * @param n Number of prints
     * @return minedblock info
     */
    public String minedblocks(int n) {
        List<Block> blocks = kernel.getBlockchain().listMinedBlocks(n);
        if (CollectionUtils.isEmpty(blocks)) {
            return "empty";
        }
        return printHeaderBlockList() +
                blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
    }

    public void run() {
        try {
            kernel.testStart();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void stop() {
        kernel.testStop();
    }

    public String listConnect() {
        Map<Node, Long> map = kernel.getNodeMgr().getActiveNode();
        StringBuilder stringBuilder = new StringBuilder();
        for (Iterator<Node> it = map.keySet().iterator(); it.hasNext(); ) {
            Node node = it.next();
            stringBuilder
                    .append(node.getAddress())
                    .append(" ")
                    .append(map.get(node) == null ? null : XdagTime.format(new Date(map.get(node))))
                    .append(" ")
                    .append(node.getStat().Inbound.get())
                    .append(" in/")
                    .append(node.getStat().Outbound.get())
                    .append(" out").append(System.getProperty("line.separator"));
        }

        return stringBuilder.toString();
    }

    public String keygen()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        kernel.getXdagState().tempSet(XdagState.KEYS);
        kernel.getWallet().addAccountRandom();
        int size = kernel.getWallet().getAccounts().size();
        kernel.getXdagState().rollback();
        return "Key " + (size - 1) + " generated and set as default,now key size is:" + size;
    }

    public String miners() {
        Miner poolMiner = kernel.getPoolMiner();
        StringBuilder sbd = new StringBuilder();
        sbd.append("fee:").append(BasicUtils.hash2Address(poolMiner.getAddressHash())).append("\n");
        if (kernel.getMinerManager().getActivateMiners().size() == 0) {
            sbd.append(" without activate miners");
        } else {
            for (Miner miner : kernel.getMinerManager().getActivateMiners().values()) {
                if (miner.getMinerStates() == MinerStates.MINER_ACTIVE) {
                    sbd.append(MinerCalculate.minerStats(miner));
                }
            }
        }
        return sbd.toString();
    }

    public String state() {
        return kernel.getXdagState().toString();
    }

    public String disConnectMinerChannel(String command) {
        // TODO: 2020/6/13 判断输入的ip地址是否是合法的 端口 然后找到特定的channel 断开连接
        if ("all".equals(command)) {
            Map<InetSocketAddress, MinerChannel> channels = kernel.getMinerManager().getActivateMinerChannels();
            for (MinerChannel channel : channels.values()) {
                channel.dropConnection();
            }
            return "disconnect all channels...";
        } else {
            String[] args = command.split(":");
            try {
                InetSocketAddress host = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
                MinerChannel channel = kernel.getMinerManager().getChannelByHost(host);
                if (channel != null) {
                    channel.dropConnection();
                    return "disconnect a channel：" + command;
                } else {
                    return "Can't find the corresponding channel, please check";
                }
            } catch (Exception e) {
                return "Argument is incorrect.";
            }
        }
    }

}
