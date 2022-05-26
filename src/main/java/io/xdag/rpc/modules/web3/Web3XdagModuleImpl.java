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

package io.xdag.rpc.modules.web3;

import static io.xdag.config.Constants.CLIENT_VERSION;
import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.XdagState;
import io.xdag.core.XdagStats;
import io.xdag.net.node.Node;
import io.xdag.rpc.dto.StatusDTO;
import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.XdagTime;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Web3XdagModuleImpl implements Web3XdagModule {

    private static final Logger logger = LoggerFactory.getLogger(Web3XdagModuleImpl.class);
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
            logger.debug("xdag_syncing():current {}, highest {}, isSyncDone {}", s.currentBlock, s.highestBlock,
                    s.isSyncDone);
        }
    }

    @Override
    public String xdag_coinbase() {
        return hash2Address(kernel.getPoolMiner().getAddressHash());
    }

    @Override
    public String xdag_blockNumber() {
        long b = blockchain.getXdagStats().nmain;
        logger.debug("xdag_blockNumber(): {}", b);
        return Long.toString(b);
    }

    @Override
    public String xdag_getBalance(String address) {
        Bytes32 hash;
        if (StringUtils.length(address) == 32) {
            hash = address2Hash(address);
        } else {
            hash = BasicUtils.getHash(address);
        }
//        byte[] key = new byte[32];
        MutableBytes32 key = MutableBytes32.create();
//        System.arraycopy(Objects.requireNonNull(hash), 8, key, 8, 24);
        key.set(8, hash.slice(8, 24));
        Block block = kernel.getBlockStore().getBlockInfoByHash(Bytes32.wrap(key));
        String balance = String.format("%.9f", amount2xdag(block.getInfo().getAmount()));

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
                .totalNmain(Long.toString(Math.max(xdagStats.getTotalnmain(),xdagStats.getNmain())))
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
    public Object xdag_netType() throws Exception {
        return kernel.getConfig().getRootDir();
    }

    @Override
    public Object xdag_poolConfig() throws Exception {
        return null;
    }

    @Override
    public Object xdag_netConnectionList() throws Exception {
        return null;
//        Map<Node, Long> map = kernel.getNodeMgr().getActiveNode();
//        StringBuilder stringBuilder = new StringBuilder();
//        for (Iterator<Node> it = map.keySet().iterator(); it.hasNext(); ) {
//            Node node = it.next();
//            stringBuilder
//                    .append(node.getAddress())
//                    .append(" ")
//                    .append(map.get(node) == null ? null : XdagTime.format(new Date(map.get(node))))
//                    .append(" ")
//                    .append(node.getStat().Inbound.get())
//                    .append(" in/")
//                    .append(node.getStat().Outbound.get())
//                    .append(" out").append(System.getProperty("line.separator"));
//        }
//
//        return stringBuilder.toString();
    }

    static class SyncingResult {

        public String currentBlock;
        public String highestBlock;
        public boolean isSyncDone;

    }
}
