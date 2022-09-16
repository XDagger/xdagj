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

package io.xdag.net.handler;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.Kernel;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.XdagStats;
import io.xdag.net.Channel;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.Message;
import io.xdag.net.message.impl.*;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.XdagTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Xdag03 extends XdagHandler {

    public Xdag03(Kernel kernel, Channel channel) {
        this.kernel = kernel;
        this.channel = channel;
        this.blockchain = kernel.getBlockchain();
        this.syncMgr = kernel.getSyncMgr();
        this.version = XdagVersion.V03;
        this.netDBManager = kernel.getNetDBMgr();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        switch (msg.getCommand()) {
            case NEW_BLOCK -> processNewBlock((NewBlockMessage) msg);
            case BLOCK_REQUEST -> processBlockRequest((BlockRequestMessage) msg);
            case BLOCKS_REQUEST -> processBlocksRequest((BlocksRequestMessage) msg);
            case BLOCKS_REPLY -> processBlocksReply((BlocksReplyMessage) msg);
            case SUMS_REQUEST -> processSumsRequest((SumRequestMessage) msg);
            case SUMS_REPLY -> processSumsReply((SumReplyMessage) msg);
            case BLOCKEXT_REQUEST -> processBlockExtRequest((BlockExtRequestMessage) msg);
            default -> {
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        msgQueue.activate(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("channelInactive:[{}] ", ctx.toString());
        killTimers();
        disconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("exceptionCaught:[{}]", cause.getMessage(), cause);
        ctx.close();
        killTimers();
        disconnect();
    }

    @Override
    public void dropConnection() {
        log.info("Peer {}: is a bad one, drop", channel.getNode().getAddress());
        disconnect();
    }

    public void killTimers() {
        log.debug("msgQueue stop");
        msgQueue.close();
    }

    /**
     * ********************** Message Processing * ***********************
     */
    protected void processNewBlock(NewBlockMessage msg) {
        Block block = msg.getBlock();
        log.debug("processNewBlock:{} from node {}", block.getHashLow(), channel.getInetSocketAddress());
        BlockWrapper bw = new BlockWrapper(block, msg.getTtl() - 1, channel.getNode());
        syncMgr.validateAndAddNewBlock(bw);
    }

    /**
     * 区块请求响应一个区块 并开启一个线程不断发送一段时间内的区块 *
     */
    protected void processBlocksRequest(BlocksRequestMessage msg) {
//        log.debug("processBlocksRequest:" + msg);
        // 更新全网状态
        updateXdagStats(msg);
        long startTime = msg.getStarttime();
        long endTime = msg.getEndtime();
        long random = msg.getRandom();

        // TODO: paulochen 处理多区块请求
//        // 如果大于快照点的话 我可以发送
//        if (startTime > 1658318225407L) {
//            // TODO: 如果请求时间间隔过大，启动新线程发送，目的是避免攻击
        log.debug("Send blocks between {} and {} to node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getInetSocketAddress());
        List<Block> blocks = blockchain.getBlocksByTime(startTime, endTime);
        for (Block block : blocks) {
            sendNewBlock(block, 1);
        }
        sendMessage(new BlocksReplyMessage(startTime, endTime, random, kernel.getBlockchain().getXdagStats(),
                netDBManager.getNetDB()));
//        }
    }

    protected void processBlocksReply(BlocksReplyMessage msg) {
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<Bytes> sf = kernel.getSync().getBlocksRequestMap().get(randomSeq);
        if (sf != null) {
            sf.set(Bytes.wrap(new byte[]{0}));
        }
    }

    /**
     * 将sumRequest的后8个字段填充为自己的sum 修改type类型为reply 发送
     */
    protected void processSumsRequest(SumRequestMessage msg) {
        updateXdagStats(msg);
        MutableBytes sums = MutableBytes.create(256);
        // TODO: paulochen 处理sum请求
        kernel.getBlockStore().loadSum(msg.getStarttime(),msg.getEndtime(),sums);
            SumReplyMessage reply = new SumReplyMessage(msg.getEndtime(), msg.getRandom(),
                    kernel.getBlockchain().getXdagStats(), sums,netDBManager.getNetDB());
            sendMessage(reply);
    }

    protected void processSumsReply(SumReplyMessage msg) {
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<Bytes> sf = kernel.getSync().getSumsRequestMap().get(randomSeq);
        if (sf != null) {
            sf.set(msg.getSum());
        }
    }

    protected void processBlockExtRequest(BlockExtRequestMessage msg) {
    }

    protected void processBlockRequest(BlockRequestMessage msg) {
        Bytes32 hash = msg.getHash();
        MutableBytes32 find = MutableBytes32.create();
        find.set(8, hash.reverse().slice(8, 24));
        Block block = blockchain.getBlockByHash(find, true);
        if (block != null) {
//            log.debug("processBlockRequest: findBlock" + Hex.toHexString(block.getHashLow()));
            NewBlockMessage message = new NewBlockMessage(block, kernel.getConfig().getNodeSpec().getTTL());
            sendMessage(message);
        }
    }

    /**
     * ********************** Message Sending * ***********************
     */
    @Override
    public void sendNewBlock(Block newBlock, int TTL) {
//        log.debug("sendNewBlock:" + Hex.toHexString(newBlock.getHashLow()));
        log.debug("send block:{} to node:{}", newBlock.getHashLow(), channel.getInetSocketAddress());
        NewBlockMessage msg = new NewBlockMessage(newBlock, TTL);
        sendMessage(msg);
    }

    @Override
    public long sendGetBlocks(long startTime, long endTime) {
        log.debug("Request blocks between {} and {} from node {}",
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(startTime)),
                FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(endTime)),
                channel.getInetSocketAddress());
        BlocksRequestMessage msg = new BlocksRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats(),
                netDBManager.getNetDB());
        sendMessage(msg);
        return msg.getRandom();
    }

    @Override
    public long sendGetBlock(MutableBytes32 hash) {
//        log.debug("sendGetBlock:[{}]", Hex.toHexString(hash));
        BlockRequestMessage msg = new BlockRequestMessage(hash, kernel.getBlockchain().getXdagStats(),
                netDBManager.getNetDB());
        log.debug("Request block {} from node {}", hash, channel.getInetSocketAddress());
        sendMessage(msg);
        return msg.getRandom();
    }

    @Override
    public long sendGetSums(long startTime, long endTime) {
        SumRequestMessage msg = new SumRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats(),
                netDBManager.getNetDB());
        sendMessage(msg);
        return msg.getRandom();
    }

    @Override
    public void sendMessage(Message message) {
        if (msgQueue.isRunning()) {
            msgQueue.sendMessage(message);
        } else {
            log.debug("msgQueue is close");
        }
    }

    protected void disconnect() {
        msgQueue.disconnect();
    }

    @Override
    public void activate() {
        log.debug("Xdag protocol activate");
        //// xdagListener.trace("Xdag protocol activate");
    }

    public void updateXdagStats(AbstractMessage message) {
        XdagStats remoteXdagStats = message.getXdagStats();
        kernel.getBlockchain().getXdagStats().update(remoteXdagStats);
        kernel.getNetDBMgr().updateNetDB(message.getNetDB());
    }

}
