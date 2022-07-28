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

package io.xdag.mine.handler;

import static io.xdag.utils.BytesUtils.compareTo;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ImportResult;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.message.NewBalanceMessage;
import io.xdag.mine.message.NewTaskMessage;
import io.xdag.mine.message.TaskShareMessage;
import io.xdag.mine.message.WorkerNameMessage;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.message.Message;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@Slf4j
public class Miner03 extends SimpleChannelInboundHandler<Message> {

    private final Kernel kernel;
    private final MinerChannel channel;
    private final MinerManager minerManager;
    private final SyncManager syncManager;
    private ChannelHandlerContext ctx;

    public Miner03(MinerChannel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        minerManager = kernel.getMinerManager();
        syncManager = kernel.getSyncMgr();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        switch (msg.getCommand()) {
            case NEW_BALANCE -> processNewBalance((NewBalanceMessage) msg);
            case TASK_SHARE -> processTaskShare((TaskShareMessage) msg);
            case NEW_TASK -> processNewTask((NewTaskMessage) msg);
            case NEW_BLOCK -> processNewBlock((NewBlockMessage) msg);
            case WORKER_NAME ->processWorkerName((WorkerNameMessage) msg);
            default -> log.warn("没有这种对应数据的消息类型，内容为【{}】", msg.getEncoded());
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        channel.setCtx(ctx);
        this.ctx = ctx;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("Close miner at time:{}", XdagTime.format(new Date()));
            ctx.channel().closeFuture();
        } else {
            cause.printStackTrace();
        }
        channel.onDisconnect();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("Close miner at time:{}", XdagTime.format(new Date()));
        ctx.channel().closeFuture();
        channel.setActive(false);
    }

    /**
     * ********************** Message Processing * ***********************
     */
    protected void processNewBlock(NewBlockMessage msg) {
        Block block = msg.getBlock();
        ImportResult importResult = syncManager
                .validateAndAddNewBlock(new BlockWrapper(block, kernel.getConfig().getNodeSpec().getTTL()));
        if (importResult.isNormal()) {
            log.info("XDAG:receive transaction. A Transaction from wallet/miner, block hash:{}",
                    block.getHash().toHexString());
        }
    }

    protected void processNewBalance(NewBalanceMessage msg) {
        // TODO: 2020/5/9 处理矿工接受到的余额信息 矿工功能
        log.debug("Receive New Balance [{}]", msg.getEncoded().toHexString());
    }

    protected void processNewTask(NewTaskMessage msg) {
        // TODO: 2020/5/9 处理矿工收到的新任务 矿工功能
        log.debug("Miner Receive New Task [{}]", msg.getEncoded().toHexString());
    }

    protected void processTaskShare(TaskShareMessage msg) {
        log.debug("Pool Receive Share");

        //share地址不一致，修改对应的miner地址
        if (compareTo(msg.getEncoded().toArray(), 8, 24, channel.getAccountAddressHash().toArray(), 8, 24) != 0) {
            byte[] zero = new byte[8];
            Bytes32 blockHash;
            BytesUtils.isFullZero(zero);

            Bytes32 hashLow = Bytes32
                    .wrap(BytesUtils.merge(zero, BytesUtils.subArray(msg.getEncoded().toArray(), 8, 24)));
            Block block = kernel.getBlockchain().getBlockByHash(hashLow, false);
            Miner oldMiner = channel.getMiner();
            //不为空，表示可以找到对应的区块，地址存在
            if (block != null) {
                blockHash = block.getHash();
                Miner miner = kernel.getMinerManager().getActivateMiners()
                        .get(blockHash);
                if (miner == null) {
                    log.debug("creat a new miner");
                    miner = new Miner(blockHash);
                    minerManager.addActiveMiner(miner);
                }
                //改变channel对应的地址，并替换新的miner连接
                channel.updateMiner(miner);
                log.info("XDAG:randomXminer. channel {} with wallet-address {} is randomXminer",
                        channel.getInetAddress().toString(), blockHash.toHexString());

                oldMiner.setMinerStates(MinerStates.MINER_ARCHIVE);
                minerManager.getActivateMiners().remove(oldMiner.getAddressHash());
            } else {
                //to do nothing
                log.debug("can not receive the share, No such address exists.");
                ctx.close();
                minerManager.getActivateMiners().remove(oldMiner.getAddressHash());
            }
        }

        if (channel.getSharesCounts() <= kernel.getConfig().getPoolSpec().getMaxShareCountPerChannel()) {
            channel.addShareCounts(1);
            minerManager.onNewShare(channel, msg);
        } else {
            log.debug("Too many Shares,Reject...");
        }

    }

    private void processWorkerName(WorkerNameMessage msg) {
        log.debug("Pool Receive Worker Name");
        byte[] workerNameByte = msg.getEncoded().reverse().slice(4).toArray();
        String workerName = new String(workerNameByte, StandardCharsets.UTF_8).trim();
        channel.setWorkerName(workerName);
    }

    /**
     * 发送任务消息
     */
    public void sendMessage(Bytes bytes) {
        ctx.channel().writeAndFlush(bytes.toArray());
    }

    public void dropConnection() {
        disconnect();
    }

    public void disconnect() {
        ctx.close();
        this.channel.setActive(false);
        kernel.getChannelsAccount().getAndDecrement();
        //minerManager.removeUnactivateChannel(this.channel);
        log.info("XDAG:channel close. channel {} with wallet-address {} close",
                this.channel.getInetAddress().toString(), this.channel.getMiner().getAddressHash().toHexString());
    }
}
