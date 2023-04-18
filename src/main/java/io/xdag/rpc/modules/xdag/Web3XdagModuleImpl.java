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

package io.xdag.rpc.modules.xdag;

import static io.xdag.config.Constants.CLIENT_VERSION;
import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.Hash2byte;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.getHash;
import static io.xdag.utils.BasicUtils.pubAddress2Hash;
import static io.xdag.utils.WalletUtils.checkAddress;
import static io.xdag.utils.WalletUtils.fromBase58;
import static io.xdag.utils.WalletUtils.toBase58;

import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.PoolSpec;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerCalculate;
import io.xdag.net.node.Node;
import io.xdag.rpc.dto.ConfigDTO;
import io.xdag.rpc.dto.NetConnDTO;
import io.xdag.rpc.dto.PoolWorkerDTO;
import io.xdag.rpc.dto.StatusDTO;
import io.xdag.utils.BasicUtils;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;

import com.google.common.collect.Lists;

@Slf4j
public class Web3XdagModuleImpl implements Web3XdagModule {

    private final Blockchain blockchain;
    private final XdagModule xdagModule;
    private final Kernel kernel;

    public Web3XdagModuleImpl(XdagModule xdagModule, Kernel kernel) {
        this.blockchain = kernel.getBlockchain();
        this.xdagModule = xdagModule;
        this.kernel = kernel;
    }

    @Override
    public XdagModule getXdagModule() {
        return xdagModule;
    }

    @Override
    public String xdag_protocolVersion() {
        return CLIENT_VERSION;
    }

    @Override
    public Object xdag_syncing() {
        long currentBlock = this.blockchain.getXdagStats().nmain;
        long highestBlock = Math.max(this.blockchain.getXdagStats().totalnmain, currentBlock);
        SyncingResult s = new SyncingResult();
        s.isSyncDone = false;

        Config config = kernel.getConfig();
        if (config instanceof MainnetConfig) {
            if (kernel.getXdagState() != XdagState.SYNC) {
                return s;
            }
        } else if (config instanceof TestnetConfig) {
            if (kernel.getXdagState() != XdagState.STST) {
                return s;
            }
        } else if (config instanceof DevnetConfig) {
            if (kernel.getXdagState() != XdagState.SDST) {
                return s;
            }
        }

        try {
            s.currentBlock = Long.toString(currentBlock);
            s.highestBlock = Long.toString(highestBlock);
            s.isSyncDone = true;

            return s;
        } finally {
            log.debug("xdag_syncing():current {}, highest {}, isSyncDone {}", s.currentBlock, s.highestBlock,
                    s.isSyncDone);
        }
    }

    @Override
    public String xdag_coinbase() {
        return toBase58(Hash2byte(kernel.getPoolMiner().getAddressHash().mutableCopy()));
    }

    @Override
    public String xdag_blockNumber() {
        long b = blockchain.getXdagStats().nmain;
        log.debug("xdag_blockNumber(): {}", b);
        return Long.toString(b);
    }

    @Override
    public String xdag_getBalance(String address) {
        Bytes32 hash;
        MutableBytes32 key = MutableBytes32.create();
        String balance;
        if (checkAddress(address)) {
            hash = pubAddress2Hash(address);
            key.set(8, Objects.requireNonNull(hash).slice(8, 20));
            balance = String.format("%.9f", amount2xdag(kernel.getAddressStore().getBalanceByAddress(fromBase58(address))));
        } else {
            if (StringUtils.length(address) == 32) {
                hash = address2Hash(address);
            } else {
                hash = getHash(address);
            }
            key.set(8, Objects.requireNonNull(hash).slice(8, 24));
            Block block = kernel.getBlockStore().getBlockInfoByHash(Bytes32.wrap(key));
            balance = String.format("%.9f", amount2xdag(block.getInfo().getAmount()));
        }
//        double balance = amount2xdag(block.getInfo().getAmount());
        return balance;
    }

