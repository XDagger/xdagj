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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import io.xdag.config.spec.AdminSpec;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.PoolSpec;
import io.xdag.config.spec.RPCSpec;
import io.xdag.config.spec.SnapshotSpec;
import io.xdag.config.spec.WalletSpec;
import io.xdag.core.XdagField;
import io.xdag.crypto.DnetKeys;
import io.xdag.crypto.jni.Native;
import io.xdag.rpc.modules.ModuleDescription;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Getter
@Setter
public class AbstractConfig implements Config, AdminSpec, PoolSpec, NodeSpec, WalletSpec, RPCSpec, SnapshotSpec {

    protected String configName;

    // =========================
    // Admin spec
    // =========================
    protected String telnetIp = "127.0.0.1";
    protected int telnetPort = 7001;
    protected String password;

    // =========================
    // Mining Pool spec
    // =========================
    protected String poolIp;
    protected int poolPort;
    protected String poolTag;
    protected double poolRation;
    protected double rewardRation;
    protected double fundRation;
    protected double directRation;

    protected int globalMinerLimit;
    protected int globalMinerChannelLimit;
    protected int maxMinerPerAccount;
    protected int maxConnectPerIp;

    protected int maxShareCountPerChannel = 20;
    protected int awardEpoch = 0xf;
    protected int waitEpoch = 10;

    // =========================
    // Node spec
    // =========================
    protected String nodeIp;
    protected int nodePort;
    protected int maxConnections = 1024;
    protected int connectionTimeout = 10000;
    protected int connectionReadTimeout = 10000;

    protected String rootDir;
    protected String storeDir;
    protected String storeBackupDir;
    protected String whiteListDir;
    protected String netDBDir;

    protected int storeMaxOpenFiles = 1024;
    protected int storeMaxThreads = 1;
    protected boolean storeFromBackup = false;
    protected String originStoreDir = "./testdate";

    protected String whitelistUrl;
    protected boolean enableRefresh = false;
    protected String dnetKeyFile;
    protected String walletKeyFile;

    protected int TTL = 5;
    protected byte[] dnetKeyBytes = new byte[2048];
    protected DnetKeys xKeys;
    protected List<String> whiteIPList = new ArrayList<>() {
    };


    // =========================
    // Libp2p spec
    // =========================
    protected int libp2pPort;
    protected boolean isBootnode;
    protected String libp2pPrivkey;
    protected List<String> bootnodes = new ArrayList<>();

    // =========================
    // Wallet spec
    // =========================
    protected String walletFilePath;

    // =========================
    // Xdag spec
    // =========================
    protected long xdagEra;
    protected XdagField.FieldType xdagFieldHeader;
    protected long mainStartAmount;
    protected long apolloForkHeight;
    protected long apolloForkAmount;


    // =========================
    // Xdag RPC modules
    // =========================
    protected List<ModuleDescription> moduleDescriptions;
    protected boolean rpcEnabled = false;
    protected String rpcHost;
    protected int rpcPortHttp;
    protected int rpcPortWs;


    // =========================
    // Xdag Snapshot
    // =========================
    protected boolean snapshotEnabled = false;
    protected long snapshotHeight;
    protected long snapshotTime; // TODO：用于sync时的起始时间
    protected boolean isSnapshotJ;

    protected AbstractConfig(String rootDir, String configName) {
        this.rootDir = rootDir;
        this.configName = configName;

        getSetting();
        setDir();
    }

    public void setDir() {
        storeDir = getRootDir() + "/rocksdb/xdagdb";
        storeBackupDir = getRootDir() + "/rocksdb/xdagdb/backupdata";
        whiteListDir = getRootDir() + "/netdb-white.txt";
        netDBDir = getRootDir() + "/netdb.txt";
    }

    public void initKeys() throws Exception {
        InputStream inputStream = Native.class.getClassLoader().getResourceAsStream("dnet_keys.bin");
        if (inputStream == null) {
            throw new RuntimeException("can not find dnet_key.bin file.");
        } else {
            xKeys = new DnetKeys();
            byte[] data = new byte[3072];
            IOUtils.read(inputStream, data);
            System.arraycopy(data, 0, xKeys.prv, 0, 1024);
            System.arraycopy(data, 1024, xKeys.pub, 0, 1024);
            System.arraycopy(data, 2048, xKeys.sect0_encoded, 0, 512);
            System.arraycopy(data, 2048 + 512, xKeys.sect0, 0, 512);

            Native.init(this);
            if (Native.load_dnet_keys(data, data.length) < 0) {
                throw new Exception("dnet crypt init failed");
            }

            if (Native.dnet_crypt_init() < 0) {
                throw new Exception("dnet crypt init failed");
            }
        }
    }

    @Override
    public RPCSpec getRPCSpec() {
        return this;
    }

    @Override
    public SnapshotSpec getSnapshotSpec() {
        return this;
    }

    @Override
    public PoolSpec getPoolSpec() {
        return this;
    }

    @Override
    public NodeSpec getNodeSpec() {
        return this;
    }

    @Override
    public AdminSpec getAdminSpec() {
        return this;
    }

    @Override
    public WalletSpec getWalletSpec() {
        return this;
    }

