package io.xdag.cli;

import static io.xdag.utils.BasicUtils.address2Hash;

import io.xdag.Kernel;
import io.xdag.utils.StringUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.spongycastle.util.encoders.Hex;

public class ShellCommand {
  public Command cmd;
  Pattern p = Pattern.compile("^\\s*(.*?):(\\d+)\\s*$");
  private boolean stopFlag = false;

  public ShellCommand(Command cmd) {
    this.cmd = cmd;
  }

  public static void printHelp() {
    System.out.println(
        "Commands:\n"
            + "  account [N]          - print first N (20 by default) our addresses with their amounts\n"
            + "  balance [A]          - print balance of the address A or total balance for all our addresses\n"
            + "  block [A]            - print extended info for the block corresponding to the address or hash A\n"
            + "  lastblocks [N]       - print latest N (20 by default, max limit 100) main blocks\n"
            + "  exit                 - exit this program (not the daemon)\n"
            + "  help                 - print this help\n"
            + "  keygen               - generate new private/public key pair and set it by default\n"
            + "  level [N]            - print level of logging or set it to N (0 - nothing, ..., 9 - all)\n"
            + "  miners               - for pool, print list of recent connected miners\n"
            + "  mining [N]           - print number of mining threads or set it to N\n"
            + "  net command          - run transport layer command, try 'net help'\n"
            + "  pool [CFG]           - print or set pool config; CFG is miners:maxip:maxconn:fee:reward:direct:fund\n"
            + "                          miners - maximum allowed number of miners,\n"
            + "                          maxip - maximum allowed number of miners connected from single ip,\n"
            + "                          maxconn - maximum allowed number of miners with the same address,\n"
            + "                          fee - pool fee in percent,\n"
            + "                          reward - reward to miner who got a block in percent,\n"
            + "                          direct - reward to miners participated in earned block in percent,\n"
            + "                          fund - community fund fee in percent\n"
            + "  run                  - run node after loading local blocks if option -r is used\n"
            + "  state                - print the program state\n"
            + "  stats                - print statistics for loaded and all known blocks\n"
            + "  terminate            - terminate both daemon and this program\n"
            + "  xfer S A             - transfer S XDAG to the address A\n"
            + "  disconnect O [A|IP]  - disconnect all connections or specified miners\n"
            + "                          O is option, can be all, address or ip\n"
            + "                          A is the miners' address\n"
            + "                          IP is the miners' IP\n"
            + "  rpc [command]        - rpc commands, try 'rpc help'\n"
            + "  mainblocks [N]       - print list of N (20 by default) main blocks\n"
            + "  minedblocks [N]      - print list of N (20 by default) main blocks mined by current pool");
  }

  public void handle(String input) {
    String[] args = input.split(" ", 2);
    String command = args[0];
    String value = null;
    if (args.length != 1) {
      value = args[1];
    }
    process(command, value);
  }

  public void process(String command, String value) {
    try {
      boolean running = cmd.getKernelState() == Kernel.State.RUNNING;
      switch (command) {
        case "account":
          if (running) {
            processAccount(value);
          }
          break;
        case "balance":
          if (running) {
            processBalance(value);
          }
          break;
        case "block":
          if (running) {
            processBlock(value);
          }
          break;
        case "lastblocks":
          if (running) {
            processLastblocks(value);
          }
          break;
        case "exit":
        case "terminate":
          cmd.stop();
          onStop();
          break;
        case "help":
          printHelp();
          break;
        case "net":
          if (running) {
            processNet(value);
          }
          break;
        case "stats":
          if (running) {
            processStats();
          }
          break;
        case "xfer":
          if (running) {
            processXfer(value);
          }
          break;
        case "mainblocks":
          if (running) {
            processMainblocks(value);
          }
          break;
        case "minedblocks":
          if (running) {
            processMinedblocks(value);
          }
          break;
        case "run":
          cmd.start();
          break;
        case "keygen":
          System.out.println(cmd.keygen());
          break;
        case "reset":
          cmd.resetStore();
          break;
        case "level":
          System.out.println(cmd.getState());
          break;
        case "miners":
          cmd.printfMiners();
          break;
        case "mining":
          System.out.println(cmd.getState());
          break;
        case "state":
          System.out.println(cmd.getState());
          break;
        case "disconnect":
          System.out.println(cmd.disConnectMinerChannel(value));
        case "rpc":
          System.out.println("default");
          break;
        default:
          System.out.println("default");
          break;
      }
    } catch (Exception e) {
      System.out.println("Please enter the correct instructions");
    }
  }

  public void processAccount(String value) {
    try {
      int num = StringUtils.getNum(value);
      System.out.println(cmd.account(num));
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processLastblocks(String value) {
    try {
      int num = StringUtils.getNum(value);
      System.out.println("Print " + num + " lastblocks");
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processMainblocks(String value) {
    try {
      int num = StringUtils.getNum(value);
      System.out.println(cmd.mainblocks(num));
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processMinedblocks(String value) {
    try {
      int num = StringUtils.getNum(value);
      System.out.println(cmd.minedblocks(num));
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processBalance(String address) {
    try {
      byte[] hash = null;
      if (address != null && address.length() == 32) {
        hash = address2Hash(address);
      } else {
        hash = StringUtils.getHash(address);
      }
      System.out.println(cmd.balance(hash));
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processBlock(String address) {
    try {
      byte[] hash;
      // 作为地址
      if (address.length() == 32) {
        hash = address2Hash(address);
      } else {
        hash = StringUtils.getHash(address);
      }
      if (hash == null) {
        System.out.println("No param");
        return;
      }
      System.out.println("Print block info:" + Hex.toHexString(hash));
      System.out.println(cmd.block(hash));
    } catch (Exception e) {
      System.out.println("Argument is incorrect.");
    }
  }

  public void processNet(String command) {
    if (command == null) {
      return;
    }
    if ("conn".equals(command)) {
      System.out.println(cmd.listConnect());
    } else if ("help".equals(command)) {
      System.out.println(
          "Commands:\n"
              + "  conn                          - list connections\n"
              + "  connect ip:port               - connect to this host\n"
              + "  help                          - print this help");
    } else {
      String[] args = command.split(" ");
      if ("connect".equals(args[0]) && args.length == 2) {
        System.out.println("connect to :" + args[1]);
        Matcher m = p.matcher(args[1]);
        if (m.matches()) {
          String host = m.group(1);
          int port = Integer.parseInt(m.group(2));
          cmd.connect(host, port);
        } else {
          System.out.println("Node ip:port Error");
        }
      }
    }
  }

  public void processStats() {
    System.out.println(cmd.stats());
  }

  public void processXfer(String command) {

    System.out.println(cmd.xfer(command));
  }

  public void onStop() {
    stopFlag = true;
  }

  public boolean getStopFlag() {
    return stopFlag;
  }
}
