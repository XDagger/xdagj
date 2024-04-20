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
import io.xdag.Network;
import io.xdag.config.spec.*;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.net.Capability;
import io.xdag.net.CapabilityTreeSet;
import io.xdag.net.message.MessageCode;
import io.xdag.rpc.modules.ModuleDescription;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;

import java.net.InetSocketAddress;
import java.util.*;

@Slf4j
@Getter
@Setter
public class AbstractConfig implements Config, AdminSpec, NodeSpec, WalletSpec, RPCSpec, SnapshotSpec, RandomxSpec, FundSpec {

    protected String configName;

    // =========================
    // Admin spec
    // =========================
    protected String telnetIp = "127.0.0.1";
    protected int telnetPort = 7001;
    protected String telnetPassword;

    // =========================
    // Pool websocket spec
    // =========================

    protected int WebsocketServerPort;

    protected int maxShareCountPerChannel = 20;
    protected int awardEpoch = 0xf;
    protected int waitEpoch = 32;
    // =========================
    // foundation spec
    // =========================
    protected String fundAddress;
    protected double fundRation;
    protected double nodeRation;
    // =========================
    // Network
    // =========================
    protected Network network;
    protected short networkVersion;
    protected int netMaxOutboundConnections = 128;
    protected int netMaxInboundConnections = 512;
    protected int netMaxInboundConnectionsPerIp = 5;
//    protected int netMaxMessageQueueSize = 4096;
    protected int netMaxFrameBodySize = 128 * 1024;
    protected int netMaxPacketSize = 16 * 1024 * 1024;
    protected int netRelayRedundancy = 8;
    protected int netHandshakeExpiry = 5 * 60 * 1000;
    protected int netChannelIdleTimeout = 2 * 60 * 1000;

    protected Set<MessageCode> netPrioritizedMessages = new HashSet<>(Arrays.asList(
            MessageCode.NEW_BLOCK,
            MessageCode.BLOCK_REQUEST,
            MessageCode.BLOCKS_REQUEST));

    protected String nodeIp;
    protected int nodePort;
    protected String nodeTag;
    protected int maxConnections = 1024;
    protected int maxInboundConnectionsPerIp = 8;
    protected int connectionTimeout = 10000;
    protected int connectionReadTimeout = 10000;
    protected boolean enableTxHistory = false;
    protected long txPageSizeLimit = 500;
    protected boolean enableGenerateBlock = false;

    protected String rootDir;
    protected String storeDir;
    protected String storeBackupDir;
    protected String whiteListDir;
    protected String rejectAddress;
    protected String netDBDir;

    protected int storeMaxOpenFiles = 1024;
    protected int storeMaxThreads = 1;
    protected boolean storeFromBackup = false;
    protected String originStoreDir = "./testdate";

    protected String whitelistUrl;
    protected boolean enableRefresh = false;
    protected String walletKeyFile;

    protected int TTL = 5;
    protected List<InetSocketAddress> whiteIPList = Lists.newArrayList();
    protected List<String> poolWhiteIPList = Lists.newArrayList();

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
    protected long snapshotTime;
    protected boolean isSnapshotJ;

    // =========================
    // Randomx Config
    // =========================
    protected boolean flag;

    protected AbstractConfig(String rootDir, String configName, Network network, short networkVersion) {
        this.rootDir = rootDir;
        this.configName = configName;
        this.network = network;
        this.networkVersion = networkVersion;
        getSetting();
        setDir();
    }

