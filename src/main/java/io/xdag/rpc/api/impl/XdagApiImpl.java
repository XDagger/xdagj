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

package io.xdag.rpc.api.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.RPCSpec;
import io.xdag.core.*;
import io.xdag.crypto.exception.AddressFormatException;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.net.Channel;
import io.xdag.rpc.model.request.TransactionRequest;
import io.xdag.rpc.model.response.*;
import io.xdag.rpc.server.core.JsonRpcServer;
import io.xdag.rpc.api.XdagApi;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.WalletUtils;
import io.xdag.utils.exception.XdagOverFlowException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.xdag.cli.Commands.getStateByFlags;
import static io.xdag.config.Constants.*;
import static io.xdag.core.BlockState.MAIN;
import static io.xdag.core.BlockType.*;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static io.xdag.db.mysql.TransactionHistoryStoreImpl.totalPage;
import static io.xdag.rpc.error.JsonRpcError.*;
import static io.xdag.rpc.util.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.WalletUtils.*;
import static io.xdag.utils.XdagTime.xdagTimestampToMs;

@Slf4j
public class XdagApiImpl extends AbstractXdagLifecycle implements XdagApi {
    private final Kernel kernel;
    private final Blockchain blockchain;
    private final RPCSpec rpcSpec;
    private JsonRpcServer server;

    public XdagApiImpl(Kernel kernel) {
        this.kernel = kernel;
        this.blockchain = kernel.getBlockchain();
        this.rpcSpec = kernel.getConfig().getRPCSpec();
        this.server = new JsonRpcServer(rpcSpec, this);

        validateConfiguration();
    }

    private void validateConfiguration() {
        if (rpcSpec.isRpcHttpEnabled()) {
            if (StringUtils.isBlank(rpcSpec.getRpcHttpHost())) {
                throw new IllegalArgumentException("RPC host not configured");
            }
            if (rpcSpec.getRpcHttpPort() <= 0 || rpcSpec.getRpcHttpPort() > 65535) {
                throw new IllegalArgumentException("Invalid RPC port");
            }
        }
    }

    @Override
    protected void doStart() {
        try {
            if (rpcSpec.isRpcHttpEnabled()) {
                server.start();
                log.info("JSON-RPC server started on {}:{} (SSL: {})", rpcSpec.getRpcHttpHost(), rpcSpec.getRpcHttpPort(), rpcSpec.isRpcEnableHttps());
            }
        } catch (Exception e) {
            log.error("Failed to start RPC server", e);
            throw new RuntimeException("Failed to start RPC server", e);
        }
    }

    @Override
    protected void doStop() {
        try {
            if (server != null) {
                server.stop();
                server = null;
            }
        } catch (Exception e) {
            log.error("Error while stopping RPC server", e);
        } finally {
            log.info("JSON-RPC server stopped");
        }
    }

