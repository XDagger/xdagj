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
import io.xdag.Kernel;
import io.xdag.core.*;
import io.xdag.crypto.ECKey;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerCalculate;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.node.Node;
import io.xdag.utils.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.spongycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.xdag.config.Constants.*;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.*;

@Slf4j
public class Commands {

    @Getter
    private Kernel kernel;

    public Commands(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * list address + balance
     *
     * @param num Number of prints
     * @return address + balance
     */
    public String account(int num) {
        List<byte[]> res = kernel.getBlockchain().getAllAccount();
        // account in memory,do not store in rocksdb
        Map<ByteArrayWrapper, Integer> memAccount = kernel.getBlockchain().getMemAccount();

        StringBuilder str = new StringBuilder();

        for (ByteArrayWrapper key : memAccount.keySet()) {
            if (num == 0) {
                break;
            }
            str.append(hash2Address(key.getData()))
                    .append(" ")
                    .append("0.00 xdag")
                    .append(" ")
                    .append("key ")
                    .append(memAccount.get(key));
            num--;
        }

        for (byte[] tmp : res) {
            if (num == 0) {
                break;
            }
            str.append(hash2Address(kernel.getBlockchain().getBlockByHash(tmp, false).getHash()))
                    .append(" ")
                    .append(String.format("%.9f", amount2xdag(kernel.getBlockchain().getBlockByHash(tmp, false).getInfo().getAmount())))
                    .append("xdag")
                    .append(" key ");
//                    .append(kernel.getBlockStore().getBlockKeyIndex(tmp));
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
        if (org.apache.commons.lang3.StringUtils.isEmpty(address)) {
            return String.format("%.9f", amount2xdag(kernel.getAccountStore().getGBalance())) + " XDAG";
        } else {
            byte[] hash;
            if (org.apache.commons.lang3.StringUtils.length(address) == 32) {
                hash = address2Hash(address);
            } else {
                hash = StringUtils.getHash(address);
            }
            byte[] key = new byte[32];
            System.arraycopy(Objects.requireNonNull(hash), 8, key, 8, 24);
            Block block = kernel.getBlockStore().getBlockInfoByHash(key);
            return String.format("%.9f", amount2xdag(block.getInfo().getAmount())) + " XDAG";
        }
    }

    /**
     * Real make a transaction for given amount and address
     *
     * @param sendAmount amount
     * @param address    receiver address
     * @return Transaction hash
     */
    public String xfer(double sendAmount, byte[] address) {
        long amount = xdag2amount(sendAmount);
        byte[] to = new byte[32];
        System.arraycopy(address, 8, to, 8, 24);

        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUT, amount));
        Map<Address, ECKey> ans = kernel.getAccountStore().getAccountListByAmount(amount);
        if (MapUtils.isEmpty(ans)) {
            return "Balance not enough";
        }
        Block block = kernel.getBlockchain().createNewBlock(ans, tos, false);
        ECKey defaultKey = kernel.getWallet().getDefKey().ecKey;
        boolean isdefaultKey = false;
        // 签名
        for (ECKey ecKey : ans.values()) {

            if (ecKey.equals(defaultKey)) {
                isdefaultKey = true;
                block.signOut(ecKey);
            } else {
                block.signIn(ecKey);
            }
        }
        // 如果默认密钥被更改，需要重新对输出签名签属
        if (!isdefaultKey) {
            block.signOut(kernel.getWallet().getDefKey().ecKey);
        }

        BlockWrapper blockWrapper = new BlockWrapper(block, kernel.getConfig().getTTL(), null);

        // blockWrapper.setTransaction(true);
        kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);

        log.info(
                "Transfer [{}]Xdag from [{}] to [{}]",
                sendAmount,
                BasicUtils.hash2Address(ans.keySet().iterator().next().getHashLow()),
                BasicUtils.hash2Address(to));

//        System.out.println(
//                "Transfer " + sendAmount + "XDAG to Address [" + BasicUtils.hash2Address(to) + "]");

