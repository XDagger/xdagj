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

import static io.xdag.cli.Commands.getStateByFlags;
import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.core.BlockState.MAIN;
import static io.xdag.core.BlockType.MAIN_BLOCK;
import static io.xdag.core.BlockType.SNAPSHOT;
import static io.xdag.core.BlockType.TRANSACTION;
import static io.xdag.core.BlockType.WALLET;
import static io.xdag.core.XdagField.FieldType.*;
import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.Hash2byte;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.pubAddress2Hash;
import static io.xdag.utils.PubkeyAddressUtils.checkAddress;
import static io.xdag.utils.PubkeyAddressUtils.toBase58;
import static io.xdag.utils.XdagTime.xdagTimestampToMs;

import io.xdag.Kernel;
import io.xdag.cli.Commands;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.BlockInfo;
import io.xdag.core.Blockchain;
import io.xdag.core.TxHistory;
import io.xdag.rpc.dto.AccountResultDTO;
import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.dto.BlockResultDTO.Link;
import io.xdag.rpc.dto.BlockResultDTO.TxLink;
import io.xdag.utils.BasicUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

public class XdagModuleChainBase implements XdagModuleChain {

    private final Blockchain blockchain;
    private final Kernel kernel;

    public XdagModuleChainBase(Blockchain blockchain, Kernel kernel) {
        this.blockchain = blockchain;
        this.kernel = kernel;
    }

    @Override
    public String getCoinBase() {
        return null;
    }

    @Override
    public BlockResultDTO getBlockByHash(String hash) {
        return getBlockDTOByHash(hash);
    }

    @Override
    public AccountResultDTO getAccountByAddress(String address) {
        return getAccountDTOByAddress(address);
    }

    private AccountResultDTO getAccountDTOByAddress(String address) {
        UInt64 balance = kernel.getAddressStore().getBalanceByAddress(Hash2byte(pubAddress2Hash(address).mutableCopy()));
        AccountResultDTO.AccountResultDTOBuilder accountResultDTOBuilder = AccountResultDTO.builder();
        accountResultDTOBuilder.address(address)
                .balance(String.format("%.9f", amount2xdag(balance)))
                .transactions(getTxHistory(address));
        return accountResultDTOBuilder.build();
    }

    @Override
    public BlockResultDTO getBlockByNumber(String bnOrId) {
        Block blockFalse = blockchain.getBlockByHeight(Long.parseLong(bnOrId));
        if (null == blockFalse) {
            return null;
        }
        Block blockTrue = blockchain.getBlockByHash(blockFalse.getHash(), true);
        if (blockTrue == null) {
            return transferBlockInfoToBlockResultDTO(blockFalse);
        }
        return transferBlockToBlockResultDTO(blockTrue);
    }

    @Override
    public String getRewardByNumber(String bnOrId) {
        try {
            long reward = blockchain.getReward(Long.parseLong(bnOrId));
            return String.format("%.9f", amount2xdag(reward));
        } catch (Exception e) {
            return e.getMessage();
        }

    }

    @Override
    public String getBalanceByNumber(String bnOrId) {
        Block block = blockchain.getBlockByHeight(Long.parseLong(bnOrId));
        if (null == block) {
            return null;
        }
        return String.format("%.9f", amount2xdag(block.getInfo().getAmount()));
    }

    @Override
    public Object getBlocksByNumber(String numberReq) {
        try {
            int number = numberReq == null ? 20 : Integer.parseInt(numberReq);// default 20
            List<Block> blocks = blockchain.listMainBlocks(number);
            List<BlockResultDTO> resultDTOS = new ArrayList<>();
            for (Block block : blocks) {
                BlockResultDTO dto = transferBlockToBriefBlockResultDTO(blockchain.getBlockByHash(block.getHash(), false));
                if (dto != null) {
                    resultDTOS.add(dto);
                }
            }
            return resultDTOS;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String getMaxXferBalance() {
        return Commands.getBalanceMaxXfer(kernel);
    }

    public BlockResultDTO getBlockDTOByHash(String hash) {
        Bytes32 blockHash;
        if (checkAddress(hash)) {
            return transferAccountToBlockResultDTO(hash);
        } else {
            if (StringUtils.length(hash) == 32) {
                blockHash = address2Hash(hash);
            } else {
                blockHash = BasicUtils.getHash(hash);
            }
            Block block = blockchain.getBlockByHash(blockHash, true);
            if (block == null) {
                block = blockchain.getBlockByHash(blockHash, false);
                return transferBlockInfoToBlockResultDTO(block);
            }
            return transferBlockToBlockResultDTO(block);
        }
    }

    private BlockResultDTO transferBlockToBriefBlockResultDTO(Block block) {
        if (null == block) {
            return null;
        }
        BlockResultDTO.BlockResultDTOBuilder BlockResultDTOBuilder = BlockResultDTO.builder();
        BlockResultDTOBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%.9f", amount2xdag(block.getInfo().getAmount())))
                .blockTime(xdagTimestampToMs(block.getTimestamp()))
                .timeStamp(block.getTimestamp())
                .flags(Integer.toHexString(block.getInfo().getFlags()))
                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
                        StandardCharsets.UTF_8).trim())
                .state(getStateByFlags(block.getInfo().getFlags()))
                .type(getType(block))
                .height(block.getInfo().getHeight());
        return BlockResultDTOBuilder.build();
    }

