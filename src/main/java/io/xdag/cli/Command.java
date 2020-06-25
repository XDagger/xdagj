package io.xdag.cli;

import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.BI_MAIN_CHAIN;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.BI_REMARK;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.xdag2amount;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.XdagState;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerCalculate;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.node.Node;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.StringUtils;
import io.xdag.utils.XdagTime;

public class Command {
  private Kernel kernel;
  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  DecimalFormat df = new DecimalFormat("######0.00");

  public Command(Kernel kernel) {
    this.kernel = kernel;
  }

  /**
   * list address + balance
   *
   * @param num
   * @return address + balance
   */
  public String account(int num) {
    List<byte[]> res = kernel.getBlockchain().getAllAccount();
    // account in memory,do not store in rocksdb
    Map<ByteArrayWrapper, Integer> memAccount = kernel.getBlockchain().getMemAccount();
    System.out.println("Account size:" + (res.size() + memAccount.size()));

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
          .append(memAccount.get(key))
          .append("\n");
      num--;
    }

    for (byte[] tmp : res) {
      if (num == 0) {
        break;
      }
      str.append(hash2Address(kernel.getBlockchain().getBlockByHash(tmp, false).getHash()))
          .append(" ")
          .append(
              df.format(amount2xdag(kernel.getBlockchain().getBlockByHash(tmp, false).getAmount())))
          .append("xdag")
          .append(" key ")
          .append(kernel.getBlockStore().getBlockKeyIndex(tmp))
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
  public String balance(byte[] address) {
    if (address != null) {
      byte[] key = new byte[32];
      System.arraycopy(address, 8, key, 8, 24);
      Block block = kernel.getBlockStore().getBlockInfoByHash(key);
      double xdag = amount2xdag(block.getAmount());
      return df.format(xdag);
    } else {
      return df.format(amount2xdag(kernel.getAccountStore().getGBalance())) + "xdag";
    }
  }

  /**
   * Process xfer command
   *
   * @param command
   * @return
   */
  public String xfer(String command) {

    String[] args = command.split("\\s+");
    try {
      byte[] hash;
      double amount = StringUtils.getDouble(args[0]);

      if (amount < 0) {
        return "The transfer amount must be greater than 0";
      }

      if (args[1].length() == 32) {
        hash = address2Hash(args[1]);
      } else {
        hash = StringUtils.getHash(args[1]);
      }
      if (hash == null) {
        return "No param";
      }
      if (kernel.getAccountStore().getAccountBlockByHash(hash, false) == null) {
        return " incorrect address";
      }
      // 数据检验都合法 请求用户输入密码
      System.out.println("please input your password");
      Scanner scanner = new Scanner(System.in);
      String pwd = scanner.nextLine();

      int err = Native.verify_dnet_key(pwd, kernel.getConfig().getDnetKeyBytes());

      if (err < 0) {
        return "The password is incorrect";
      }

      return xfer(amount, hash);
    } catch (Exception e) {
      return ("Argument is incorrect.");
    }
  }

  /**
   * Real make a transaction for given amount and address
   *
   * @param sendAmount
   * @param address
   * @return
   */
  public String xfer(double sendAmount, byte[] address) {
    long amount = xdag2amount(sendAmount);
    byte[] to = new byte[32];
    System.arraycopy(address, 8, to, 8, 24);

    List<Address> tos = new ArrayList<>();
    tos.add(new Address(to, XDAG_FIELD_OUT, amount));
    Map<Address, ECKey> ans = kernel.getAccountStore().getAccountListByAmount(amount);
    if (ans == null || ans.size() == 0) {
      return "Balance not enough";
    }
    Block block = kernel.getBlockchain().createNewBlock(ans, tos, false);
    // 签名
    for (ECKey ecKey : ans.values()) {
      if (ecKey.equals(kernel.getWallet().getDefKey().ecKey)) {
        block.signOut(ecKey);
      } else {
        block.signIn(ecKey);
      }
    }
    BlockWrapper blockWrapper = new BlockWrapper(block, kernel.getConfig().getTTL(), null);

    // blockWrapper.setTransaction(true);
    kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);

    // logger.info("Transfer [{}]Xdag from [{}] to [{}]",sendAmount,
    // BasicUtils.hash2Address(ans.keySet().iterator().next().getHashLow()),
    // BasicUtils.hash2Address(to));
    System.out.println(
        "Transfer " + sendAmount + "XDAG   to Address [" + BasicUtils.hash2Address(to) + "]");

    return "Transaction :" + Hex.toHexString(block.getHashLow()) + " waiting to be processed";
  }

  /** Current Blockchain Status */
  public String stats() {
    String stringBuilder =
        "blocks(save):"
            + kernel.getBlockchain().getBlockSize()
            + "\n"
            + "main blocks(save):"
            + kernel.getBlockchain().getMainBlockSize()
            + "\n"
            + "blocks:"
            + kernel.getNetStatus().getNblocks()
            + " of "
            + kernel.getNetStatus().getTotalnblocks()
            + "\n"
            + "main blocks:"
            + kernel.getNetStatus().getNmain()
            + " of "
            + kernel.getNetStatus().getTotalnmain()
            + "\n"
            + "extra blocks:"
            + kernel.getBlockchain().getExtraSize()
            + "\n"
            + "orphan blocks:"
            + kernel.getBlockchain().getOrphanSize()
            + "\n"
            + "chain difficulity:"
            + kernel.getBlockchain().getTopDiff().toString(16)
            + " of "
            + kernel.getNetStatus().getMaxdifficulty().toString(16)
            + "\n"
            + "XDAG supply:"
            + kernel.getBlockchain().getMainBlockSize() * 1024
            + "\n";
    return stringBuilder;
  }