        return "Transaction :"
                + BasicUtils.hash2Address(block.getHashLow())
                + " it will take several minutes to complete the transaction.";
    }

    /**
     * Current Blockchain Status
     */
    public String stats() {
        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        return String.format(
                "Statistics for ours and maximum known parameters:\n" +
                        "            hosts: %d of %d\n" +
                        "           blocks: %d of %d\n" +
                        "      main blocks: %d of %d\n" +
                        "     extra blocks: %d      \n" +
                        "    orphan blocks: %d      \n" +
                        " wait sync blocks: %d      \n" +
                        " chain difficulty: %s of %s\n" +
                        "      XDAG supply: %.9f of %.9f",
                kernel.getNetDB().getSize(), kernel.getNetDBMgr().getWhiteDB().getSize(),
                xdagStats.getNblocks(), xdagStats.getTotalnblocks(),
                xdagStats.getNmain(), xdagStats.getTotalnmain(),
                xdagStats.nextra,
                xdagStats.nnoref,
                xdagStats.nwaitsync,
                xdagStats.getTopDiff()!=null?xdagStats.getTopDiff().toString(16):"",
                xdagStats.getMaxdifficulty()!=null?xdagStats.getMaxdifficulty().toString(16):"",
                amount2xdag(kernel.getBlockchain().getSupply(xdagStats.nmain)),
                amount2xdag(kernel.getBlockchain().getSupply(xdagStats.totalnmain))
        );
    }

    /**
     * Connect to Node
     */
    public void connect(String server, int port) {
        kernel.getNodeMgr().doConnect(server, port);
    }

    /**
     * Query block by hash
     *
     * @param blockhash blockhash
     * @return block info
     */
    public String block(byte[] blockhash) {
        try {
            byte[] hashLow = new byte[32];
            System.arraycopy(blockhash, 8, hashLow, 8, 24);
            Block block = kernel.getBlockStore().getBlockInfoByHash(hashLow);
            return printBlockInfo(block);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    public String block(String address) {
        byte[] hashlow = address2Hash(address);
        if (hashlow != null) {
            return block(hashlow);
        } else {
            return "Argument is incorrect.";
        }
    }

    public static String printHeaderBlockList() {
        return "---------------------------------------------------------------------------------------------------------\n" +
                "height        address                            time                      state     mined by            \n" +
                "---------------------------------------------------------------------------------------------------------\n";
    }


    public String printBlockInfo(Block block) {
        block.parse();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        String format = (block.getInfo().getFlags() & BI_MAIN) == 0?
                "":"    height: %08d\n" +
                "      time: %s\n" +
                " timestamp: %s\n" +
                "     flags: %s\n" +
                "     state: %s\n" +
                "      hash: %s\n" +
                "    remark: %s\n" +
                "difficulty: %s\n" +
                "   balance: %s  %.9f";
        //TODO need add block as transaction
        return String.format(format,
                block.getInfo().getHeight(),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(time),
                Long.toHexString(block.getTimestamp()),
                Integer.toHexString(block.getInfo().getFlags()),
                getStateByFlags(block.getInfo().getFlags()),
                Hex.toHexString(block.getInfo().getHash()),
                new String(block.getInfo().getRemark(), StandardCharsets.UTF_8),
                block.getInfo().getDifficulty().toString(16),
                hash2Address(block.getHash()), amount2xdag(block.getInfo().getAmount()));
    }

    public static String printBlock(Block block) {
        return printBlock(block, false);
    }


    public static String printBlock(Block block, boolean print_only_addresses) {
        StringBuilder sbd = new StringBuilder();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        if(print_only_addresses) {
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
                    new String(remark==null?"".getBytes():remark), StandardCharsets.UTF_8));
        }
        return sbd.toString();
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
        StringBuilder sbd = new StringBuilder();
        sbd.append(printHeaderBlockList());
        sbd.append(blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n")));
        return sbd.toString();
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
        StringBuilder sbd = new StringBuilder();
        sbd.append(printHeaderBlockList());
        sbd.append(blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n")));
        return sbd.toString();
    }

    public static String getStateByFlags(int flags) {
        int flag = flags & ~(BI_OURS | BI_REMARK);
        // 1F
        if (flag == (BI_REF | BI_MAIN_REF | BI_APPLIED | BI_MAIN | BI_MAIN_CHAIN)) {
            return "Main";
        }
        // 1C
        if (flag == (BI_REF | BI_MAIN_REF | BI_APPLIED)) {
            return "Accepted";
        }
        // 18
        if (flag == (BI_REF | BI_MAIN_REF)) {
            return "Rejected";
        }
        return "Pending";
    }

    public void run() {
        try {
            kernel.testStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        kernel.testStop();
    }

    public String listConnect() {
        Map<Node, Long> map = kernel.getNodeMgr().getActiveNode();
        StringBuilder stringBuilder = new StringBuilder();
        for (Node node : map.keySet()) {
            stringBuilder
                    .append(node.getAddress())
                    .append(" ")
                    .append(map.get(node) == null ? null : FormatDateUtils.format(new Date(map.get(node))))
                    .append(" ")
                    .append(node.getStat().Inbound.get())
                    .append(" in/")
                    .append(node.getStat().Outbound.get())
                    .append(" out");
        }
        return stringBuilder.toString();
    }

    public Kernel.State getKernelState() {
        return kernel.getState();
    }

    public String keygen() {
        kernel.getXdagState().tempSet(XdagState.KEYS);
        kernel.getWallet().createNewKey();
        int size = kernel.getWallet().getKey_internal().size();
        kernel.getXdagState().rollback();
        return "Key " + (size - 1) + " generated and set as default,now key size is:" + size;
    }

    public void resetStore() {
        // TODO 在这里更改是不是可以的 会不会存在线程问题
        kernel.getXdagState().tempSet(XdagState.REST);
        kernel.resetStore();
        kernel.getXdagState().rollback();
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