    private BlockResultDTO transferBlockInfoToBlockResultDTO(Block block) {
        if (null == block) {
            return null;
        }
        BlockResultDTO.BlockResultDTOBuilder BlockResultDTOBuilder = BlockResultDTO.builder();
        BlockResultDTOBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%.9f", amount2xdag(block.getInfo().getAmount())))
                .type(SNAPSHOT.getDesc())
                .blockTime(xdagTimestampToMs(kernel.getConfig().getSnapshotSpec().getSnapshotTime()))
                .timeStamp(kernel.getConfig().getSnapshotSpec().getSnapshotTime())
//                .flags(Integer.toHexString(block.getInfo().getFlags()))
//                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
//                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
//                        StandardCharsets.UTF_8).trim())
//                .state(getStateByFlags(block.getInfo().getFlags()))
//                .type(getType(block))
//                .refs(getLinks(block))
//                .height(block.getInfo().getHeight())
                .transactions(getTxLinks(block));
        return BlockResultDTOBuilder.build();
    }

    private BlockResultDTO transferAccountToBlockResultDTO(String address) {
        UInt64 balance = kernel.getAddressStore().getBalanceByAddress(Hash2byte(pubAddress2Hash(address).mutableCopy()));

        BlockResultDTO.BlockResultDTOBuilder BlockResultDTOBuilder = BlockResultDTO.builder();
        BlockResultDTOBuilder.address(address)
                .hash(null)
                .balance(String.format("%.9f", amount2xdag(balance)))
                .type("Wallet")
                .blockTime(xdagTimestampToMs(kernel.getConfig().getSnapshotSpec().getSnapshotTime()))
                .timeStamp(kernel.getConfig().getSnapshotSpec().getSnapshotTime())
//                .flags(Integer.toHexString(block.getInfo().getFlags()))
//                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
//                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
//                        StandardCharsets.UTF_8).trim())
                .state("Accepted")
//                .type(getType(block))
//                .refs(getLinks(block))
//                .height(block.getInfo().getHeight())
                .transactions(getTxHistory(address));
        return BlockResultDTOBuilder.build();
    }

    private BlockResultDTO transferBlockToBlockResultDTO(Block block) {
        if (null == block) {
            return null;
        }
        BlockResultDTO.BlockResultDTOBuilder BlockResultDTOBuilder = BlockResultDTO.builder();
        BlockResultDTOBuilder.address(hash2Address(block.getHash()))
                .hash(block.getHash().toUnprefixedHexString())
                .balance(String.format("%.9f", amount2xdag(block.getInfo().getAmount())))
                .blockTime(xdagTimestampToMs(block.getTimestamp()))
                .timeStamp(block.getTimestamp())
                .flags(Integer.toHexString(block.getInfo().getFlags()))
                .diff(toQuantityJsonHex(block.getInfo().getDifficulty()))
                .remark(block.getInfo().getRemark() == null ? "" : new String(block.getInfo().getRemark(),
                        StandardCharsets.UTF_8).trim())
                .state(getStateByFlags(block.getInfo().getFlags()))
                .type(getType(block))
                .refs(getLinks(block))
                .height(block.getInfo().getHeight())
                .transactions(getTxLinks(block));
        return BlockResultDTOBuilder.build();
    }