    public void getSetting() {
        Setting setting = new Setting(getConfigName());
        setting = setting.getSetting("default");

        telnetIp = setting.getStr("telnetIp");
        telnetPort = setting.getInt("telnetPort");

        nodeIp = setting.getStr("nodeIp");
        nodePort = setting.getInt("nodePort");

        poolIp = setting.getStr("poolIp");
        poolPort = setting.getInt("poolPort");
        poolTag = setting.getOrDefault("poolTag", "XdagJ");

        poolRation = setting.getInt("poolRation");
        rewardRation = setting.getInt("rewardRation");
        fundRation = setting.getInt("fundRation");
        directRation = setting.getInt("directRation");

        globalMinerLimit = setting.getInt("globalMinerLimit");
        globalMinerChannelLimit = setting.getInt("globalMinerChannelLimit");
        maxConnectPerIp = setting.getInt("maxConnectPerIp");
        maxMinerPerAccount = setting.getInt("maxMinerPerAccount");

        libp2pPort = setting.getInt("libp2pPort");
        libp2pPrivkey = setting.getStr("libp2pPrivkey");
        isBootnode = setting.getBool("isbootnode");
        password = setting.getStr("password");

        String[] list = setting.getStrings("whiteIPs");
        if (list != null) {
            log.debug("{} IP access", list.length);
            whiteIPList.addAll(Arrays.asList(list));
        }
        String[] bootnodelist = setting.getStrings("bootnode");
        if (bootnodelist != null) {
            bootnodes.addAll(Arrays.asList(bootnodelist));
        }
        // rpc
        rpcEnabled = setting.getBool("isRPCEnabled") != null && setting.getBool("isRPCEnabled");
        if (rpcEnabled) {
            rpcHost = setting.getStr("rpcHost");
            rpcPortHttp = setting.getInt("rpcPort_http");
            rpcPortWs = setting.getInt("rpcPort_ws");
        }
    }

    @Override
    public void changePara(String[] args) {
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
                    this.rootDir = args[i];
                    break;
                case "-p":
                    i++;
                    this.changeNode(args[i]);
                    break;
                case "-P":
                    i++;
                    this.changePoolPara(args[i]);
                    break;
                case "-r":
                    // todo only load block but no run
                    break;
                case "-s":
                    i++;
                    // todo bind the host for us
                    break;
                case "-tag":
                    this.poolTag = StringUtils.substring(args[i + 1], 0, 31);
                    break;
                default:
                    log.error("Illegal instruction");
            }
        }
    }

    public void changeNode(String host) {
        String[] args = host.split(":");
        this.nodeIp = args[0];
        this.nodePort = Integer.parseInt(args[1]);
    }

    /**
     * 设置矿池的分配奖励
     */
    public void changePoolPara(String para) {
        String[] args = para.split(":");
        if (args.length != 9) {
            throw new IllegalArgumentException("Illegal instruction");
        }
        this.setPoolIp(args[0]);
        this.setPoolPort(Integer.parseInt(args[1]));
        this.globalMinerChannelLimit = Integer.parseInt(args[2]);
        this.maxConnectPerIp = Integer.parseInt(args[3]);
        this.maxMinerPerAccount = Integer.parseInt(args[4]);
        this.poolRation = Double.parseDouble(args[5]);
        this.rewardRation = Double.parseDouble(args[6]);
        this.directRation = Double.parseDouble(args[7]);
        this.fundRation = Double.parseDouble(args[8]);
    }

    @Override
    public boolean isBootnode() {
        return this.isBootnode;
    }

    @Override
    public void setDnetKeyBytes(byte[] dnetKeyBytes) {
        this.dnetKeyBytes = dnetKeyBytes;
    }

    @Override
    public boolean enableRefresh() {
        return this.enableRefresh;
    }

    @Override
    public List<ModuleDescription> getRpcModules() {

        if (!rpcEnabled) {
            return null;
        }

        if (this.moduleDescriptions != null) {
            return this.moduleDescriptions;
        }

        List<ModuleDescription> modules = new ArrayList<>();

        com.typesafe.config.Config configFromFiles = ConfigFactory.load("rpc_modules");
        List<? extends ConfigObject> list = configFromFiles.getObjectList("rpc.modules");

        for (ConfigObject configObject : list) {
            com.typesafe.config.Config configElement = configObject.toConfig();
            String name = configElement.getString("name");
            String version = configElement.getString("version");
            boolean enabled = configElement.getBoolean("enabled");
            List<String> enabledMethods = null;
            List<String> disabledMethods = null;

            if (configElement.hasPath("methods.enabled")) {
                enabledMethods = configElement.getStringList("methods.enabled");
            }

            if (configElement.hasPath("methods.disabled")) {
                disabledMethods = configElement.getStringList("methods.disabled");
            }

            modules.add(new ModuleDescription(name, version, enabled, enabledMethods, disabledMethods));
        }

        this.moduleDescriptions = modules;

        // TODO: get modules from config
//        String name = "xdag";
//        String version = "1.0";
//        boolean enabled = true;
//        List<String> enabledMethods = null;
//        List<String> disabledMethods = null;
//
//        modules.add(new ModuleDescription(name, version, enabled, enabledMethods, disabledMethods));
//        this.moduleDescriptions = modules;

        return modules;
    }

    @Override
    public boolean isRPCEnabled() {
        return rpcEnabled;
    }

    @Override
    public String getRPCHost() {
        return rpcHost;
    }

    @Override
    public int getRPCPortByHttp() {
        return rpcPortHttp;
    }

    @Override
    public int getRPCPortByWebSocket() {
        return rpcPortWs;
    }

    @Override
    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    @Override
    public boolean isSnapshotJ() {
        return isSnapshotJ;
    }

    @Override
    public long getSnapshotHeight() {
        return snapshotHeight;
    }

    @Override
    public void setSnapshotJ(boolean isSnapshot) {
        this.isSnapshotJ = isSnapshot;
    }

    @Override
    public void snapshotEnable() {
        snapshotEnabled = true;
    }

    @Override
    public long getSnapshotTime() {
        return snapshotTime;
    }
}
