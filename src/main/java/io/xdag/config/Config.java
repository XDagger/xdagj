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
package io.xdag.config;

import cn.hutool.setting.Setting;
import io.xdag.crypto.DnetKeys;
import io.xdag.crypto.jni.Native;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Data
public class Config implements Serializable {
    public static boolean MAINNET = false;
    /** 配置存储root */
    public static String root = MAINNET ? "mainnet" : "testnet";
    public static final String WHITELIST_URL_TESTNET = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white-testnet.txt";
    public static final String WHITELIST_URL = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white.txt";

    /** 保存得密钥文件 */
    public static final String DNET_KEY_FILE = Config.MAINNET?Config.root + "/dnet_key.dat":Config.root + "/dnet_key.dat";
    /** 钱包文件 */
    public static final String WALLET_KEY_FILE = Config.MAINNET?Config.root + "/wallet.dat":Config.root + "/wallet-testnet.dat";

    /** bip44 wallet file */
    public static final String BIP44_WALLET_KEYSTORE_FILE = Config.MAINNET?Config.root + "/keystore":Config.root + "/keystore-testnet";

    public final int MAX_CHANNELS = 1024;
    private final int connectionTimeout = 10000;
    private final int channelReadTimeout = 10000;
    /** 同一个channel 某一个任务种最多允许发share的次数 */
    private final int maxShareCountPerChannel = 20;
    private final int storeMaxOpenFiles = 1024;
    private final int storeMaxThreads = 1;
    /** telnet监听地址 */
    private String telnetIp;
    private int telnetPort;

    /** 配置节点监听地址 */
    private String nodeIp;
    private int nodePort;
    /** 矿池地址 */
    private String poolIp;
    /** 矿池的端口 */
    private int poolPort;
    /** Pool Tag */
    private String poolTag;
    /** 一个矿池最多允许接入的矿工数量 */
    private int globalMinerLimit;
    /** 允许最大的接入连接 g_max_connections_count */
    private int globalMinerChannelLimit;
    /** 同一ip地址允许同时接入的客户端数量 */
    private int maxConnectPerIp;
    /** 拥有相同地址块的矿工最多允许同时在线的数量 g_connections_per_miner_limit */
    private int maxMinerPerAccount;
    public boolean isbootnode ;
    public int discoveryPort;
    private int libp2pPort;
    private String Privkey;
    private boolean nodeDiscoveryEnabled = true;
    private boolean nodeSyncEnabled = true;
    private int nodeMaxActive = 100;
    private int nodeSyncCount = 10;
    private boolean enableRefresh = false;
    /** 矿池自己的收益占比 */
    private double poolRation;
    /** 出块矿工收益占比 */
    private double rewardRation;
    /** 基金会收益占比 */
    private double fundRation;
    /** 参与奖励的占比 */
    private double directRation;
    private String storeDir;
    private String storeBackupDir;
    private String whiteListDirTest;
    private String whiteListDir;
    /** 存放网络接收到的新节点地址 */
    private String netDBDirTest;
    private String netDBDir;
    /** 存储相关 */
    private boolean storeFromBackup = false;
    /** 用于测试加载已有区块数据 从C版本生成的数据 请将所需要的数据放在该目录下 */
    private String originStoreDir = "./testdate";
    private int TTL = 5;
    private byte[] dnetKeyBytes = new byte[2048];
    private DnetKeys xKeys;

    private List<String> whiteIPList = new ArrayList<>(){};

    /** 等待超过10个epoch默认启动挖矿 **/
    public static long WAIT_EPOCH = 10;

    /** 奖励支付的周期**/
    public static int AWARD_EPOCH = 0xf;

    /** admin for telnet **/
    @Getter
    public String password;

    // BIP32
    public static final int BIP32_HEADER_P2PKH_PUB= 0x0488b21e; // The 4 byte header that serializes in base58 to "xpub".
    public static final int BIP32_HEADER_P2PKH_PRIV = 0x0488ade4; // The 4 byte header that serializes in base58 to "xprv"

