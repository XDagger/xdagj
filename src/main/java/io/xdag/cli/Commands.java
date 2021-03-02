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

import cn.hutool.core.lang.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import org.apache.commons.lang3.time.FastDateFormat;
import org.spongycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.xdag.config.Constants.*;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.*;

@Slf4j
public class Commands {

    @Getter
    private final Kernel kernel;

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
        // account in memory,do not store in rocksdb
        Map<ByteArrayWrapper, Integer> memOurBlocks = kernel.getBlockchain().getMemOurBlocks();
        StringBuilder str = new StringBuilder();
        memOurBlocks.keySet().stream().limit(num).forEach(key-> str.append(hash2Address(key.getData()))
                .append(" ")
                .append("0.00")
                .append(" XDAG")
                .append(" key ")
                .append(memOurBlocks.get(key)));

        kernel.getBlockStore().fetchOurBlocks(pair -> {
            Integer index = pair.getKey();
            Block block = pair.getValue();
            str.append(hash2Address(block.getHash()))
                    .append(" ")
                    .append(String.format("%.9f", amount2xdag(block.getInfo().getAmount())))
                    .append(" XDAG")
                    .append(" key ")
                    .append(index);
            return false;
        });
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
            return String.format("%.9f", amount2xdag(kernel.getBlockchain().getXdagStats().getBalance())) + " XDAG";
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
//
        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUT, amount));

        AtomicLong remain = new AtomicLong(amount);
        Map<Address, ECKey> ourBlocks = Maps.newHashMap();
        //out block select
        kernel.getBlockStore().fetchOurBlocks(new Function<Pair<Integer, Block>, Boolean>() {
            @Override
            public Boolean apply(Pair<Integer, Block> pair) {
                int index = pair.getKey().intValue();
                Block block = pair.getValue();
                if(remain.get() <= block.getInfo().getAmount()) {
                    ourBlocks.put(new Address(block), kernel.getWallet().getKeyByIndex(index));
                    return true;
                } else {
                    remain.set(remain.get() - block.getInfo().getAmount());
                    ourBlocks.put(new Address(block), kernel.getWallet().getKeyByIndex(index));
                    return false;
                }
            }
        });

        Block block = kernel.getBlockchain().createNewBlock(ourBlocks, tos, false);
        ECKey defaultKey = kernel.getWallet().getDefKey().ecKey;
        boolean isdefaultKey = false;
        // 签名
        for (ECKey ecKey : ourBlocks.values()) {
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

        BlockWrapper blockWrapper = new BlockWrapper(block, kernel.getConfig().getTTL());

        // blockWrapper.setTransaction(true);
        kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);

        log.info("Transfer [{}]Xdag from [{}] to [{}]",
                sendAmount,
                BasicUtils.hash2Address(ourBlocks.keySet().iterator().next().getHashLow()),
                BasicUtils.hash2Address(to));

        System.out.println("Transfer " + sendAmount + "XDAG to Address [" + BasicUtils.hash2Address(to) + "]");
        return "Transaction :"
                + BasicUtils.hash2Address(block.getHashLow())
                + " it will take several minutes to complete the transaction.";
    }

    /**
     * Current Blockchain Status
     */
    public String stats() {
        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        return String.format("""
                        Statistics for ours and maximum known parameters:
                                    hosts: %d of %d
                                   blocks: %d of %d
                              main blocks: %d of %d
                             extra blocks: %d
                            orphan blocks: %d
                         wait sync blocks: %d
                         chain difficulty: %s of %s
                              XDAG supply: %.9f of %.9f""",
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
        System.out.println("cxcxcx");
        kernel.getNodeMgr().doConnect(server, port);
    }

    public void connectbylibp2p(String server,int port,String ip){
        StringBuilder stringBuilder = new StringBuilder();
//        network.connect1("/ip4/192.168.3.5/tcp/11112/ipfs/16Uiu2HAmRfT8vNbCbvjQGsfqWUtmZvrj5y8XZXiyUz6HVSqZW8gy")
        stringBuilder.append("/ip4/").append(server).append("/tcp/").append(port).append("/ipfs/").append(ip.replaceAll(":",""));
        System.out.println("ip = "+ stringBuilder.toString());
        kernel.getLibp2pNetwork().dail(stringBuilder.toString());
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
        return """
                ---------------------------------------------------------------------------------------------------------
                height        address                            time                      state     mined by           \s
                ---------------------------------------------------------------------------------------------------------
                """;
    }


    public String printBlockInfo(Block block) {
        block.parse();
        long time = XdagTime.xdagTimestampToMs(block.getTimestamp());
        String heightFormat = ((block.getInfo().getFlags() & BI_MAIN) == 0? "":"    height: %08d\n");
        String otherFormat = """
                         time: %s
                    timestamp: %s
                        flags: %s
                        state: %s
                         hash: %s
                       remark: %s
                   difficulty: %s
                      balance: %s  %.9f""";
        //TODO need add block as transaction
        return String.format(heightFormat, block.getInfo().getHeight()) + String.format(otherFormat,
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
                    new String(remark==null?"".getBytes():remark, StandardCharsets.UTF_8)));
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
            log.error(e.getMessage(), e);
        }
    }

    public void stop() {
        kernel.testStop();
    }

    public String listConnect() {
        Map<Node, Long> map = kernel.getNodeMgr().getActiveNode();
        Map<Node,Long> map0 = kernel.getNodeMgr().getActiveNode0();
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
        for (Node node0 : map0.keySet()) {
            stringBuilder
                    .append(node0.getAddress())
                    .append(" ")
                    .append("libp2p")
                    .append(" ")
                    .append(map0.get(node0) == null ? null : FormatDateUtils.format(new Date(map0.get(node0))))
                    .append(" ")
                    .append(node0.getStat().Inbound.get())
                    .append(" in/")
                    .append(node0.getStat().Outbound.get())
                    .append(" out");
        }
        return stringBuilder.toString();
    }

    public String keygen() {
        kernel.getXdagState().tempSet(XdagState.KEYS);
        kernel.getWallet().createNewKey();
        int size = kernel.getWallet().getKey_internal().size();
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
