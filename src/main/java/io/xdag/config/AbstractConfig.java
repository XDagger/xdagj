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
import io.xdag.Network;
import io.xdag.config.spec.*;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
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

    // Admin configuration
    protected String adminTelnetIp = "127.0.0.1";
    protected int adminTelnetPort = 7001;
    protected String adminTelnetPassword;

    // Pool websocket configuration 
    protected int websocketServerPort;
    protected int maxShareCountPerChannel = 20;
    protected int awardEpoch = 0xf;
    protected int waitEpoch = 32;

    // Foundation configuration
    protected String fundAddress;
    protected double fundRation;
    protected double nodeRation;

    // Network configuration
    protected Network network;
    protected short networkVersion;
    protected int netMaxOutboundConnections = 128;
    protected int netMaxInboundConnections = 512;
    protected int netMaxInboundConnectionsPerIp = 5;
    protected int netMaxFrameBodySize = 128 * 1024;
    protected int netMaxPacketSize = 16 * 1024 * 1024;
    protected int netRelayRedundancy = 8;
    protected int netHandshakeExpiry = 5 * 60 * 1000;
    protected int netChannelIdleTimeout = 2 * 60 * 1000;

    // Node configuration
    protected String nodeIp;
    protected int nodePort;
    protected String nodeTag;
    protected int maxConnections = 50;
    protected int minConnections = 8;
    protected int maxInboundConnections = 40;
    protected int maxInboundConnectionsPerIp = 8;
    protected List<InetSocketAddress> seedNodes = Lists.newArrayList();
    protected List<InetSocketAddress> trustedNodes = Lists.newArrayList();
    protected boolean discoveryEnabled = true;
    protected boolean allowPrivateAddresses = false;
    protected boolean allowPrivateAddressesConfigured = false;
    protected String nodeBindIp = "";
    protected String nodeKeyFile;
    protected int connectionTimeout = 10000;
    protected int connectionReadTimeout = 10000;
    protected boolean enableTxHistory = false;
    protected long txPageSizeLimit = 500;
    protected boolean enableGenerateBlock = false;

    // Storage configuration
    protected String rootDir;
    protected String storeDir;
    protected String storeBackupDir;
    protected String rejectAddress;

    protected int storeMaxOpenFiles = 1024;
    protected int storeMaxThreads = 1;
    protected boolean storeFromBackup = false;
    protected String originStoreDir = "./testdate";

    // Whitelist configuration
    protected String walletKeyFile;

    protected int TTL = 5;
    protected List<String> poolWhiteIPList = Lists.newArrayList();

    // Wallet configuration
    protected String walletFilePath;

    // XDAG configuration
    protected long xdagEra;
    protected XdagField.FieldType xdagFieldHeader;
    protected XAmount mainStartAmount;
    protected long apolloForkHeight;
    protected XAmount apolloForkAmount;

    // RPC configuration
    protected boolean rpcHttpEnabled = false;
    protected String rpcHttpHost = "127.0.0.1";
    protected int rpcHttpPort = 10001;
    protected boolean rpcEnableHttps = false;
    protected String rpcHttpCorsOrigins = "*";
    protected String  rpcHttpsCertFile;
    protected String rpcHttpsKeyFile;
    protected int rpcHttpMaxContentLength = 1024 * 1024; // 1MB

    // RPC netty configuration
    protected int rpcHttpBossThreads = 1;
    protected int rpcHttpWorkerThreads = 4; // 0 means use Netty default (2 * CPU cores)


    // Snapshot configuration
    protected boolean snapshotEnabled = false;
    protected long snapshotHeight;
    protected long snapshotTime;
    protected boolean isSnapshotJ;

    // RandomX configuration
    protected boolean flag;

    /**
     * Epoch from which the open-network hardening fork is in force; {@link Constants#OPEN_NET_FORK_NOT_SCHEDULED}
     * means the fork is not scheduled. Networks set their own default; test networks may override it with
     * {@code consensus.opennet.forkEpoch} (a negative value means "not scheduled").
     */
    protected long openNetForkEpoch = Constants.OPEN_NET_FORK_NOT_SCHEDULED;
    /**
     * Value of {@code consensus.opennet.forkEpoch} in the config file, or null if absent.
     */
    protected Long configuredOpenNetForkEpoch;

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
        if (nodeKeyFile == null) {
            nodeKeyFile = getRootDir() + "/node.key";
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
    public String getClientId() {
        return String.format("%s/v%s-%s/%s",
                Constants.CLIENT_NAME,
                Constants.CLIENT_VERSION,
                SystemUtils.OS_NAME,
                SystemUtils.OS_ARCH);
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
        applySettings(ConfigFactory.load(getConfigName()));
    }

    /** Reads the settings from a parsed configuration (the file of this network, or anything a test builds). */
    public void applySettings(com.typesafe.config.Config config) {
        adminTelnetIp = config.hasPath("admin.telnet.ip") ? config.getString("admin.telnet.ip") : "127.0.0.1";
        adminTelnetPort = config.hasPath("admin.telnet.port") ? config.getInt("admin.telnet.port") : 6001;
        adminTelnetPassword = config.getString("admin.telnet.password");

        poolWhiteIPList = config.hasPath("pool.whiteIPs") ? config.getStringList("pool.whiteIPs") : Collections.singletonList("127.0.0.1");
        log.info("Pool whitelist {}. Any IP allowed? {}", poolWhiteIPList, poolWhiteIPList.contains("0.0.0.0"));
        websocketServerPort = config.hasPath("pool.ws.port") ? config.getInt("pool.ws.port") : 7001;
        nodeIp = config.hasPath("node.ip") ? config.getString("node.ip") : "127.0.0.1";
        nodePort = config.hasPath("node.port") ? config.getInt("node.port") : 8001;
        nodeTag = config.hasPath("node.tag") ? config.getString("node.tag") : "xdagj";
        rejectAddress = config.hasPath("node.reject.transaction.address") ? config.getString("node.reject.transaction.address") : "";
        if (config.hasPath("node.maxInboundConnectionsPerIp")) {
            maxInboundConnectionsPerIp = config.getInt("node.maxInboundConnectionsPerIp");
        }
        enableTxHistory = config.hasPath("node.transaction.history.enable") && config.getBoolean("node.transaction.history.enable");
        enableGenerateBlock = config.hasPath("node.generate.block.enable") && config.getBoolean("node.generate.block.enable");
        txPageSizeLimit = config.hasPath("node.transaction.history.pageSizeLimit") ? config.getInt("node.transaction.history.pageSizeLimit") : 500;
        fundAddress = config.hasPath("fund.address") ? config.getString("fund.address") : "4duPWMbYUgAifVYkKDCWxLvRRkSByf5gb";
        fundRation = config.hasPath("fund.ration") ? config.getDouble("fund.ration") : 5;
        nodeRation = config.hasPath("node.ration") ? config.getDouble("node.ration") : 5;
        // Peers. There is no whitelist any more: seeds are entry points, trusted peers are kept connected and
        // never banned, and while the network is closed (open-network fork not in force) they are all there is.
        if (config.hasPath("node.seeds")) {
            seedNodes.clear();
            seedNodes.addAll(parseAddresses(config.getStringList("node.seeds"), "node.seeds"));
        }
        if (config.hasPath("node.trustedPeers")) {
            trustedNodes.clear();
            trustedNodes.addAll(parseAddresses(config.getStringList("node.trustedPeers"), "node.trustedPeers"));
        }
        if (config.hasPath("node.whiteIPs")) {
            // the former whitelist: its entries are what an operator would put into both lists today
            List<InetSocketAddress> legacy = parseAddresses(config.getStringList("node.whiteIPs"), "node.whiteIPs");
            log.warn("node.whiteIPs is no longer a whitelist; its {} entries are used as seeds and trusted peers "
                    + "(see docs/OPEN_NETWORK.md)", legacy.size());
            for (InetSocketAddress a : legacy) {
                if (!seedNodes.contains(a)) {
                    seedNodes.add(a);
                }
                if (!trustedNodes.contains(a)) {
                    trustedNodes.add(a);
                }
            }
        }
        discoveryEnabled = !config.hasPath("node.discovery.enabled") || config.getBoolean("node.discovery.enabled");
        if (config.hasPath("node.allowPrivateAddresses")) {
            allowPrivateAddresses = config.getBoolean("node.allowPrivateAddresses");
            allowPrivateAddressesConfigured = true;
        }
        nodeBindIp = config.hasPath("node.bindIp") ? config.getString("node.bindIp") : "";
        if (config.hasPath("node.keyFile")) {
            nodeKeyFile = config.getString("node.keyFile");
        }
        if (config.hasPath("node.minConnections")) {
            minConnections = config.getInt("node.minConnections");
        }
        if (config.hasPath("node.maxConnections")) {
            maxConnections = config.getInt("node.maxConnections");
        }
        if (config.hasPath("node.maxInboundConnections")) {
            maxInboundConnections = config.getInt("node.maxInboundConnections");
        }
        // RPC configuration
        rpcHttpEnabled = config.hasPath("rpc.http.enabled") && config.getBoolean("rpc.http.enabled");
        if (rpcHttpEnabled) {
            rpcHttpHost = config.hasPath("rpc.http.host") ? config.getString("rpc.http.host") : "127.0.0.1";
            rpcHttpPort = config.hasPath("rpc.http.port") ? config.getInt("rpc.http.port") : 10001;
        }
        flag = config.hasPath("randomx.flags.fullmem") && config.getBoolean("randomx.flags.fullmem");
        if (config.hasPath("consensus.opennet.forkEpoch")) {
            long epoch = config.getLong("consensus.opennet.forkEpoch");
            configuredOpenNetForkEpoch = epoch < 0 ? Constants.OPEN_NET_FORK_NOT_SCHEDULED : epoch;
        }

    }

    /** Parses {@code host:port} entries; entries that do not parse or resolve are reported and skipped. */
    protected static List<InetSocketAddress> parseAddresses(List<String> entries, String what) {
        List<InetSocketAddress> result = new ArrayList<>();
        for (String entry : entries) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                log.warn("{}: '{}' is not host:port, ignored", what, entry);
                continue;
            }
            try {
                String host = entry.substring(0, colon).replace("[", "").replace("]", "");
                int port = Integer.parseInt(entry.substring(colon + 1));
                InetSocketAddress address = new InetSocketAddress(host, port);
                if (address.isUnresolved()) {
                    log.warn("{}: '{}' cannot be resolved, ignored", what, entry);
                    continue;
                }
                result.add(address);
            } catch (RuntimeException e) {
                log.warn("{}: '{}' is not usable ({}), ignored", what, entry, e.getMessage());
            }
        }
        return result;
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
                    // TODO: Set mining thread count
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
                    // TODO: Only load block but no run
                    break;
                case "-d":
                case "-t":
                    // Only devnet or testnet
                    break;
                default:
                    // log.error("Illegal instruction");
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
    public List<String> getPoolWhiteIPList() {
        return poolWhiteIPList;
    }

    @Override
    public int getWebsocketServerPort() {
        return websocketServerPort;
    }

    @Override
    public boolean isRpcHttpEnabled() {
        return rpcHttpEnabled;
    }

    @Override
    public String getRpcHttpHost() {
        return rpcHttpHost;
    }

    @Override
    public int getRpcHttpPort() { return rpcHttpPort;}

    @Override
    public boolean isRpcEnableHttps() {return rpcEnableHttps;}

    @Override
    public String getRpcHttpCorsOrigins() {return rpcHttpCorsOrigins;}

    @Override
    public int getRpcHttpMaxContentLength() {return rpcHttpMaxContentLength;}

    @Override
    public int getRpcHttpBossThreads() {return rpcHttpBossThreads;}

    @Override
    public int getRpcHttpWorkerThreads() {return rpcHttpWorkerThreads;}

    @Override
    public String getRpcHttpsCertFile() {return rpcHttpsCertFile;}

    @Override
    public String getRpcHttpsKeyFile() {return rpcHttpsKeyFile;}

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