    @Override
    public BlockResponse xdag_getTransactionByHash(String hash, int page) {
      try {
        return getBlockDTOByHash(hash, page);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String xdag_getBalanceByNumber(String bnOrId) {
        Block block = blockchain.getBlockByHeight(Long.parseLong(bnOrId));
        if (null == block) {
            return null;
        }
        return String.format("%s", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString());
    }

    @Override
    public ConfigResponse xdag_poolConfig() {
        NodeSpec nodeSpec = kernel.getConfig().getNodeSpec();
        ConfigResponse.ConfigResponseBuilder configResponseBuilder = ConfigResponse.builder();
        configResponseBuilder.nodeIp(nodeSpec.getNodeIp());
        configResponseBuilder.nodePort(nodeSpec.getNodePort());
        return configResponseBuilder.build();
    }

    @Override
    public String xdag_protocolVersion() {
        return CLIENT_VERSION;
    }

    @Override
    public BlockResponse xdag_getBlockByHash(String hash, int page) {
      try {
        return getBlockDTOByHash(hash, page);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public BlockResponse xdag_getBlockByHash(String hash, int page, int pageSize) {
      try {
        return getBlockDTOByHash(hash, page, pageSize);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public BlockResponse xdag_getBlockByHash(String hash, int page, String startTime, String endTime) {
      try {
        return getBlockDTOByHash(hash, page, startTime, endTime);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public BlockResponse xdag_getBlockByHash(String hash, int page, String startTime, String endTime, int pageSize) {
      try {
        return getBlockDTOByHash(hash, page, startTime, endTime, pageSize);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public BlockResponse xdag_getBlockByNumber(String bnOrId, int page) {
        return getBlockByNumber(bnOrId, page);
    }

    @Override
    public BlockResponse xdag_getBlockByNumber(String bnOrId, int page, int pageSize) {
        return getBlockByNumber(bnOrId, page, pageSize);
    }

    @Override
    public List<BlockResponse> xdag_getBlocksByNumber(String bnOrId) {
        int number = bnOrId == null ? 20 : Integer.parseInt(bnOrId);// default 20
        List<Block> blocks = blockchain.listMainBlocks(number);
        List<BlockResponse> resultDTOS = Lists.newArrayList();
        for (Block block : blocks){
            BlockResponse dto = transferBlockToBriefBlockResultDTO(blockchain.getBlockByHash(block.getHash(), false));
            if(dto != null){
                resultDTOS.add(dto);
            }
        }
        return resultDTOS;
    }

    @Override
    public String xdag_coinbase() {
        return toBase58(toBytesAddress(kernel.getCoinbase()));
    }

    @Override
    public String xdag_blockNumber() {
        long b = blockchain.getXdagStats().nmain;
        log.debug("xdag_blockNumber(): {}", b);
        return Long.toString(b);
    }

    @Override
    public String xdag_getBalance(String address) throws AddressFormatException {
        Bytes32 hash;
        MutableBytes32 key = MutableBytes32.create();
        String balance;
        if (WalletUtils.checkAddress(address)) {
            hash = pubAddress2Hash(address);
            key.set(8, Objects.requireNonNull(hash).slice(8, 20));
            balance = String.format("%s", kernel.getAddressStore().getBalanceByAddress(fromBase58(address).toArray()).toDecimal(9, XUnit.XDAG).toPlainString());
        } else {
            if (StringUtils.length(address) == 32) {
                hash = BasicUtils.address2Hash(address);
            } else {
                hash = BasicUtils.getHash(address);
            }
            key.set(8, Objects.requireNonNull(hash).slice(8, 24));
            Block block = kernel.getBlockStore().getBlockInfoByHash(Bytes32.wrap(key));
            balance = String.format("%s", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString());
        }
        return balance;
    }

    @Override
    public String xdag_getTransactionNonce(String address) throws AddressFormatException {
        UInt64 txNonce = UInt64.ZERO;
        if (WalletUtils.checkAddress(address)) {
            UInt64 txQuantity = kernel.getAddressStore().getTxQuantity(fromBase58(address).toArray());
            txNonce = txNonce.add(txQuantity.add(UInt64.ONE));
        }
        return String.format("%s", txNonce.toLong());
    }

    @Override
    public String xdag_getTotalBalance() {
        return String.format("%s", kernel.getBlockchain().getXdagStats().getBalance().toDecimal(9, XUnit.XDAG).toPlainString());
    }

    @Override
    public XdagStatusResponse xdag_getStatus() {
        XdagStats xdagStats = blockchain.getXdagStats();
        XdagExtStats xdagExtStats = blockchain.getXdagExtStats();
        double hashrateOurs = BasicUtils.xdagHashRate(xdagExtStats.getHashRateOurs());
        double hashrateTotal = BasicUtils.xdagHashRate(xdagExtStats.getHashRateTotal());
        XdagStatusResponse.XdagStatusResponseBuilder builder = XdagStatusResponse.builder();
        builder.nblock(Long.toString(xdagStats.getNblocks()))
                .totalNblocks(Long.toString(xdagStats.getTotalnblocks()))
                .nmain(Long.toString(xdagStats.getNmain()))
                .totalNmain(Long.toString(Math.max(xdagStats.getTotalnmain(), xdagStats.getNmain())))
                .curDiff(toQuantityJsonHex(xdagStats.getDifficulty()))
                .netDiff(toQuantityJsonHex(xdagStats.getMaxdifficulty()))
                .hashRateOurs(toQuantityJsonHex(hashrateOurs))
                .hashRateTotal(toQuantityJsonHex(hashrateTotal))
                .ourSupply(String.format("%s", blockchain.getSupply(xdagStats.nmain).toDecimal(9, XUnit.XDAG).toPlainString()))
                .netSupply(String.format("%s", blockchain.getSupply(Math.max(xdagStats.nmain, xdagStats.totalnmain)).toDecimal(9, XUnit.XDAG).toPlainString()));
        return builder.build();
    }

    @Override
    public ProcessResponse xdag_personal_sendTransaction(TransactionRequest request, String passphrase) {
        log.debug("personalSendTransaction request:{}", request);

        String from = request.getFrom();
        String to = request.getTo();
        String value = request.getValue();
        String remark = request.getRemark();

        ProcessResponse result = ProcessResponse.builder().code(SUCCESS).build();

        checkParam(value, remark, result);
        if (result.getCode() != SUCCESS) {
            return result;
        }

      Bytes32 toHash = null;
      try {
        toHash = checkTo(to, result);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
      if (result.getCode() != SUCCESS) {
            return result;
        }

      Bytes32 fromHash = null;
      try {
        fromHash = checkFrom(from, result);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
      if (result.getCode() != SUCCESS) {
            return result;
        }

        checkPassword(passphrase, result);
        if (result.getCode() != SUCCESS) {
            return result;
        }

        // do xfer
        double amount = BasicUtils.getDouble(value);
        doXfer(amount, fromHash, toHash, remark, null, result);

        return result;
    }

    @Override
    public ProcessResponse xdag_personal_sendSafeTransaction(TransactionRequest request, String passphrase) {
        log.debug("personalSendTransaction request:{}", request);

        String from = request.getFrom();
        String to = request.getTo();
        String value = request.getValue();
        String remark = request.getRemark();
        String nonce = request.getNonce();

        ProcessResponse result = ProcessResponse.builder().code(SUCCESS).build();

        checkParam(value, remark, result);
        if (result.getCode() != SUCCESS) {
            return result;
        }

      Bytes32 toHash = null;
      try {
        toHash = checkTo(to, result);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
      if (result.getCode() != SUCCESS) {
            return result;
        }

      Bytes32 fromHash = null;
      try {
        fromHash = checkFrom(from, result);
      } catch (AddressFormatException e) {
        throw new RuntimeException(e);
      }
      if (result.getCode() != SUCCESS) {
            return result;
        }

        UInt64 txNonce = UInt64.valueOf(new BigInteger(nonce));

        checkPassword(passphrase, result);
        if (result.getCode() != SUCCESS) {
            return result;
        }

        // do xfer
        double amount = BasicUtils.getDouble(value);
        doXfer(amount, fromHash, toHash, remark, txNonce, result);

        return result;
    }

    @Override
    public String xdag_getRewardByNumber(String bnOrId) {
        try {
            XAmount reward = blockchain.getReward(Long.parseLong(bnOrId));
            return String.format("%s", reward.toDecimal(9, XUnit.XDAG).toPlainString());
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String xdag_sendRawTransaction(String rawData) {
        // 1. build transaction
        // 2. try to add blockchain
        // 3. check from address if valid.
        Block block = new Block(new XdagBlock(Hex.decode(rawData)));
        ImportResult result;
        List<Address> inputs = block.getInputs();
        int inputSize = inputs.size();
        if (inputSize == 0) {
            result = ImportResult.INVALID_BLOCK;
            return "THE TX NEEDS INPUT " + result.getErrorInfo();
        }
        for (Address input : inputs) {
            if (input.getType() == XDAG_FIELD_IN && block.getTxNonceField() != null) {
                result = ImportResult.INVALID_BLOCK;
                return "NO NONCE IS REQUIRED FOR MAIN BLOCK TRANSFER " + result.getErrorInfo();
            } else if (input.getType() == XDAG_FIELD_INPUT) {
                Bytes addr = BytesUtils.byte32ToArray(input.getAddress());
                UInt64 legalNonce = kernel.getAddressStore().getTxQuantity(addr.toArray()).add(UInt64.ONE);
                UInt64 blockNonce;
                if (inputSize != 1) {
                    result = ImportResult.INVALID_BLOCK;
                    return "ACCOUNT TRANSFER IS LIMITED TO ONE INPUT ONLY " + result.getErrorInfo();
                }
                if (block.getTxNonceField() == null) {
                    result = ImportResult.INVALID_BLOCK;
                    return "PLEASE DOWNLOAD THE LATEST WALLET " + result.getErrorInfo();
                }
                blockNonce = block.getTxNonceField().getTransactionNonce();
                if (blockNonce.compareTo(legalNonce) != 0) {
                    result = ImportResult.INVALID_BLOCK;
                    return "PLEASE FILL IN THE CORRECT NONCE " + result.getErrorInfo();
                }
            }
        }
        if (checkTransaction(block)) {
            result = kernel.getSyncMgr().importBlock(
                    new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL()));
        } else {
            result = ImportResult.INVALID_BLOCK;
        }
        if(result == ImportResult.IMPORTED_NOT_BEST && block.getTxNonceField() != null) {
            List<Address> in = block.getInputs();
            UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();
            for (Address input : in) {
                if (input.getType() == XDAG_FIELD_INPUT) {
                    Bytes addr = BytesUtils.byte32ToArray(input.getAddress());
                    kernel.getAddressStore().updateTxQuantity(addr.toArray(), blockNonce);
                }
            }
        }
        return result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST ?
                BasicUtils.hash2Address(block.getHash()) : "INVALID_BLOCK " + result.getErrorInfo();
    }

    @Override
    public List<NetConnResponse> xdag_netConnectionList() {
        List<NetConnResponse> netConnResponseList = Lists.newArrayList();
        NetConnResponse.NetConnResponseBuilder netConnDTOBuilder = NetConnResponse.builder();
        List<Channel> channelList = kernel.getChannelMgr().getActiveChannels();
        for (Channel channel : channelList) {
            netConnDTOBuilder.connectTime(kernel.getConfig().getSnapshotSpec().getSnapshotTime())
                    .inBound(0)
                    .outBound(0)
                    .nodeAddress(channel.getRemoteAddress());
            netConnResponseList.add(netConnDTOBuilder.build());
        }
        return netConnResponseList;
    }

    @Override
    public String xdag_netType() {
        return kernel.getConfig().getNodeSpec().getNetwork().toString().toLowerCase();
    }

    private BlockResponse transferAccountToBlockResultDTO(String address, int page, Object... parameters)
        throws AddressFormatException {
        XAmount balance = kernel.getAddressStore().getBalanceByAddress(hash2byte(pubAddress2Hash(address).mutableCopy()).toArray());

        BlockResponse.BlockResponseBuilder BlockResultDTOBuilder = BlockResponse.builder();
        BlockResultDTOBuilder.address(address)
                .hash(null)
                .balance(balance.toDecimal(9, XUnit.XDAG).toPlainString())
                .type("Wallet")
                .blockTime(xdagTimestampToMs(kernel.getConfig().getSnapshotSpec().getSnapshotTime()))
                .timeStamp(kernel.getConfig().getSnapshotSpec().getSnapshotTime())
                .state("Accepted");
        if (page != 0) {
            BlockResultDTOBuilder.transactions(getTxHistory(address, page, parameters))
                    .totalPage(totalPage);
        }
        totalPage = 1;
        return BlockResultDTOBuilder.build();
    }

    private BlockResponse transferBlockInfoToBlockResultDTO(Block block, int page, Object... parameters) {
        if (null == block) {
            return null;
        }
        BlockResponse.BlockResponseBuilder BlockResultDTOBuilder = BlockResponse.builder();
        BlockResultDTOBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%s", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                .type(SNAPSHOT.getDesc())
                .blockTime(xdagTimestampToMs(kernel.getConfig().getSnapshotSpec().getSnapshotTime()))
                .timeStamp(kernel.getConfig().getSnapshotSpec().getSnapshotTime());
//                .flags(Integer.toHexString(block.getInfo().getFlags()))
//                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
//                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
//                        StandardCharsets.UTF_8).trim())
//                .state(getStateByFlags(block.getInfo().getFlags()))
//                .type(getType(block))
//                .refs(getLinks(block))
//                .height(block.getInfo().getHeight())
        if (page != 0) {
            BlockResultDTOBuilder.transactions(getTxLinks(block, page, parameters))
                    .totalPage(totalPage);
        }
        totalPage = 1;
        return BlockResultDTOBuilder.build();
    }

    private List<BlockResponse.TxLink> getTxHistory(String address, int page, Object... parameters)
        throws AddressFormatException {
        List<TxHistory> txHistories = blockchain.getBlockTxHistoryByAddress(pubAddress2Hash(address), page, parameters);
        List<BlockResponse.TxLink> txLinks = Lists.newArrayList();
        for (TxHistory txHistory : txHistories) {
            Block b = blockchain.getBlockByHash(txHistory.getAddress().getAddress(), false);
            BlockResponse.TxLink.TxLinkBuilder txLinkBuilder = BlockResponse.TxLink.builder();
            if (b != null) {
                BlockInfo blockInfo = b.getInfo();
                if ((blockInfo.flags & BI_APPLIED) == 0) {
                    continue;
                }

                txLinkBuilder.address(hash2Address(txHistory.getAddress().getAddress()))
                        .hashlow(txHistory.getAddress().getAddress().toUnprefixedHexString())
                        .amount(String.format("%s", txHistory.getAddress().getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                        .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_INPUT) ? 0 :
                                txHistory.getAddress().getType().equals(XDAG_FIELD_OUTPUT) ? 1 :
                                        txHistory.getAddress().getType().equals(XDAG_FIELD_COINBASE) ? 2 : 3)
                        .time(txHistory.getTimestamp())
                        .remark(txHistory.getRemark());
            } else {
                txLinkBuilder.address(toBase58(BytesUtils.byte32ToArray(txHistory.getAddress().getAddress())))
                        .hashlow(txHistory.getAddress().getAddress().toUnprefixedHexString())
                        .amount(String.format("%s", txHistory.getAddress().getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                        .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_IN) ? 0 :
                                txHistory.getAddress().getType().equals(XDAG_FIELD_OUT) ? 1 : 3)
                        .time(xdagTimestampToMs(kernel.getConfig().getSnapshotSpec().getSnapshotTime()))
                        .remark(txHistory.getRemark());
            }
            txLinks.add(txLinkBuilder.build());
        }
        return txLinks;
    }

    public BlockResponse getBlockByNumber(String bnOrId, int page, Object... parameters) {
        Block blockFalse = blockchain.getBlockByHeight(Long.parseLong(bnOrId));
        if (null == blockFalse) {
            return null;
        }
        Block blockTrue = blockchain.getBlockByHash(blockFalse.getHash(), true);
        if (blockTrue == null) {
            return transferBlockInfoToBlockResultDTO(blockFalse, page, parameters);
        }
        return transferBlockToBlockResultDTO(blockTrue, page, parameters);
    }

    public BlockResponse getBlockDTOByHash(String hash, int page, Object... parameters)
        throws AddressFormatException {
        Bytes32 blockHash;
        if (WalletUtils.checkAddress(hash)) {
            return transferAccountToBlockResultDTO(hash, page, parameters);
        } else {
            if (StringUtils.length(hash) == 32) {
                blockHash = address2Hash(hash);
            } else {
                blockHash = BasicUtils.getHash(hash);
            }
            Block block = blockchain.getBlockByHash(blockHash, true);
            if (block == null) {
                block = blockchain.getBlockByHash(blockHash, false);
                return transferBlockInfoToBlockResultDTO(block, page, parameters);
            }
            return transferBlockToBlockResultDTO(block, page, parameters);
        }
    }


    private List<BlockResponse.TxLink> getTxLinks(Block block, int page, Object... parameters) {
        List<TxHistory> txHistories = blockchain.getBlockTxHistoryByAddress(block.getHashLow(), page, parameters);
        List<BlockResponse.TxLink> txLinks = Lists.newArrayList();
        // 1. earning info
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) && block.getInfo().getHeight() > kernel.getConfig().getSnapshotSpec().getSnapshotHeight()) {
            BlockResponse.TxLink.TxLinkBuilder txLinkBuilder = BlockResponse.TxLink.builder();
            String remark = "";
            if (block.getInfo().getRemark() != null && block.getInfo().getRemark().length != 0) {
                remark = new String(block.getInfo().getRemark(), StandardCharsets.UTF_8).trim();
            }
            XAmount earnFee = kernel.getBlockStore().getBlockInfoByHash(block.getHashLow()).getFee();
            // if (block.getInfo().getAmount().equals(XAmount.ZERO)){ earnFee = XAmount.ZERO;} //when block amount is zero, fee also should make zero.
            txLinkBuilder.address(hash2Address(block.getHashLow()))
                    .hashlow(block.getHashLow().toUnprefixedHexString())
                    .amount(String.format("%s", blockchain.getReward(block.getInfo().getHeight()).add(earnFee).toDecimal(9, XUnit.XDAG).toPlainString()))
                    .direction(2)
                    .time(xdagTimestampToMs(block.getTimestamp()))
                    .remark(remark);
            txLinks.add(txLinkBuilder.build());
        }
        // 2. tx history info
        for (TxHistory txHistory : txHistories) {
            BlockInfo blockInfo = blockchain.getBlockByHash(txHistory.getAddress().getAddress(), false).getInfo();
            if ((blockInfo.flags & BI_APPLIED) == 0) {
                continue;
            }

            XAmount Amount = txHistory.getAddress().getAmount();
            // Check if it's a transaction block, only subtract 0.1 if it has inputs
            if (!block.getInputs().isEmpty() && txHistory.getAddress().getType().equals(XDAG_FIELD_OUTPUT)) {
                Amount = Amount.subtract(MIN_GAS);
            }
            BlockResponse.TxLink.TxLinkBuilder txLinkBuilder = BlockResponse.TxLink.builder();
            txLinkBuilder.address(hash2Address(txHistory.getAddress().getAddress()))
                    .hashlow(txHistory.getAddress().getAddress().toUnprefixedHexString())
                    .amount(String.format("%s", Amount.toDecimal(9, XUnit.XDAG).toPlainString()))
                    .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_IN) ? 0 :
                            txHistory.getAddress().getType().equals(XDAG_FIELD_OUT) ? 1 : 3)
                    .time(txHistory.getTimestamp())
                    .remark(txHistory.getRemark());
            txLinks.add(txLinkBuilder.build());
        }
        return txLinks;
    }

    private List<BlockResponse.Link> getLinks(Block block) {
        List<Address> inputs = block.getInputs();
        List<Address> outputs = block.getOutputs();
        List<BlockResponse.Link> links = Lists.newArrayList();

        // fee update
        BlockResponse.Link.LinkBuilder fee = BlockResponse.Link.builder();
        fee.address(block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        : hash2Address(Bytes32.wrap(block.getInfo().getRef())))
                .hashlow(block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        : Bytes32.wrap(block.getInfo().getRef()).toUnprefixedHexString())
                .amount(block.getInfo().getRef() == null ? String.format("%.9f", amount2xdag(0)) :
                        (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) ? kernel.getBlockStore().getBlockInfoByHash(block.getHashLow()).getFee().toDecimal(9, XUnit.XDAG).toPlainString() :
                                (block.getInputs().isEmpty() ? XAmount.ZERO.toDecimal(9, XUnit.XDAG).toPlainString() :
                                        MIN_GAS.multiply(block.getOutputs().size()).toDecimal(9, XUnit.XDAG).toPlainString())))// calculate the fee
                .direction(2);
        links.add(fee.build());


        for (Address input : inputs) {
            BlockResponse.Link.LinkBuilder linkBuilder = BlockResponse.Link.builder();
            linkBuilder.address(input.getIsAddress() ? toBase58(hash2byte(input.getAddress())) : hash2Address(input.getAddress()))
                    .hashlow(input.getAddress().toUnprefixedHexString())
                    .amount(String.format("%s", input.getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                    .direction(0);
            links.add(linkBuilder.build());
        }

        for (Address output : outputs) {
            BlockResponse.Link.LinkBuilder linkBuilder = BlockResponse.Link.builder();
            if (output.getType().equals(XDAG_FIELD_COINBASE)) continue;
            XAmount Amount = output.getAmount();
            if (!block.getInputs().isEmpty()) {
                Amount = Amount.subtract(MIN_GAS);
            }
            linkBuilder.address(output.getIsAddress() ? toBase58(hash2byte(output.getAddress())) : hash2Address(output.getAddress()))
                    .hashlow(output.getAddress().toUnprefixedHexString())
                    .amount(String.format("%s", Amount.toDecimal(9, XUnit.XDAG).toPlainString()))
                    .direction(1);
            links.add(linkBuilder.build());
        }

        return links;
    }

    private BlockResponse transferBlockToBlockResultDTO(Block block, int page, Object... parameters) {
        if (null == block) {
            return null;
        }
        BlockResponse.BlockResponseBuilder BlockResultDTOBuilder = BlockResponse.builder();
        BlockResultDTOBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%s", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                .blockTime(xdagTimestampToMs(block.getTimestamp()))
                .timeStamp(block.getTimestamp())
                .flags(Integer.toHexString(block.getInfo().getFlags()))
                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
                        StandardCharsets.UTF_8).trim())
                .state(getStateByFlags(block.getInfo().getFlags()))
                .type(getType(block))
                .refs(getLinks(block))
                .height(block.getInfo().getHeight());
        if (page != 0) {
            BlockResultDTOBuilder.transactions(getTxLinks(block, page, parameters))
                    .totalPage(totalPage);
        }
        totalPage = 1;
        return BlockResultDTOBuilder.build();
    }

    private BlockResponse transferBlockToBriefBlockResultDTO(Block block) {
        if (null == block) {
            return null;
        }
        BlockResponse.BlockResponseBuilder BlockResponseBuilder = BlockResponse.builder();
        BlockResponseBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%s", block.getInfo().getAmount().toDecimal(9, XUnit.XDAG).toPlainString()))
                .blockTime(xdagTimestampToMs(block.getTimestamp()))
                .timeStamp(block.getTimestamp())
                .flags(Integer.toHexString(block.getInfo().getFlags()))
                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
                        StandardCharsets.UTF_8).trim())
                .state(getStateByFlags(block.getInfo().getFlags()))
                .type(getType(block))
                .height(block.getInfo().getHeight());
        return BlockResponseBuilder.build();
    }

    private String getType(Block block) {
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc())) {
            return MAIN_BLOCK.getDesc();
        } else if (block.getInsigs() == null || block.getInsigs().isEmpty()) {
            if (CollectionUtils.isEmpty(block.getInputs()) && CollectionUtils.isEmpty(block.getOutputs())) {
                return WALLET.getDesc();
            } else {
                return TRANSACTION.getDesc();
            }
        } else {
            return TRANSACTION.getDesc();
        }
    }

    private void checkParam(String value, String remark, ProcessResponse processResponse) {
        try {
            double amount = BasicUtils.getDouble(value);
            if (amount < 0) {
                processResponse.setCode(ERR_XDAG_AMOUNT);
                processResponse.setErrMsg("The transfer amount must be greater than 0");
            }

        } catch (NumberFormatException e) {
            processResponse.setCode(ERR_XDAG_PARAM);
            processResponse.setErrMsg(e.getMessage());
        }
    }

    private void checkPassword(String passphrase, ProcessResponse result) {
        Wallet wallet = new Wallet(kernel.getConfig());
        try {
            boolean res = wallet.unlock(passphrase);
            if (!res) {
                result.setCode(ERR_XDAG_WALLET_LOCKED);
                result.setErrMsg("wallet unlock failed");
            }
        } catch (Exception e) {
            result.setCode(ERR_XDAG_WALLET);
            result.setErrMsg(e.getMessage());
        }
    }

    public void doXfer(
            double sendValue,
            Bytes32 fromAddress,
            Bytes32 toAddress,
            String remark,
            UInt64 txNonce,
            ProcessResponse processResponse
    ) {
        XAmount amount;
        try {
            amount = XAmount.of(BigDecimal.valueOf(sendValue), XUnit.XDAG);
        } catch (XdagOverFlowException e) {
            processResponse.setCode(ERR_XDAG_PARAM);
            processResponse.setErrMsg("param invalid");
            return;
        }
        MutableBytes32 to = MutableBytes32.create();
        to.set(8, toAddress.slice(8, 20));

        // Remaining balance to transfer
        AtomicReference<XAmount> remain = new AtomicReference<>(amount);
        // Transfer inputs
        Map<Address, ECKeyPair> ourAccounts = Maps.newHashMap();

        // If no from address, search from node accounts
        if (fromAddress == null) {
            log.debug("fromAddress is null, search all our blocks");
            // our block select

            List<ECKeyPair> accounts = kernel.getWallet().getAccounts();
            for (ECKeyPair account : accounts) {
                Bytes addr = toBytesAddress(account);
                XAmount addrBalance = kernel.getAddressStore().getBalanceByAddress(addr.toArray());

                if (txNonce == null) {
                    UInt64 currentTxQuantity = kernel.getAddressStore().getTxQuantity(addr.toArray());
                    txNonce = currentTxQuantity.add(UInt64.ONE);
                } else if (txNonce.compareTo(kernel.getAddressStore().getTxQuantity(addr.toArray()).add(UInt64.ONE)) != 0) {
                    processResponse.setCode(ERR_XDAG_PARAM);
                    processResponse.setErrMsg("The nonce passed is incorrect. Please fill in the nonce according to the query value");
                    return;
                }

                if (compareAmountTo(remain.get(), addrBalance) <= 0) {
                    ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, remain.get(), true), account);
                    remain.set(XAmount.ZERO);
                    break;
                } else {
                    if (compareAmountTo(addrBalance, XAmount.ZERO) > 0) {
                        remain.set(remain.get().subtract(addrBalance));
                        ourAccounts.put(new Address(keyPair2Hash(account), XDAG_FIELD_INPUT, addrBalance, true), account);
                    }
                }
            }
        } else {
            MutableBytes32 from = MutableBytes32.create();
            from.set(8, fromAddress.slice(8, 20));
            byte[] addr = from.slice(8, 20).toArray();

            if (txNonce == null) {
                UInt64 currentTxQuantity = kernel.getAddressStore().getTxQuantity(addr);
                txNonce = currentTxQuantity.add(UInt64.ONE);
            } else if (txNonce.compareTo(kernel.getAddressStore().getTxQuantity(addr).add(UInt64.ONE)) != 0) {
                processResponse.setCode(ERR_XDAG_PARAM);
                processResponse.setErrMsg("The nonce passed is incorrect. Please fill in the nonce according to the query value");
                return;
            }

            // If balance is sufficient
            if (compareAmountTo(kernel.getAddressStore().getBalanceByAddress(addr), remain.get()) >= 0) {
                // if (fromBlock.getInfo().getAmount() >= remain.get()) {
                ourAccounts.put(new Address(from, XDAG_FIELD_INPUT, remain.get(), true),
                        kernel.getWallet().getAccount(addr));
                remain.set(XAmount.ZERO);
            }
        }

        // Insufficient balance
        if (compareAmountTo(remain.get(), XAmount.ZERO) > 0) {
            processResponse.setCode(ERR_XDAG_BALANCE);
            processResponse.setErrMsg("balance not enough");
            return;
        }
        List<String> resInfo = Lists.newArrayList();
        // create transaction
        List<BlockWrapper> txs = kernel.getWallet().createTransactionBlock(ourAccounts, to, remark, txNonce);
        for (BlockWrapper blockWrapper : txs) {
            ImportResult result = kernel.getSyncMgr().validateAndAddNewBlock(blockWrapper);
            if (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST) {
                kernel.getChannelMgr().sendNewBlock(blockWrapper);
                Block block = new Block(new XdagBlock(blockWrapper.getBlock().getXdagBlock().getData().toArray()));
                List<Address> inputs = block.getInputs();
                UInt64 blockNonce = block.getTxNonceField().getTransactionNonce();
                for (Address input : inputs) {
                    if (input.getType() == XDAG_FIELD_INPUT) {
                        Bytes addr = BytesUtils.byte32ToArray(input.getAddress());
                        kernel.getAddressStore().updateTxQuantity(addr.toArray(), blockNonce);
                    }
                }
                resInfo.add(BasicUtils.hash2Address(blockWrapper.getBlock().getHashLow()));
            } else if (result == ImportResult.INVALID_BLOCK) {
                resInfo.add(result.getErrorInfo());
            }
        }

        processResponse.setCode(SUCCESS);
        processResponse.setResInfo(resInfo);
    }

    private Bytes32 checkFrom(String fromAddress, ProcessResponse processResponse)
        throws AddressFormatException {
        if (StringUtils.isBlank(fromAddress)) {
            return null;
        } else {
            return checkAddress(fromAddress, processResponse);
        }
    }

    private Bytes32 checkTo(String toAddress, ProcessResponse processResponse)
        throws AddressFormatException {
        if (StringUtils.isBlank(toAddress)) {
            processResponse.setCode(ERR_XDAG_DEST);
            processResponse.setErrMsg("To address is illegal");
            return null;
        } else {
            return checkAddress(toAddress, processResponse);
        }
    }

    private Bytes32 checkAddress(String address, ProcessResponse processResponse)
        throws AddressFormatException {
        Bytes32 hash = null;

        // check whether existed in blockchain
        if (WalletUtils.checkAddress(address)) {
            hash = pubAddress2Hash(address);
        } else {
            processResponse.setCode(ERR_XDAG_ADDRESS);
            processResponse.setErrMsg("To address is illegal");
        }

        return hash;
    }

    public boolean checkTransaction(Block block) {
        //reject transaction without input. For link block attack.
        if (block.getInputs().isEmpty()) {
            return false;
        }

        //check from address if reject Address.
        for (Address link : block.getInputs()) {
            if (WalletUtils.toBase58(link.getAddress().slice(8, 20)).equals(kernel.getConfig().getNodeSpec().getRejectAddress())) {
                return false;
            }
        }
        return true;
    }
    @Override
    public Object xdag_syncing(){
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

    static class SyncingResult {

        public String currentBlock;
        public String highestBlock;
        public boolean isSyncDone;

    }


}
