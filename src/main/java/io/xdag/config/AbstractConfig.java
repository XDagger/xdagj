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

import static io.xdag.Network.DEVNET;
import static io.xdag.Network.MAINNET;
import static io.xdag.Network.TESTNET;
import static io.xdag.core.Fork.APOLLO_FORK;
import static io.xdag.core.XUnit.MILLI_XDAG;
import static io.xdag.core.XUnit.XDAG;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.apache.commons.lang3.SystemUtils;

import com.google.common.collect.Lists;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;

import io.xdag.Network;
import io.xdag.config.spec.AdminSpec;
import io.xdag.config.spec.DagSpec;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.RPCSpec;
import io.xdag.config.spec.RandomxSpec;
import io.xdag.config.spec.SnapshotSpec;
import io.xdag.core.Fork;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.net.Capability;
import io.xdag.net.CapabilityTreeSet;
import io.xdag.net.message.MessageCode;
import io.xdag.rpc.modules.ModuleDescription;
import io.xdag.utils.exception.UnreachableException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class AbstractConfig implements Config, DagSpec, AdminSpec, NodeSpec, RPCSpec, SnapshotSpec, RandomxSpec {

    // =========================
    // Dag spec
    // =========================
    protected XAmount minTransactionFee = XAmount.of(100, MILLI_XDAG);
    protected XAmount maxMainBlockTransactionFee = XAmount.of(100, XDAG);
    protected long epochTime = 64 * 1000;
    protected File rootDir;

    // =========================
    // Forks
    // =========================
    protected boolean forkApolloEnabled = false;

    // =========================
    // Node spec
    // =========================
    protected String nodeIp;
    protected int nodePort;
    protected String nodeTag;
    protected int maxConnections = 1024;
    protected int maxInboundConnectionsPerIp = 8;
    protected int connectionTimeout = 4000;
    protected int connectionReadTimeout = 10000;
    protected boolean enableTxHistory = false;
    protected long txPageSizeLimit = 500;
    protected boolean enableGenerateBlock = false;

    // =========================
    // Txpool Spec
    // =========================
    protected long maxTxPoolTimeDrift = TimeUnit.HOURS.toMillis(2);


    // =========================
    // Sync spec
    // =========================
    protected long syncDownloadTimeout = 1_000L;
    protected int syncMaxQueuedJobs = 10_000;
    protected int syncMaxPendingJobs = 200;
    protected int syncMaxPendingBlocks = 2_000;
    protected boolean syncDisconnectOnInvalidBlock = false;
    protected boolean syncFastSync = false;

    // =========================
    // Admin spec
    // =========================
    protected String telnetIp = "127.0.0.1";
    protected int telnetPort = 7001;
    protected String telnetPassword;

    // =========================
    // Network
    // =========================
    protected Network network;
    protected short networkVersion;
    protected int netMaxOutboundConnections = 128;
    protected int netMaxInboundConnections = 512;
    protected int netMaxInboundConnectionsPerIp = 5;
    protected int netMaxMessageQueueSize = 4096;
    protected int netMaxFrameBodySize = 128 * 1024;
    protected int netMaxPacketSize = 16 * 1024 * 1024;
    protected int netRelayRedundancy = 8;
    protected int netHandshakeExpiry = 5 * 60 * 1000;
    protected int netChannelIdleTimeout = 2 * 60 * 1000;

    protected Set<MessageCode> netPrioritizedMessages = new HashSet<>(Arrays.asList(
            MessageCode.EPOCH_BLOCK));


    protected String whiteListDir;
    protected String netDBDir;
    protected String whitelistUrl;
    protected boolean enableRefresh = false;
    protected List<InetSocketAddress> whiteIPList = Lists.newArrayList();

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

    protected AbstractConfig(String rootDir, Network network, short networkVersion) {
        this.rootDir = new File(rootDir);
        this.network = network;
        this.networkVersion = networkVersion;
        getSetting();
    }

    @Override
    public File rootDir() {
        return this.rootDir;
    }

    @Override
    public File chainDir() {
        return chainDir(network);
    }

    @Override
    public File chainDir(Network network) {
        return new File(rootDir, Constants.CHAIN_DIR + File.separator + network.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public File configDir() {
        return new File(rootDir, Constants.CONFIG_DIR);
    }

    @Override
    public File walletDir() {
        return new File(rootDir, Constants.WALLET_DIR);
    }

    @Override
    public File logDir() {
        return new File(rootDir, Constants.LOG_DIR);
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
    public int getWaitEpoch() {
        return 0;
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
        return CapabilityTreeSet.of(Capability.FULL_NODE, Capability.FAST_SYNC);
    }

    @Override
    public DagSpec getDagSpec() {
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

    public void getSetting() {
        com.typesafe.config.Config config = ConfigFactory.load(String.format("xdag-%s", network.label()));

        telnetIp = config.hasPath("admin.telnet.ip")?config.getString("admin.telnet.ip"):"127.0.0.1";
        telnetPort = config.hasPath("admin.telnet.port")?config.getInt("admin.telnet.port"):6001;
        telnetPassword = config.getString("admin.telnet.password");

        nodeIp = config.hasPath("node.ip")?config.getString("node.ip"):"127.0.0.1";
        nodePort = config.hasPath("node.port")?config.getInt("node.port"):8001;
        nodeTag = config.hasPath("node.tag")?config.getString("node.tag"):"xdagj";
        maxInboundConnectionsPerIp = config.getInt("node.maxInboundConnectionsPerIp");
        enableTxHistory = config.hasPath("node.transaction.history.enable")?config.getBoolean("node.transaction.history.enable"):false;
        enableGenerateBlock = config.hasPath("node.generate.block.enable") && config.getBoolean("node.generate.block.enable");
        txPageSizeLimit = config.hasPath("node.transaction.history.pageSizeLimit")?config.getInt("node.transaction.history.pageSizeLimit"):500;

        List<String> whiteIpList = config.getStringList("node.whiteIPs");
        log.debug("{} IP access", whiteIpList.size());
        for(String addr : whiteIpList) {
            String ip = addr.split(":")[0];
            int port = Integer.parseInt(addr.split(":")[1]);
            whiteIPList.add(new InetSocketAddress(ip,port));
        }

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
    public int getNetMaxFrameBodySize() {
        return this.netMaxFrameBodySize;
    }

    @Override
    public int getNetMaxPacketSize() { return this.netMaxPacketSize; }

    @Override
    public int getMaxInboundConnectionsPerIp() {
        return this.maxInboundConnectionsPerIp;
    }

    @Override
    public int getNetHandshakeExpiry() { return this.netHandshakeExpiry; }

    @Override
    public boolean enableRefresh() {
        return this.enableRefresh;
    }

    @Override
    public long syncDownloadTimeout() {
        return this.syncDownloadTimeout;
    }

    @Override
    public int syncMaxQueuedJobs() {
        return this.syncMaxQueuedJobs;
    }

    @Override
    public int syncMaxPendingJobs() {
        return this.syncMaxPendingJobs;
    }

    @Override
    public int syncMaxPendingBlocks() {
        return this.syncMaxPendingBlocks;
    }

    @Override
    public boolean syncDisconnectOnInvalidBlock() {
        return false;
    }

    @Override
    public boolean syncFastSync() {
        return false;
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
    public long getSnapshotHeight() {
        return snapshotHeight;
    }

    @Override
    public boolean getRandomxFlag() {
        return flag;
    }

    @Override
    public Map<Fork, Long> manuallyActivatedForks() {
        return Collections.emptyMap();
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
    public XAmount getMinTransactionFee() {
        return minTransactionFee;
    }

    @Override
    public XAmount getMaxMainBlockTransactionFee() {
        return maxMainBlockTransactionFee;
    }

    @Override
    public long getMaxTxPoolTimeDrift() {
        return maxTxPoolTimeDrift;
    }

    @Override
    public long getMaxTransactionDataSize(TransactionType type) {
        switch (type) {
        case COINBASE:
        case TRANSFER:
            return 128; // for memo
        default:
            throw new UnreachableException();
        }
    }

    @Override
    public XAmount getMainBlockReward(long number) {
        if (number < 1_017_323L) { // before apollo fork
            return XAmount.of(1024, XDAG);
        } else if (number < 2_097_152L) { // ~4.25 years
            return XAmount.of(128, XDAG);
        } else if (number < 4_194_304L) { // ~8.51 years
            return XAmount.of(64, XDAG);
        } else if (number < 6_291_456L) { // ~12.77 years
            return XAmount.of(32, XDAG);
        } else if (number < 8_388_608L) { // ~17.02 years
            return XAmount.of(16, XDAG);
        } else if (number < 10_485_760L) { // ~21.28 years
            return XAmount.of(8, XDAG);
        } else if (number < 12_582_912L) {// ~25.53 years
            return XAmount.of(4, XDAG);
        } else if (number < 14_680_064L) {// ~29.79 years
            return XAmount.of(2, XDAG);
        } else if (number < 16_777_216L) {// ~34.04 years
            return XAmount.of(1, XDAG);
        } else {
            return XAmount.ZERO;
        }
    }

    @Override
    public XAmount getMainBlockSupply(long blockNumber) {
        long totalSupply = LongStream.range(1, blockNumber + 1).map(number -> {
            if (number < 1_017_323L) { // before apollo fork
                return XAmount.of(1024, XDAG).toLong();
            } else if (number < 2_097_152L) { // ~4.25 years
                return XAmount.of(128, XDAG).toLong();
            } else if (number < 4_194_304L) { // ~8.51 years
                return XAmount.of(64, XDAG).toLong();
            } else if (number < 6_291_456L) { // ~12.77 years
                return XAmount.of(32, XDAG).toLong();
            } else if (number < 8_388_608L) { // ~17.02 years
                return XAmount.of(16, XDAG).toLong();
            } else if (number < 10_485_760L) { // ~21.28 years
                return XAmount.of(8, XDAG).toLong();
            } else if (number < 12_582_912L) {// ~25.53 years
                return XAmount.of(4, XDAG).toLong();
            } else if (number < 14_680_064L) {// ~29.79 years
                return XAmount.of(2, XDAG).toLong();
            } else if (number <= 16_777_216L) {// ~34.04 years
                return XAmount.of(1, XDAG).toLong();
            } else {
                return XAmount.ZERO.toLong();
            }
        }).reduce(0, Long::sum);

        return XAmount.of(totalSupply);
    }

    @Override
    public Map<Long, byte[]> checkpoints() {
        return Collections.emptyMap();
    }

    private static long[][][] periods = new long[3][64][];

    static {
        periods[MAINNET.id()][APOLLO_FORK.id()] = new long[] { 1L, 1017_323L };

        periods[TESTNET.id()][APOLLO_FORK.id()] = new long[] { 1L, 1017_323L };

        // as soon as possible
        periods[DEVNET.id()][APOLLO_FORK.id()] = new long[] { 1L, 10L };
    }

    @Override
    public long[] getForkSignalingPeriod(Fork fork) {
        return periods[getNetwork().id()][fork.id()];
    }

    @Override
    public boolean forkApolloEnabled() {
        return forkApolloEnabled;
    }

    @Override
    public long getPowEpochTimeout() {
        return epochTime;
    }
}
