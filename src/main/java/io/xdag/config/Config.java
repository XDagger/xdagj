package io.xdag.config;

import io.xdag.cli.ShellCommand;
import io.xdag.crypto.DnetKeys;
import io.xdag.crypto.jni.Native;
import lombok.Data;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.math.BigDecimal;

@Data
public class Config {

  public static boolean MainNet = false;

  /** 配置节点监听地址 */
  private String nodeIp = "127.0.0.1";

  private int nodePort = 30303;

  /** 矿池地址 */
  private String poolIp = "127.0.0.1";

  /** 矿池的端口 */
  private int poolPort = 50505;

  private int connectionTimeout = 10;

  private int channelReadTimeout = 10;

  private String nodeKeyPrivate = "123";

  private String nodeKeyPassword = "123456";

  private boolean nodeDiscoveryEnabled = true;

  private boolean nodeSyncEnabled = true;

  private int nodeMaxActive = 100;

  private int nodeSyncCount = 10;

  private boolean enableRefresh = false;

  /** 一个矿池最多允许接入的channel */
  private int globalMinerLimit = 8192;

  /** 允许最大的接入连接 */
  private int globalMinerChannelLimit = 8192;

  /** 同一ip地址允许同时接入的客户端数量 */
  private int maxConnectPerIp = 5;

  /** 拥有相同地址块的矿工最多允许同时在线的数量 */
  private int maxMinerPerAccount = 5;

  /** 同一个channel 某一个任务种最多允许发share的次数 */
  private int maxShareCountPerChannel = 20;

  /** 矿池自己的收益占比 */
  private double poolRation = BigDecimal.valueOf(5).doubleValue();

  /** 出块矿工收益占比 */
  private double rewardRation = BigDecimal.valueOf(5).doubleValue();

  /** 基金会收益占比 */
  private double fundRation = BigDecimal.valueOf(5).doubleValue();

  /** 参与奖励的占比 */
  private double directRation = BigDecimal.valueOf(5).doubleValue();

  private byte[] dnetKeyBytes = new byte[2048];

  /** 配置存储root */
  public static String root = MainNet ? "./mainnet" : "./testnet";
  private String storeDir;
  private String storeBackupDir;
  private String whiteListDirTest;
  private String whiteListDir;
  /**存放网络接收到的新节点地址*/
  private String netDBDirTest;
  private String netDBDir;

  /** 用于测试加载已有区块数据 从C版本生成的数据 请将所需要的数据放在该目录下 */
  private String originStoreDir = "./testdate";

  private int TTL = 5;

  public static final String WHITELIST_URL_TESTNET =
      "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white-testnet.txt";
  public static final String WHITELIST_URL =
      "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white.txt";

  public final int MAX_CHANNELS = 1024;

  private boolean storeFromBackup = false;

  private int storeMaxOpenFiles = 1024;

  private int storeMaxThreads = 2;


  private DnetKeys xKeys;

  public Config() {}

  public void initKeys() throws Exception {
    InputStream inputStream = Native.class.getClassLoader().getResourceAsStream("dnet_keys.bin");
    if (inputStream == null) {
      throw new Exception("can not find dnet_key.bin file.");
    } else {
      xKeys = new DnetKeys();

      byte[] data = new byte[3072];
      IOUtils.read(inputStream, data);

      System.arraycopy(data, 0, xKeys.prv, 0, 1024);
      System.arraycopy(data, 1024, xKeys.pub, 0, 1024);
      System.arraycopy(data, 2048, xKeys.sect0_encoded, 0, 512);
      System.arraycopy(data, 2048 + 512, xKeys.sect0, 0, 512);

      Native.init();
      if (Native.load_dnet_keys(data, data.length) < 0) {
        throw new Exception("dnet crypt init failed");
      }

      if (Native.dnet_crypt_init() < 0) {
        throw new Exception("dnet crypt init failed");
      }
    }
  }

  public String getWhiteListDir() {
    if (MainNet) {
      return whiteListDir;
    } else {
      return whiteListDirTest;
    }
  }

  public void setPara(Config config, String[] args) {
    if (args == null || args.length == 0) {
      System.out.println("Use default configuration");
      return;
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-a":
          break;
        case "-c":
          break;
        case "-h":
          ShellCommand.printHelp();
          System.exit(0);
          break;
        case "-m":
          i++;
          // todo 设置挖矿的线程数
          break;
        case "-f":
          i++;
          Config.root = args[i];
          break;
        case "-t":
          Config.MainNet = false;
          break;
        case "-p":
          i++;
          config.setNode(config, args[i]);
          break;
        case "-P":
          i++;
          config.setPoolPara(config, args[i]);
          break;
        case "-r":
          // todo only load block but no run
          break;
        case "-s":
          i++;
          // todo bind the host for us
          break;
        default:
          System.out.println("Illegal instruction");
      }
    }
  }

  public void setNode(Config config, String host) {
    String[] args = host.split(":");
    config.nodeIp = args[0];
    config.nodePort = Integer.parseInt(args[1]);
  }

  public void setPoolPara(Config config, String para) {
    String[] args = para.split(":");

    if (args.length != 9) {
      ShellCommand.printHelp();
      throw new IllegalArgumentException("Illegal instruction");
    }
    config.setPoolIp(args[0]);
    config.setPoolPort(Integer.parseInt(args[1]));
    config.globalMinerLimit = Integer.parseInt(args[2]);
    config.maxConnectPerIp = Integer.parseInt(args[3]);
    config.maxMinerPerAccount = Integer.parseInt(args[4]);
    config.poolRation = Double.parseDouble(args[5]);
    config.rewardRation = Double.parseDouble(args[6]);
    config.directRation = Double.parseDouble(args[7]);
    config.fundRation = Double.parseDouble(args[8]);
  }

  /**
   * 设置存储的路径
   */
  public void changeDir(){
    //配置存储root
    root = Config.MainNet ? "./mainnet" : "./testnet";
    storeDir = root + "/rocksdb/xdagdb";
    storeBackupDir = root +  "/rocksdb/Xdagdb/backupdata";
    whiteListDirTest = root + "/netdb-white-testnet.txt";
    whiteListDir = root + "/netdb-white.txt";
    netDBDirTest = root + "/netdb-testnet.txt";
    netDBDir = root + "/netdb.txt";
  }
}