  /**
   * Connect to Node
   *
   * @param server
   * @param port
   */
  public void connect(String server, int port) {
    kernel.getNodeMgr().doConnect(server, port);
  }

  /**
   * Query block by hash
   *
   * @param blockhash
   * @return
   */
  public String block(byte[] blockhash) {
    try {
      byte[] hashLow = new byte[32];
      System.arraycopy(blockhash, 8, hashLow, 8, 24);
      Block blockInfo = kernel.getBlockStore().getBlockInfoByHash(hashLow);
      return printBlockInfo(blockInfo);
    } catch (Exception e) {
      return "Block is not found.";
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

  /**
   * Print Main blocks by given number
   *
   * @param n
   * @return
   */
  public String mainblocks(int n) {
    List<Block> res = kernel.getBlockchain().listMainBlocks(n);
    if (res == null || res.size() == 0) {
      return "No match ans";
    }
    StringBuilder ans = new StringBuilder();
    for (int i = 0; i < res.size(); i++) {
      Block blockInfo = res.get(i);
      Date date = new Date(XdagTime.xdagtimestampToMs(blockInfo.getTimestamp()));
      // ans.append(Hex.toHexString(blockInfo.getHash())).append("
      // ").append(simpleDateFormat.format(date)).append("
      // ").append(getStateByFlags(blockInfo.getFlags())).append("\n");
      ans.append(Hex.toHexString(blockInfo.getHash()))
          .append("  ")
          .append(BasicUtils.hash2Address(blockInfo.getHash()))
          .append("     ")
          .append(simpleDateFormat.format(date))
          .append(" ")
          .append(getStateByFlags(blockInfo.getFlags()))
          .append("   epoch:[" + XdagTime.getEpoch(blockInfo.getTimestamp()) + "]")
          .append("\n");
    }
    return ans.toString();
  }

  /**
   * Print Mined Block by given number
   *
   * @param n
   * @return
   */
  public String minedblocks(int n) {
    List<Block> res = kernel.getBlockchain().listMinedBlocks(n);
    if (res == null || res.size() == 0) {
      return "No match ans";
    }
    StringBuilder ans = new StringBuilder();
    for (int i = 0; i < res.size(); i++) {
      Block blockInfo = res.get(i);
      Date date = new Date(XdagTime.xdagtimestampToMs(blockInfo.getTimestamp()));
      ans.append(
          Hex.toHexString(blockInfo.getHash())
              + "  "
              + simpleDateFormat.format(date)
              + " "
              + getStateByFlags(blockInfo.getFlags())
              + "\n");
    }
    return ans.toString();
  }

  public String printBlockInfo(Block blockInfo) {
    long time = XdagTime.xdagtimestampToMs(blockInfo.getTimestamp());
    Date date = new Date(time);

    return "time: "
        + simpleDateFormat.format(date)
        + "\n"
        + "timestamp: "
        + Long.toHexString(blockInfo.getTimestamp())
        + "\n"
        + "flags: "
        + Integer.toHexString(blockInfo.getFlags())
        + "\n"
        + "state: "
        + getStateByFlags(blockInfo.getFlags())
        + "\n"
        + "hash: "
        + Hex.toHexString(blockInfo.getHash())
        + "\n"
        + "difficulty: "
        + blockInfo.getDifficulty().toString(16)
        + "\n"
        + "balance: "
        + hash2Address(blockInfo.getHash())
        + " "
        + df.format(amount2xdag(blockInfo.getAmount()))
        + "\n";
  }

  public String getStateByFlags(int flags) {
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

  public void start() {
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
          .append(map.get(node) == null ? null : simpleDateFormat.format(new Date(map.get(node))))
          .append(
              " " + node.getStat().Inbound.get() + " in/" + node.getStat().Outbound.get() + " out")
          .append("\n");
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

  public void printfMiners() {

    Miner poolMiner = kernel.getPoolMiner();
    System.out.println("fee : " + BasicUtils.hash2Address(poolMiner.getAddressHash()));

    if (kernel.getMinerManager().getActivateMiners().size() == 0) {
      System.out.println(" without activate miners");
    } else {
      for (Miner miner : kernel.getMinerManager().getActivateMiners().values()) {
        if (miner.getMinerStates() == MinerStates.MINER_ACTIVE) {
          MinerCalculate.printfMinerStats(miner);
        }
      }
    }
  }

  public String getState() {
    return kernel.getXdagState().toString();
  }

  public String disConnectMinerChannel(String command) {
    // TODO: 2020/6/13 判断输入的ip地址是否是合法的 端口 然后找到特定的channel 断开连接

    if ("all".equals(command)) {
      Map<InetSocketAddress, MinerChannel> channels =
          kernel.getMinerManager().getActivateMinerChannels();
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
