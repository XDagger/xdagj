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

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.message.NewBalanceMessage;
import io.xdag.mine.message.NewTaskMessage;
import io.xdag.mine.message.TaskShareMessage;
import io.xdag.mine.miner.Miner;
import io.xdag.mine.miner.MinerStates;
import io.xdag.net.message.Message;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FastByteComparisons;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class Miner03 extends SimpleChannelInboundHandler<Message> {

  private Kernel kernel;
  private MinerChannel channel;
  private ChannelHandlerContext ctx;
  private MinerManager minerManager;
  private SyncManager syncManager;

  public Miner03(MinerChannel channel, Kernel kernel) {
    this.channel = channel;
    this.kernel = kernel;
    minerManager = kernel.getMinerManager();
    syncManager = kernel.getSyncMgr();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
    switch (msg.getCommand()) {
      case NEW_BALANCE:
        processNewBalance((NewBalanceMessage) msg);
        break;
      case TASK_SHARE:
        processTaskShare((TaskShareMessage) msg);
        break;
      case NEW_TASK:
        processNewTask((NewTaskMessage) msg);
        break;
      case NEW_BLOCK:
        processNewBlock((NewBlockMessage) msg);
        break;
      default:
        log.warn("没有这种对应数据的消息类型，内容为【{}】", msg.getEncoded());
        break;
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
      log.debug("远程主机关闭了一个连接");
      ctx.channel().closeFuture();
    } else {
      cause.printStackTrace();
    }
    channel.onDisconnect();
  }

  /** *********************** Message Processing * *********************** */
  protected void processNewBlock(NewBlockMessage msg) {
    log.debug(" Receive a Tx");
    Block block = msg.getBlock();
    syncManager.validateAndAddNewBlock(new BlockWrapper(block, kernel.getConfig().getTTL()));
  }

  protected void processNewBalance(NewBalanceMessage msg) {
    // TODO: 2020/5/9 处理矿工接受到的余额信息 矿工功能
    log.debug(" Receive New Balance [{}]", Hex.toHexString(msg.getEncoded()));
  }

  protected void processNewTask(NewTaskMessage msg) {
    // TODO: 2020/5/9 处理矿工收到的新任务 矿工功能
    log.debug(" Miner Receive New Task [{}]", Hex.toHexString(msg.getEncoded()));
  }

  protected void processTaskShare(TaskShareMessage msg) {
    log.debug(" Pool Receive Share");
//    if (FastByteComparisons.compareTo(
//                msg.getEncoded(), 8, 24, channel.getAccountAddressHash(), 8, 24)
//            == 0
//        && channel.getSharesCounts() <= kernel.getConfig().getMaxShareCountPerChannel()) {
//
//      channel.addShareCounts(1);
//      minerManager.onNewShare(channel, msg);
//    } else {
//      System.out.println("接收到的share不对应");
//      log.debug("Too many Shares,Reject...");
//    }

    //share地址不一致，修改对应的miner地址
    if (FastByteComparisons.compareTo( msg.getEncoded(), 8, 24, channel.getAccountAddressHash(), 8, 24) != 0) {
      byte[] zero = new byte[8];
      byte[] blockHash = new byte[32];
      BytesUtils.isFullZero(zero);

      byte[] hashLow = BytesUtils.merge(zero, BytesUtils.subArray(msg.getEncoded(),8 ,24));
      System.out.println("拼接后的hashlow = " + Hex.toHexString(hashLow));
      Block block = kernel.getBlockchain().getBlockByHash(hashLow,false);
      Miner oldMiner = channel.getMiner();
      //不为空，表示可以找到对应的区块，地址存在
      if (block != null) {

        blockHash = block.getHash();
        //System.out.println("查找到的区块哈希为 = " + Hex.toHexString(blockHash) + "转换的地址为 = " + BasicUtils.hash2Address(blockHash ));
        Miner miner = kernel.getMinerManager().getActivateMiners().get(new ByteArrayWrapper(blockHash));
        if (miner != null) {
          System.out.println("查找到对应的miner，miner,地址为 = " + BasicUtils.hash2Address(miner.getAddressHash()));

        }else {
          //创建一个新的miner对象
          miner = new Miner(blockHash);
          minerManager.addActiveMiner(miner);
        }
        //改变channel对应的地址，并替换新的miner连接
//        channel.setMiner(miner);
//        channel.setAccountAddressHash(blockHash);
        //channel.setAccountAddressHashLow(BytesUtils.fixBytes(blockHash, 8, 24));
        channel.updateMiner(miner);

//        miner.putChannel(channel.getInetAddress(), channel);
//        miner.addChannelCounts(1);
//        miner.setMinerStates(MinerStates.MINER_ACTIVE);

        oldMiner.setMinerStates(MinerStates.MINER_ARCHIVE);
        minerManager.getActivateMiners().remove(new ByteArrayWrapper(oldMiner.getAddressHash()));
      }else {
        //to do nothing
        System.out.println("找不到对应的区块");

        log.debug("can not receive the share, No such address exists.");
        ctx.close();
        minerManager.getActivateMiners().remove(new ByteArrayWrapper(oldMiner.getAddressHash()));
      }
    }

    if (channel.getSharesCounts() <= kernel.getConfig().getMaxShareCountPerChannel()) {
      channel.addShareCounts(1);
      minerManager.onNewShare(channel, msg);
    }else {
      System.out.println("Too many Shares,Reject...");
      log.debug("Too many Shares,Reject...");
    }

  }

  /** 发送任务消息 */
  public void sendMessage(byte[] bytes) {
    ctx.channel().writeAndFlush(bytes);
  }

  public void dropConnection() {
    disconnect();
  }

  public void disconnect() {
    ctx.close();
    this.channel.setActive(false);
    kernel.getChannelsAccount().getAndDecrement();
    minerManager.removeUnactivateChannel(this.channel);
  }
}