    public void setDir() {
        storeDir = getRootDir() + "/rocksdb/xdagdb";
        storeBackupDir = getRootDir() + "/rocksdb/xdagdb/backupdata";
        whiteListDir = getRootDir() + "/netdb-white.txt";
        netDBDir = getRootDir() + "/netdb.txt";
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
    public FundSpec getFundSpec() {
        return this;
    }

    @Override
    public Network getNetwork() {
        return this.network;
    }

    @Override
    public short getNetworkVersion() {
        return this.networkVersion;
    }

    @Override
    public String getNodeTag() {
        return this.nodeTag;
    }

    @Override
    public Set<MessageCode> getNetPrioritizedMessages() {
        return this.netPrioritizedMessages;
    }

    @Override
    public String getClientId() {
        return String.format("%s/v%s-%s/%s",
                Constants.CLIENT_NAME,
                Constants.CLIENT_VERSION,
                SystemUtils.OS_NAME,
                SystemUtils.OS_ARCH);
    }

    @Override
    public CapabilityTreeSet getClientCapabilities() {
        return CapabilityTreeSet.of(Capability.FULL_NODE, Capability.LIGHT_NODE);
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

        telnetIp = config.hasPath("admin.telnet.ip") ? config.getString("admin.telnet.ip") : "127.0.0.1";
        telnetPort = config.hasPath("admin.telnet.port") ? config.getInt("admin.telnet.port") : 6001;
        telnetPassword = config.getString("admin.telnet.password");

        poolWhiteIPList = config.hasPath("pool.whiteIPs") ? config.getStringList("pool.whiteIPs") : Collections.singletonList("127.0.0.1");
        log.info("Pool whitelist {}. Any IP allowed? {}", poolWhiteIPList, poolWhiteIPList.contains("0.0.0.0"));
        WebsocketServerPort = config.hasPath("pool.ws.port") ? config.getInt("pool.ws.port") : 7001;
        nodeIp = config.hasPath("node.ip") ? config.getString("node.ip") : "127.0.0.1";
        nodePort = config.hasPath("node.port") ? config.getInt("node.port") : 8001;
        nodeTag = config.hasPath("node.tag") ? config.getString("node.tag") : "xdagj";
        rejectAddress = config.hasPath("node.reject.transaction.address") ? config.getString("node.reject.transaction.address") : "";
        maxInboundConnectionsPerIp = config.getInt("node.maxInboundConnectionsPerIp");
        enableTxHistory = config.hasPath("node.transaction.history.enable") && config.getBoolean("node.transaction.history.enable");
        enableGenerateBlock = config.hasPath("node.generate.block.enable") && config.getBoolean("node.generate.block.enable");
        txPageSizeLimit = config.hasPath("node.transaction.history.pageSizeLimit") ? config.getInt("node.transaction.history.pageSizeLimit") : 500;
        fundAddress = config.hasPath("fund.address") ? config.getString("fund.address") : "4duPWMbYUgAifVYkKDCWxLvRRkSByf5gb";
        fundRation = config.hasPath("fund.ration") ? config.getDouble("fund.ration") : 5;
        nodeRation = config.hasPath("node.ration") ? config.getDouble("node.ration") : 5;
        List<String> whiteIpList = config.getStringList("node.whiteIPs");
        log.debug("{} IP access", whiteIpList.size());
        for (String addr : whiteIpList) {
            String ip = addr.split(":")[0];
            int port = Integer.parseInt(addr.split(":")[1]);
            whiteIPList.add(new InetSocketAddress(ip, port));
        }
        // rpc
        rpcEnabled = config.hasPath("rpc.enabled") && config.getBoolean("rpc.enabled");
        if (rpcEnabled) {
            rpcHost = config.hasPath("rpc.http.host") ? config.getString("rpc.http.host") : "127.0.0.1";
            rpcPortHttp = config.hasPath("rpc.http.port") ? config.getInt("rpc.http.port") : 10001;
            rpcPortWs = config.hasPath("rpc.ws.port") ? config.getInt("rpc.ws.port") : 10002;
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
                case "-r":
                    // todo only load block but no run
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

    @Override
    public int getNetMaxFrameBodySize() {
        return this.netMaxFrameBodySize;
    }

    @Override
    public int getNetMaxPacketSize() {
        return this.netMaxPacketSize;
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
    public List<String> getPoolWhiteIPList() {
        return poolWhiteIPList;
    }

    @Override
    public int getWebsocketServerPort() {
        return WebsocketServerPort;
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
    public boolean getEnableTxHistory() {
        return enableTxHistory;
    }

    @Override
    public long getTxPageSizeLimit() {
        return txPageSizeLimit;
    }

    @Override
    public boolean getEnableGenerateBlock() {
        return enableGenerateBlock;
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