    @Override
    public String xdag_getTotalBalance() {
        String balance = String.format("%.9f", amount2xdag(kernel.getBlockchain().getXdagStats().getBalance()));
        return balance;
    }


    @Override
    public StatusDTO xdag_getStatus() {
        XdagStats xdagStats = kernel.getBlockchain().getXdagStats();
        double hashrateOurs = BasicUtils.xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateOurs());
        double hashrateTotal = BasicUtils.xdagHashRate(kernel.getBlockchain().getXdagExtStats().getHashRateTotal());
        StatusDTO.StatusDTOBuilder builder = StatusDTO.builder();
        builder.nblock(Long.toString(xdagStats.getNblocks()))
                .totalNblocks(Long.toString(xdagStats.getTotalnblocks()))
                .nmain(Long.toString(xdagStats.getNmain()))
                .totalNmain(Long.toString(Math.max(xdagStats.getTotalnmain(), xdagStats.getNmain())))
                .curDiff(toQuantityJsonHex(xdagStats.getDifficulty()))
                .netDiff(toQuantityJsonHex(xdagStats.getMaxdifficulty()))
                .hashRateOurs(toQuantityJsonHex(hashrateOurs))
                .hashRateTotal(toQuantityJsonHex(hashrateTotal))
                .ourSupply(String.format("%.9f",
                        amount2xdag(
                                kernel.getBlockchain().getSupply(xdagStats.nmain))))
                .netSupply(String.format("%.9f",
                        amount2xdag(
                                kernel.getBlockchain().getSupply(Math.max(xdagStats.nmain, xdagStats.totalnmain)))));
        return builder.build();
    }

    @Override
    public Object xdag_netType() {
        return kernel.getConfig().getRootDir();
    }

    @Override
    public Object xdag_poolConfig() {
        PoolSpec poolSpec = kernel.getConfig().getPoolSpec();
        NodeSpec nodeSpec = kernel.getConfig().getNodeSpec();
        ConfigDTO.ConfigDTOBuilder configDTOBuilder = ConfigDTO.builder();
        configDTOBuilder.poolIp(poolSpec.getPoolIp());
        configDTOBuilder.poolPort(poolSpec.getPoolPort());
        configDTOBuilder.nodeIp(nodeSpec.getNodeIp());
        configDTOBuilder.nodePort(nodeSpec.getNodePort());
        configDTOBuilder.globalMinerLimit(poolSpec.getGlobalMinerLimit());
        configDTOBuilder.maxConnectMinerPerIp(poolSpec.getMaxConnectPerIp());
        configDTOBuilder.maxMinerPerAccount(poolSpec.getMaxMinerPerAccount());


        configDTOBuilder.poolFundRation(Double.toString(kernel.getAwardManager().getPoolConfig().getFundRation() * 100));
        configDTOBuilder.poolFeeRation(Double.toString(kernel.getAwardManager().getPoolConfig().getPoolRation() * 100));
        configDTOBuilder.poolDirectRation(Double.toString(kernel.getAwardManager().getPoolConfig().getDirectRation() * 100));
        configDTOBuilder.poolRewardRation(Double.toString(kernel.getAwardManager().getPoolConfig().getMinerRewardRation() * 100));
        return configDTOBuilder.build();
    }

    @Override
    public Object xdag_netConnectionList() {
        List<NetConnDTO> netConnDTOList = Lists.newArrayList();
        NetConnDTO.NetConnDTOBuilder netConnDTOBuilder = NetConnDTO.builder();
        Map<Node, Long> map = kernel.getNodeMgr().getActiveNode();
        for (Iterator<Node> it = map.keySet().iterator(); it.hasNext(); ) {
            Node node = it.next();
            netConnDTOBuilder.connectTime(map.get(node) == null ? 0 : map.get(node)) // use default "0"
                    .inBound(node.getStat().Inbound.get())
                    .outBound(node.getStat().Outbound.get())
                    .nodeAddress(node.getAddress());
            netConnDTOList.add(netConnDTOBuilder.build());
        }

        return netConnDTOList;
    }

    static class SyncingResult {

        public String currentBlock;
        public String highestBlock;
        public boolean isSyncDone;

    }

    @Override
    public Object xdag_updatePoolConfig(ConfigDTO configDTO, String passphrase) {
        try {
            //unlock
            if (checkPassword(passphrase)) {
                double poolFeeRation = configDTO.getPoolFeeRation() != null ?
                        Double.parseDouble(configDTO.getPoolFeeRation()) : kernel.getConfig().getPoolSpec().getPoolRation();
                double poolRewardRation = configDTO.getPoolRewardRation() != null ?
                        Double.parseDouble(configDTO.getPoolRewardRation()) : kernel.getConfig().getPoolSpec().getRewardRation();
                double poolDirectRation = configDTO.getPoolDirectRation() != null ?
                        Double.parseDouble(configDTO.getPoolDirectRation()) : kernel.getConfig().getPoolSpec().getDirectRation();
                double poolFundRation = configDTO.getPoolFundRation() != null ?
                        Double.parseDouble(configDTO.getPoolFundRation()) : kernel.getConfig().getPoolSpec().getFundRation();
                kernel.getAwardManager().updatePoolConfig(poolFeeRation, poolRewardRation, poolDirectRation, poolFundRation);
            }
        } catch (NumberFormatException e) {
            return "Error";
        }
        return "Success";
    }

    @Override
    public Object xdag_getPoolWorkers() {
        List<PoolWorkerDTO> poolWorkerDTOList = Lists.newArrayList();
        PoolWorkerDTO.PoolWorkerDTOBuilder poolWorkerDTOBuilder = PoolWorkerDTO.builder();
        Collection<Miner> miners = kernel.getMinerManager().getActivateMiners().values();
        PoolWorkerDTO poolWorker = getPoolWorkerDTO(poolWorkerDTOBuilder, kernel.getPoolMiner());
        poolWorker.setStatus("fee");
        poolWorkerDTOList.add(poolWorker);
        for (Miner miner : miners) {
            poolWorkerDTOList.add(getPoolWorkerDTO(poolWorkerDTOBuilder,miner));
        }
        return poolWorkerDTOList;
    }

    @Override
    public String xdag_getMaxXferBalance() {
        return xdagModule.getMaxXferBalance();
    }

    private PoolWorkerDTO getPoolWorkerDTO(PoolWorkerDTO.PoolWorkerDTOBuilder poolWorkerDTOBuilder,Miner miner){
        poolWorkerDTOBuilder.address(toBase58(miner.getAddressHashByte()))
                .status(miner.getMinerStates().toString())
                .unpaidShares(MinerCalculate.calculateUnpaidShares(miner))
                .hashrate(BasicUtils.xdag_log_difficulty2hashrate(miner.getMeanLogDiff()))
                .workers(getWorkers(miner));
        return poolWorkerDTOBuilder.build();
    }
    private List<PoolWorkerDTO.Worker> getWorkers(Miner miner) {
        List<PoolWorkerDTO.Worker> workersList = Lists.newArrayList();
        PoolWorkerDTO.Worker.WorkerBuilder workerBuilder = PoolWorkerDTO.Worker.builder();
        Map<InetSocketAddress, MinerChannel> channels = miner.getChannels();
        for (Map.Entry<InetSocketAddress, MinerChannel> channel : channels.entrySet()) {
            workerBuilder.address(channel.getKey()).inBound(channel.getValue().getInBound().get())
                    .outBound(channel.getValue().getOutBound().get())
                    .unpaidShares(MinerCalculate.calculateUnpaidShares(channel.getValue()))
                    .name(channel.getValue().getWorkerName())
                    .hashrate(BasicUtils.xdag_log_difficulty2hashrate(channel.getValue().getMeanLogDiff()));
            workersList.add(workerBuilder.build());
        }
        return workersList;
    }

    private boolean checkPassword(String passphrase) {
        Wallet wallet = new Wallet(kernel.getConfig());
        return wallet.unlock(passphrase);
    }
}