    private List<Link> getLinks(Block block) {
        List<Address> inputs = block.getInputs();
        List<Address> outputs = block.getOutputs();
        List<Link> links = new ArrayList<>();

        // fee update
        Link.LinkBuilder fee = Link.builder();
        fee.address(block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        : hash2Address(Bytes32.wrap(block.getInfo().getRef())))
                .hashlow(block.getInfo().getRef() == null ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        : Bytes32.wrap(block.getInfo().getRef()).toUnprefixedHexString())
                .amount(String.format("%.9f", amount2xdag(0))) // current fee is 0
                .direction(2);
        links.add(fee.build());


        for (Address input : inputs) {
            Link.LinkBuilder linkBuilder = Link.builder();
            linkBuilder.address(input.getIsAddress() ? toBase58(Hash2byte(input.getAddress())) : hash2Address(input.getAddress()))
                    .hashlow(input.getAddress().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(input.getAmount())))
                    .direction(0);
            links.add(linkBuilder.build());
        }

        for (Address output : outputs) {
            Link.LinkBuilder linkBuilder = Link.builder();
            if (output.getType().equals(XDAG_FIELD_COINBASE)) continue;
            linkBuilder.address(output.getIsAddress() ? toBase58(Hash2byte(output.getAddress())) : hash2Address(output.getAddress()))
                    .hashlow(output.getAddress().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(output.getAmount())))
                    .direction(1);
            links.add(linkBuilder.build());
        }

        return links;
    }

    private List<TxLink> getTxLinks(Block block) {
        List<TxHistory> txHistories = blockchain.getBlockTxHistoryByAddress(block.getHashLow());
        List<TxLink> txLinks = new ArrayList<>();
        // 1. earning info
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc()) && block.getInfo().getHeight() > kernel.getConfig().getSnapshotSpec().getSnapshotHeight()) {
            TxLink.TxLinkBuilder txLinkBuilder = TxLink.builder();
            String remark = "";
            if (block.getInfo().getRemark() != null && block.getInfo().getRemark().length != 0) {
                remark = new String(block.getInfo().getRemark(), StandardCharsets.UTF_8).trim();
            }
            txLinkBuilder.address(toBase58(Hash2byte(block.getCoinBase().getAddress())))
                    .hashlow(block.getCoinBase().getAddress().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(blockchain.getReward(block.getInfo().getHeight()))))
                    .direction(2)
                    .time(xdagTimestampToMs(block.getTimestamp()))
                    .remark(remark);
            txLinks.add(txLinkBuilder.build());
        }
        // 2. tx history info
        for (TxHistory txHistory : txHistories) {
            BlockInfo blockInfo = blockchain.getBlockByHash(txHistory.getAddress().getAddress(), false).getInfo();
            if((blockInfo.flags&BI_APPLIED)==0){
                continue;
            }
            TxLink.TxLinkBuilder txLinkBuilder = TxLink.builder();
            txLinkBuilder.address(hash2Address(txHistory.getAddress().getAddress()))
                    .hashlow(txHistory.getAddress().getAddress().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(txHistory.getAddress().getAmount())))
                    .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_IN) ? 0 :
                            txHistory.getAddress().getType().equals(XDAG_FIELD_OUT) ? 1 : 3)
                    .time(xdagTimestampToMs(txHistory.getTimeStamp()))
                    .remark(txHistory.getRemark());
            txLinks.add(txLinkBuilder.build());
        }
        return txLinks;
    }

    private List<TxLink> getTxHistory(String address) {
        List<TxHistory> txHistories = blockchain.getBlockTxHistoryByAddress(pubAddress2Hash(address));
        List<TxLink> txLinks = new ArrayList<>();
        for (TxHistory txHistory : txHistories) {
            BlockInfo blockInfo = blockchain.getBlockByHash(txHistory.getAddress().getAddress(), false).getInfo();
            if((blockInfo.flags&BI_APPLIED)==0){
                continue;
            }
            TxLink.TxLinkBuilder txLinkBuilder = TxLink.builder();
            txLinkBuilder.address(hash2Address(txHistory.getAddress().getAddress()))
                    .hashlow(txHistory.getAddress().getAddress().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(txHistory.getAddress().getAmount())))
                    .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_INPUT) ? 0 :
                            txHistory.getAddress().getType().equals(XDAG_FIELD_OUTPUT) ? 1 :
                            txHistory.getAddress().getType().equals(XDAG_FIELD_COINBASE) ? 2 : 3)
                    .time(xdagTimestampToMs(txHistory.getTimeStamp()))
                    .remark(txHistory.getRemark());
            txLinks.add(txLinkBuilder.build());
        }
        return txLinks;
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

}
