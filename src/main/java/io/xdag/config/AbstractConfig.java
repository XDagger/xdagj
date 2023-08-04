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

import com.google.common.collect.Lists;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import io.xdag.config.spec.AdminSpec;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.PoolSpec;
import io.xdag.config.spec.RPCSpec;
import io.xdag.config.spec.RandomxSpec;
import io.xdag.config.spec.SnapshotSpec;
import io.xdag.config.spec.WalletSpec;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.crypto.DnetKeys;
import io.xdag.rpc.modules.ModuleDescription;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Getter
@Setter
public class AbstractConfig implements Config, AdminSpec, PoolSpec, NodeSpec, WalletSpec, RPCSpec, SnapshotSpec, RandomxSpec {

    protected String configName;

    // =========================
    // Admin spec
    // =========================
    protected String telnetIp = "127.0.0.1";
    protected int telnetPort = 7001;
    protected String telnetPassword;

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
    protected String fundAddress;

    protected int globalMinerLimit;
    protected int globalMinerChannelLimit;
    protected int maxMinerPerAccount;
    protected int maxConnectPerIp;

    protected int maxShareCountPerChannel = 20;
    protected int awardEpoch = 0xf;
    protected int waitEpoch = 20;

    // =========================
    // Node spec
    // =========================
    protected String nodeIp;
    protected int nodePort;
    protected int maxConnections = 1024;
    protected int maxInboundConnectionsPerIp = 8;
    protected int connectionTimeout = 10000;
    protected int connectionReadTimeout = 10000;
    protected boolean enableTxHistory = false;
    protected boolean enableGenerateBlock = false;

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
    protected String walletKeyFile;

    protected int TTL = 5;
    protected byte[] dnetKeyBytes = new byte[2048];
    protected DnetKeys xKeys;
    protected List<InetSocketAddress> whiteIPList = Lists.newArrayList();


    // =========================
    // Libp2p spec
    // =========================
    protected int libp2pPort;
    protected boolean isBootnode;
    protected String libp2pPrivkey;
    protected List<String> bootnodes = Lists.newArrayList();

    // =========================
    // Wallet spec
    // =========================
    protected String walletFilePath;

    // =========================
    // Xdag spec
    // =========================
    protected long xdagEra;
    protected XdagField.FieldType xdagFieldHeader;
    protected XAmount mainStartAmount;
    protected long apolloForkHeight;
    protected XAmount apolloForkAmount;


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

    // =========================
    // Randomx Config
    // =========================
    protected boolean flag;

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
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("dnet_keys.bin");
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
    public RandomxSpec getRandomxSpec() {
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
        com.typesafe.config.Config config = ConfigFactory.load(getConfigName());

        telnetIp = config.hasPath("admin.telnet.ip")?config.getString("admin.telnet.ip"):"127.0.0.1";
        telnetPort = config.hasPath("admin.telnet.port")?config.getInt("admin.telnet.port"):6001;
        telnetPassword = config.getString("admin.telnet.password");

        poolIp = config.hasPath("pool.ip")?config.getString("pool.ip"):"127.0.0.1";
        poolPort = config.hasPath("pool.port")?config.getInt("pool.port"):7001;
        poolTag = config.hasPath("pool.tag")?config.getString("pool.tag"):"xdagj";

        poolRation = config.getInt("pool.poolRation");
        rewardRation = config.getInt("pool.rewardRation");
        fundRation = config.getInt("pool.fundRation");
        directRation = config.getInt("pool.directRation");
        fundAddress = config.hasPath("pool.fundAddress")?config.getString("pool.fundAddress"):"FQglVQtb60vQv2DOWEUL7yh3smtj7g1s";

        nodeIp = config.hasPath("node.ip")?config.getString("node.ip"):"127.0.0.1";
        nodePort = config.hasPath("node.port")?config.getInt("node.port"):8001;
        maxInboundConnectionsPerIp = config.getInt("node.maxInboundConnectionsPerIp");
        enableTxHistory = config.hasPath("node.transaction.history.enable")?config.getBoolean("node.transaction.history.enable"):false;
        enableGenerateBlock = config.hasPath("node.generate.block.enable") && config.getBoolean("node.generate.block.enable");

        List<String> whiteIpList = config.getStringList("node.whiteIPs");
        log.debug("{} IP access", whiteIpList.size());
        for(String addr : whiteIpList) {
            String ip = addr.split(":")[0];
            int port = Integer.parseInt(addr.split(":")[1]);
            whiteIPList.add(new InetSocketAddress(ip,port));
        }

        libp2pPort = config.getInt("node.libp2p.port");
        libp2pPrivkey = config.getString("node.libp2p.privkey");
        isBootnode = config.getBoolean("node.libp2p.isbootnode");

        List<String> bootnodeList = config.getStringList("node.libp2p.bootnode");
        if (bootnodeList != null) {
            bootnodes.addAll(bootnodeList);
        }

        globalMinerLimit = config.getInt("miner.globalMinerLimit");
        globalMinerChannelLimit = config.getInt("miner.globalMinerChannelLimit");
        maxConnectPerIp = config.getInt("miner.maxConnectPerIp");
        maxMinerPerAccount = config.getInt("miner.maxMinerPerAccount");

        // rpc
        rpcEnabled = config.hasPath("rpc.enabled")?config.getBoolean("rpc.enabled"):false;
        if (rpcEnabled) {
            rpcHost = config.hasPath("rpc.http.host")?config.getString("rpc.http.host"):"127.0.0.1";
            rpcPortHttp = config.hasPath("rpc.http.port")?config.getInt("rpc.http.port"):10001;
            rpcPortWs = config.hasPath("rpc.ws.port")?config.getInt("rpc.ws.port"):10002;
        }
        flag = config.hasPath("randomx.flags.fullmem") && config.getBoolean("randomx.flags.fullmem");

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
                case "-c":
                case "-m":
                case "-s":
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
                case "-tag":
                    this.poolTag = StringUtils.substring(args[i + 1], 0, 31);
                    break;
                case "-d":
                case "-t":
                    // only devnet or testnet
                    break;
                default:
//                    log.error("Illegal instruction");
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
    public int getMaxInboundConnectionsPerIp() {
        return this.maxInboundConnectionsPerIp;
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

        List<ModuleDescription> modules = Lists.newArrayList();

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
    public boolean getRandomxFlag() {
        return flag;
    }

    @Override
    public boolean getEnableTxHistory() {return enableTxHistory;}

    @Override
    public boolean getEnableGenerateBlock() {return enableGenerateBlock;}

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
