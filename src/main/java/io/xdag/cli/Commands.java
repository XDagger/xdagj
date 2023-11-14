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
import static io.xdag.utils.WalletUtils.checkAddress;
import static io.xdag.utils.WalletUtils.fromBase58;
import static io.xdag.utils.WalletUtils.toBase58;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;

import com.google.common.collect.Lists;

import io.xdag.DagKernel;
import io.xdag.config.Constants;
import io.xdag.core.Dagchain;
import io.xdag.core.MainBlock;
import io.xdag.core.Transaction;
import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import io.xdag.core.state.Account;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.crypto.Keys;
import io.xdag.net.Channel;
import io.xdag.net.Peer;
import io.xdag.utils.WalletUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class Commands {

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

        AccountState as;
        if(kernel.getDagchain().getLatestMainBlockNumber() > Constants.EPOCH_FINALIZE_NUMBER) {
            MainBlock LatestCheckPointMainBlock = kernel.getDagchain().getLatestCheckPointMainBlock();
            as = kernel.getDagchain().getAccountState(LatestCheckPointMainBlock.getHash(), LatestCheckPointMainBlock.getNumber());

            // 按balance降序排序，按key index降序排序
            list.sort((o1, o2) -> {
                Account a1 = as.getAccount(toBytesAddress(o1));
                Account a2 = as.getAccount(toBytesAddress(o2));
                int compareResult = a2.getAvailable().compareTo(a1.getAvailable());
                if (compareResult >= 0) {
                    return 1;
                } else {
                    return -1;
                }
            });
        } else {
            as = null;
        }

        for (KeyPair keyPair : list) {
            if (num == 0) {
                break;
            }

            String balanceStr = "0";
            if( as != null && kernel.getDagchain().getLatestMainBlockNumber() > Constants.EPOCH_FINALIZE_NUMBER) {
                balanceStr = as.getAccount(toBytesAddress(keyPair)).getAvailable().toDecimal(9, XUnit.XDAG).toPlainString();
            }

            str.append(toBase58(toBytesAddress(keyPair)))
                    .append(" ")
                    .append(balanceStr)
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
        AccountState as;
        if(kernel.getDagchain().getLatestMainBlockNumber() > Constants.EPOCH_FINALIZE_NUMBER) {
            MainBlock latestCheckPointMainBlock = kernel.getDagchain().getLatestCheckPointMainBlock();
            as = kernel.getDagchain().getAccountState(latestCheckPointMainBlock.getHash(), latestCheckPointMainBlock.getNumber());
        } else {
            as = null;
        }

        if (StringUtils.isEmpty(address)) {
            XAmount ourBalance = XAmount.ZERO;
            List<KeyPair> list = kernel.getWallet().getAccounts();
            String balanceStr = "0";
            if(as != null && kernel.getDagchain().getLatestMainBlockNumber() > Constants.EPOCH_FINALIZE_NUMBER) {
                for (KeyPair k : list) {
                    ourBalance = ourBalance.add(as.getAccount(toBytesAddress(k)).getAvailable());
                }
                balanceStr = ourBalance.toDecimal(9, XUnit.XDAG).toPlainString();
            }
            return String.format("Balance: %s XDAG", balanceStr);
        } else {
            if (as != null && checkAddress(address)) {
                XAmount balance = as.getAccount(fromBase58(address)).getAvailable();
                return String.format("Account balance: %s XDAG", balance.toDecimal(9, XUnit.XDAG).toPlainString());
            } else {
                return "empty";
            }
        }
    }

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
     */
    public String block(Bytes32 blockHash) {
        MainBlock mainBlock = kernel.getDagchain().getMainBlockByHash(blockHash.toArray());
        if(mainBlock != null) {
            return printBlockInfo(mainBlock);
        } else {
            return "empty";
        }
    }

    /**
     * Query block by hash
     */
    public String transaction(Bytes32 txHash) {
        Transaction tx = kernel.getDagchain().getTransaction(txHash.toArray());
        if(tx != null) {
            return printTransactionInfo(tx);
        } else {
            return "empty";
        }
    }

    public String printBlockInfo(MainBlock block) {
        StringBuilder s = new StringBuilder();
        s.append("Block Header:").append('\n');
        s.append("  number: ").append(block.getNumber()).append('\n');
        s.append("  previous hash: ").append(Bytes.wrap(block.getParentHash()).toHexString()).append("\n");
        s.append("  hash: ").append(Bytes.wrap(block.getHash()).toHexString()).append('\n');
        s.append("  coinbase: ").append(WalletUtils.toBase58(block.getCoinbase())).append('\n');
        s.append("  timestamp: ").append(block.getTimestamp()).append("\n");
        s.append("  transactions root: ").append(Bytes.wrap(block.getTransactionsRoot()).toHexString()).append('\n');
        s.append("  result root: ").append(Bytes.wrap(block.getResultsRoot()).toHexString()).append('\n');
        s.append("  difficulty target (nBits): ").append(block.getDifficultyTarget()).append("\n");
        s.append("  nonce: ").append(block.getNonce()).append("\n");
        s.append("Block Body:").append('\n');

        List<Transaction> txs =  block.getTransactions();
        if(CollectionUtils.isNotEmpty(txs)) {
            for(Transaction tx : txs) {
                s.append("  ").append(tx.toString()).append("\n");
            }
        } else {
            s.append("  empty").append("\n");
        }

        return s.toString();
    }

    public String printTransactionInfo(Transaction tx) {
        return String.format("""
                        Transaction:
                          type:%s
                          hash:%s
                          from:%s
                          to:%s
                          value:%s
                          fee:%s
                          nonce:%s
                          timestamp:%s, format_time(%s)
                          data:%s
                        """,
                tx.getType(),
                Bytes.wrap(tx.getHash()).toHexString(),
                WalletUtils.toBase58(tx.getFrom()),
                WalletUtils.toBase58(tx.getTo()),
                tx.getValue().toDecimal(2, XUnit.XDAG).toPlainString(),
                tx.getFee().toDecimal(2, XUnit.XDAG).toPlainString(),
                tx.getNonce(),
                tx.getTimestamp(), FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(tx.getTimestamp()),
                Bytes.wrap(tx.getData()).toHexString());
    }

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

    /**
     * Print Mined Block by given number
     *
     * @param n Number of prints
     * @return minedblock info
     */
    public String minedBlocks(int n) {
        Dagchain dagchain = kernel.getDagchain();
        long latestHeight = dagchain.getLatestMainBlockNumber();
        List<MainBlock> blocks = Lists.newArrayList();
        List<KeyPair> ourKeys = kernel.getWallet().getAccounts();
        for(long i = latestHeight, j = 0; i > 0 && j < n; i--) {
            MainBlock block = dagchain.getMainBlockByNumber(i);

            for(KeyPair keyPair : ourKeys) {
                if(Arrays.equals(block.getCoinbase(), Keys.toBytesAddress(keyPair))) {
                    blocks.add(block);
                    j++;
                }
            }
        }
        if (CollectionUtils.isEmpty(blocks)) {
            return "empty";
        }
        return printHeaderBlockList() +
                blocks.stream().map(Commands::printBlock).collect(Collectors.joining("\n"));
    }

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

    public String state() {
        return kernel.getState().name();
    }

    public String address(byte[] address, int page) {
        AccountState as  = kernel.getDagchain().getLatestAccountState();
        BlockState bs  = kernel.getDagchain().getLatestBlockState();
        Account account = as.getAccount(address);

        String ov = " OverView" + "\n"
                + String.format(" address: %s", toBase58(address)) + "\n"
                + String.format(" balance: %s", account.getAvailable().toDecimal(9, XUnit.XDAG).toPlainString()) + "\n";

        String txHisFormat = """
                -----------------------------------------------------------------------------------------------------------------------------
                                               histories of address: details
                 direction  address                                    amount                 time
                       """;
        StringBuilder tx = new StringBuilder();
        List<Transaction> transactions = bs.getTransactions(address, page * Constants.COMMANDS_ADDRESS_TX_PAGE_SIZE , (page + 1) * Constants.COMMANDS_ADDRESS_TX_PAGE_SIZE);

        for (Transaction transaction : transactions) {
            tx.append(transaction).append("\n");
        }

        return ov + "\n" + txHisFormat + "\n" + tx;
    }
}
