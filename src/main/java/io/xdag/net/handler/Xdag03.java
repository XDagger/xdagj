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

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import com.google.common.util.concurrent.SettableFuture;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.Kernel;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.Message;
import io.xdag.core.XdagStats;
import io.xdag.net.message.impl.BlockExtRequestMessage;
import io.xdag.net.message.impl.BlockRequestMessage;
import io.xdag.net.message.impl.BlocksReplyMessage;
import io.xdag.net.message.impl.BlocksRequestMessage;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.net.message.impl.SumReplyMessage;
import io.xdag.net.message.impl.SumRequestMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Xdag03 extends XdagHandler {
    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);
        @Override
        public Thread newThread(@Nonnull Runnable r) {
            return new Thread(r, "sendThread-" + cnt.getAndIncrement());
        }
    };

    private XdagVersion version = XdagVersion.V03;

    public Xdag03(Kernel kernel, XdagChannel channel) {
        this.kernel = kernel;
        this.channel = channel;
        this.blockchain = kernel.getBlockchain();
        this.syncMgr = kernel.getSyncMgr();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        switch (msg.getCommand()) {
        case NEW_BLOCK:
            processNewBlock((NewBlockMessage) msg);
            break;
        case BLOCK_REQUEST:
            processBlockRequest((BlockRequestMessage) msg);
            break;
        case BLOCKS_REQUEST:
            processBlocksRequest((BlocksRequestMessage) msg);
            break;
        case BLOCKS_REPLY:
            processBlocksReply((BlocksReplyMessage) msg);
            break;
        case SUMS_REQUEST:
            processSumsRequest((SumRequestMessage) msg);
            break;
        case SUMS_REPLY:
            processSumsReply((SumReplyMessage) msg);
            break;
        case BLOCKEXT_REQUEST:
            processBlockExtRequest((BlockExtRequestMessage) msg);
            break;
        default:
            break;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        msgQueue.activate(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("channelInactive:[{}] ", ctx.toString());
        this.killTimers();
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

    /** *********************** Message Processing * *********************** */
    protected void processNewBlock(NewBlockMessage msg) {
        Block block = msg.getBlock();
        log.info("processNewBlock:{}", Hex.toHexString(block.getHashLow()));
        BlockWrapper bw = new BlockWrapper(block, msg.getTtl() - 1, channel.getNode());
        syncMgr.validateAndAddNewBlock(bw);
//        if (!syncMgr.validateAndAddNewBlock(bw)) {
//            dropConnection();
//        }
    }

    /** 区块请求响应一个区块 并开启一个线程不断发送一段时间内的区块 * */
    protected void processBlocksRequest(BlocksRequestMessage msg) {
//        log.debug("processBlocksRequest:" + msg);
        updateXdagStats(msg);
        long startTime = msg.getStarttime();
        long endTime = msg.getEndtime();
        long random = msg.getRandom();

        List<Block> blocks = blockchain.getBlockByTime(startTime, endTime);
        for (Block block : blocks) {
            sendNewBlock(block, 1);
        }
        sendMessage(new BlocksReplyMessage(startTime, endTime, random, kernel.getBlockchain().getXdagStats()));
    }

    protected void processBlocksReply(BlocksReplyMessage msg) {
//        log.debug("processBlocksReply:" + msg);
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<byte[]> sf = kernel.getSync().getBlocksRequestMap().get(randomSeq);
        if(sf != null) {
            sf.set(new byte[]{0});
        }
    }

    /** 将sumRequest的后8个字段填充为自己的sum 修改type类型为reply 发送 */
    protected void processSumsRequest(SumRequestMessage msg) {
        log.debug("processSumsRequest:" + msg);
        updateXdagStats(msg);
        byte[] sums = new byte[256];
        kernel.getBlockStore().getSimpleFileStore().loadSum(msg.getStarttime(), msg.getEndtime(),sums);
        SumReplyMessage reply = new SumReplyMessage(msg.getEndtime(), msg.getRandom(), kernel.getBlockchain().getXdagStats(), sums);
        sendMessage(reply);
        log.debug("processSumsRequest:" + reply);
    }

    protected void processSumsReply(SumReplyMessage msg) {
//        log.debug("processSumReply:" + msg);
        updateXdagStats(msg);
        long randomSeq = msg.getRandom();
        SettableFuture<byte[]> sf = kernel.getSync().getSumsRequestMap().get(randomSeq);
        if(sf != null) {
            sf.set(msg.getSum());
        }
    }

    protected void processBlockExtRequest(BlockExtRequestMessage msg) {
    }

    protected void processBlockRequest(BlockRequestMessage msg) {
        log.debug("processBlockRequest:" + msg);
        byte[] find = new byte[32];
        byte[] hash = msg.getHash();
        hash = Arrays.reverse(hash);
        System.arraycopy(hash, 8, find, 8, 24);
        Block block = blockchain.getBlockByHash(find, true);
        if (block != null) {
            NewBlockMessage message = new NewBlockMessage(block, kernel.getConfig().getTTL());
            sendMessage(message);
        }
    }

    /** *********************** Message Sending * *********************** */
    @Override
    public void sendNewBlock(Block newBlock, int TTL) {
        log.debug("sendNewBlock:" + Hex.toHexString(newBlock.getHashLow()));
        NewBlockMessage msg = new NewBlockMessage(newBlock, TTL);
        sendMessage(msg);
    }

    @Override
    public long sendGetBlocks(long startTime, long endTime) {
//        log.debug("sendGetBlocks:[startTime={} endTime={}]", startTime, endTime);
        BlocksRequestMessage msg = new BlocksRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats());
        sendMessage(msg);
        return msg.getRandom();
    }

    @Override
    public boolean isIdle() {
        return false;
    }

    @Override
    public long sendGetBlock(byte[] hash) {
        log.debug("sendGetBlock:[{}]", Hex.toHexString(hash));
        BlockRequestMessage msg = new BlockRequestMessage(hash, kernel.getBlockchain().getXdagStats());
        sendMessage(msg);
        return msg.getRandom();
    }

    @Override
    public long sendGetSums(long startTime, long endTime) {
//        log.debug("sendGetSums:startTime=[{}],endTime=[{}]", startTime, endTime);
        SumRequestMessage msg = new SumRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats());
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

    @Override
    public void disableBlocks() {
        // TODO Auto-generated method stub

    }

    @Override
    public void enableBlocks() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSyncDone(boolean done) {
        // TODO Auto-generated method stub

    }

    public void updateXdagStats(AbstractMessage message) {
        XdagStats remoteXdagStats = message.getXdagStats();
        kernel.getBlockchain().getXdagStats().update(remoteXdagStats);
        kernel.getNetDBMgr().updateNetDB(message.getNetDB());
    }

}
