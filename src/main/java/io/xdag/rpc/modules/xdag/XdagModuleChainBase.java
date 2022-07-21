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
import static io.xdag.core.BlockState.MAIN;
import static io.xdag.core.BlockType.MAIN_BLOCK;
import static io.xdag.core.BlockType.SNAPSHOT;
import static io.xdag.core.BlockType.TRANSACTION;
import static io.xdag.core.BlockType.WALLET;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.XdagTime.xdagTimestampToMs;

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.core.TxHistory;
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

public class XdagModuleChainBase implements XdagModuleChain {

    private Blockchain blockchain;

    public XdagModuleChainBase(Blockchain blockchain) {
        this.blockchain = blockchain;
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
    public BlockResultDTO getBlockByNumber(String bnOrId) {
        Block block = blockchain.getBlockByHeight(Long.parseLong(bnOrId));
        if (null == block) {
            return null;
        }
        return transferBlockToBlockResultDTO(blockchain.getBlockByHash(block.getHash(), true));
    }

    @Override
    public String getRewardByNumber(String bnOrId) {
        try {
            long reward = blockchain.getReward(Long.parseLong(bnOrId));
            return String.format("%.9f", amount2xdag(reward));
        }catch (Exception e) {
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
            int number = numberReq == null?20:Integer.parseInt(numberReq);// default 20
            List<Block> blocks = blockchain.listMainBlocks(number);
            List<BlockResultDTO> resultDTOS = new ArrayList<>();
            for (Block block : blocks) {
                BlockResultDTO dto = transferBlockToBriefBlockResultDTO(blockchain.getBlockByHash(block.getHash(), true));
                if (dto != null) {
                    resultDTOS.add(dto);
                }
            }
            return resultDTOS;
        }catch (Exception e) {
            return e.getMessage();
        }
    }

    public BlockResultDTO getBlockDTOByHash(String hash) {
        Bytes32 blockHash;
        if (StringUtils.length(hash) == 32) {
            blockHash = address2Hash(hash);
        } else {
            blockHash = BasicUtils.getHash(hash);
        }
        Block block = blockchain.getBlockByHash(blockHash, true);
        if (block == null) {
            block = blockchain.getBlockByHash(blockHash,false);
            return transferBlockInfoToBlockResultDTO(block);
        }
        return transferBlockToBlockResultDTO(block);
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
//                .blockTime(xdagTimestampToMs(block.getTimestamp()))
//                .timeStamp(block.getTimestamp())
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
            linkBuilder.address(hash2Address(input.getHashLow()))
                    .hashlow(input.getHashLow().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(input.getAmount().longValue())))
                    .direction(0);
            links.add(linkBuilder.build());
        }

        for (Address output : outputs) {
            Link.LinkBuilder linkBuilder = Link.builder();
            linkBuilder.address(hash2Address(output.getHashLow()))
                    .hashlow(output.getHashLow().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(output.getAmount().longValue())))
                    .direction(1);
            links.add(linkBuilder.build());
        }

        return links;
    }

    private List<TxLink> getTxLinks(Block block) {
        List<TxHistory> txHistories = blockchain.getBlockTxHistoryByAddress(block.getHashLow());
        List<TxLink> txLinks = new ArrayList<>();
        // 1. earning info
        if (getStateByFlags(block.getInfo().getFlags()).equals(MAIN.getDesc())) {
            TxLink.TxLinkBuilder txLinkBuilder = TxLink.builder();
            String remark = "";
            if (block.getInfo().getRemark()!=null && block.getInfo().getRemark().length != 0) {
                remark = new String(block.getInfo().getRemark(), StandardCharsets.UTF_8).trim();
            }
            txLinkBuilder.address(hash2Address(block.getHashLow()))
                    .hashlow(block.getHashLow().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(blockchain.getReward(block.getInfo().getHeight()))))
                    .direction(2)
                    .time(xdagTimestampToMs(block.getTimestamp()))
                    .remark(remark);
            txLinks.add(txLinkBuilder.build());
        }
        // 2. tx history info
        for (TxHistory txHistory : txHistories) {
            TxLink.TxLinkBuilder txLinkBuilder = TxLink.builder();
            txLinkBuilder.address(hash2Address(txHistory.getAddress().getHashLow()))
                    .hashlow(txHistory.getAddress().getHashLow().toUnprefixedHexString())
                    .amount(String.format("%.9f", amount2xdag(txHistory.getAddress().getAmount().longValue())))
                    .direction(txHistory.getAddress().getType().equals(XDAG_FIELD_IN) ? 0 : 1)
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