    public int dumpedPrivateKeyHeader = 128;
    public int addressHeader = 0;

    public Config() {
        getSetting();
    }

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
        if (MAINNET) {
            return whiteListDir;
        } else {
            return whiteListDirTest;
        }
    }

    public void changePara(Config config, String[] args) {
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
            case "-m":
                i++;
                // todo 设置挖矿的线程数
                break;
            case "-f":
                i++;
                Config.root = args[i];
                break;
            case "-t":
                Config.MAINNET = false;
                break;
            case "-p":
                i++;
                config.changeNode(config, args[i]);
                break;
            case "-P":
                i++;
                config.changePoolPara(config, args[i]);
                break;
            case "-r":
                // todo only load block but no run
                break;
            case "-s":
                i++;
                // todo bind the host for us
                break;
            case "-tag":
                config.poolTag = StringUtils.substring(args[i+1], 0, 31);
                break;
            default:
                System.out.println("Illegal instruction");
            }
        }
    }

    public void changeNode(Config config, String host) {
        String[] args = host.split(":");
        config.nodeIp = args[0];
        config.nodePort = Integer.parseInt(args[1]);
    }

    /** 设置矿池的分配奖励 */
    public void changePoolPara(Config config, String para) {
        String[] args = para.split(":");
        if (args.length != 9) {
            throw new IllegalArgumentException("Illegal instruction");
        }
        config.setPoolIp(args[0]);
        config.setPoolPort(Integer.parseInt(args[1]));
        config.globalMinerChannelLimit = Integer.parseInt(args[2]);
        config.maxConnectPerIp = Integer.parseInt(args[3]);
        config.maxMinerPerAccount = Integer.parseInt(args[4]);
        config.poolRation = Double.parseDouble(args[5]);
        config.rewardRation = Double.parseDouble(args[6]);
        config.directRation = Double.parseDouble(args[7]);
        config.fundRation = Double.parseDouble(args[8]);
    }

    /** 设置存储的路径 */
    public void setDir() {
        // 配置存储root
        root = Config.MAINNET ? "./mainnet" : "./testnet";
        storeDir = root + "/rocksdb/xdagdb";
        storeBackupDir = root + "/rocksdb/xdagdb/backupdata";
        whiteListDirTest = root + "/netdb-white-testnet.txt";
        whiteListDir = root + "/netdb-white.txt";
        netDBDirTest = root + "/netdb-testnet.txt";
        netDBDir = root + "/netdb.txt";
    }

    public void getSetting() {
        Setting setting = new Setting("xdag.config");
        setting = setting.getSetting("default");

        telnetIp = setting.getStr("telnetIp");
        telnetPort = setting.getInt("telnetPort");

        nodeIp = setting.getStr("nodeIp");
        nodePort = setting.getInt("nodePort");

        poolIp = setting.getStr("poolIp");
        poolPort = setting.getInt("poolPort");

        libp2pPort = setting.getInt("libp2pPort");
        isbootnode = setting.getBool("isbootnode");

        discoveryPort = setting.getInt("discoveryPort");

        Privkey = setting.getStr("libp2pPrivkey");
        poolTag = setting.getOrDefault("poolTag", "XdagJ");

        poolRation = setting.getInt("poolRation");
        rewardRation = setting.getInt("rewardRation");
        fundRation = setting.getInt("fundRation");
        directRation = setting.getInt("directRation");

        globalMinerLimit = setting.getInt("globalMinerLimit");
        globalMinerChannelLimit = setting.getInt("globalMinerChannelLimit");
        maxConnectPerIp = setting.getInt("maxConnectPerIp");
        maxMinerPerAccount = setting.getInt("maxMinerPerAccount");

        password = setting.getStr("password");

        String[] list = setting.getStrings("whiteIPs");
        if (list != null) {
            log.debug("{} IP access", list.length);
            whiteIPList.addAll(Arrays.asList(list));
        }
    }
}
