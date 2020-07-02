package io.xdag.mine.handler;

import static io.xdag.utils.BasicUtils.crc32Verify;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.XdagBlock;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.mine.message.MinerBlockMessage;
import io.xdag.mine.message.NewBalanceMessage;
import io.xdag.mine.message.NewTaskMessage;
import io.xdag.mine.message.TaskShareMessage;
import io.xdag.net.message.Message;

import io.xdag.utils.BytesUtils;

import io.xdag.utils.FastByteComparisons;

import org.spongycastle.util.encoders.Hex;

public class Miner03 extends SimpleChannelInboundHandler<Message> {
  public static final Logger logger = LoggerFactory.getLogger(Miner03.class);

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
        processNewBlock((MinerBlockMessage) msg);
        break;
      default:
        logger.warn("没有这种对应数据的消息类型，内容为【{}】", msg.getEncoded());
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
      logger.debug("远程主机关闭了一个连接");
      ctx.channel().closeFuture();
      channel.onDisconnect();

    } else {
      cause.printStackTrace();
    }
  }

  /** *********************** Message Processing * *********************** */
  protected synchronized void processNewBlock(MinerBlockMessage msg) {
    logger.debug(" 处理矿池接受到的交易信息");
    // 先进行简单的验证 有效的话再放到queue里面

    byte[] uncryptData = msg.getEncoded();
    long transportHeader = BytesUtils.bytesToLong(uncryptData, 0, true);
    long dataLength = (transportHeader >> 16 & 0xffff);
    int crc = BytesUtils.bytesToInt(uncryptData, 4, true);

    // 清除transportheader
    System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
    // 验证 看是否可以加到本地的存储中
    if (dataLength != 512 || !crc32Verify(uncryptData, crc)) {
      Block block = new Block(new XdagBlock(uncryptData));
      syncManager.validateAndAddNewBlock(new BlockWrapper(block, 1, kernel.getClient().getNode()));
    }
  }

  protected synchronized void processNewBalance(NewBalanceMessage msg) {
    // TODO: 2020/5/9 处理矿工接受到的余额信息
    logger.debug(" 处理矿池接受到的交易信息[{}]", Hex.toHexString(msg.getEncoded()));
  }

  protected synchronized void processNewTask(NewTaskMessage msg) {
    // TODO: 2020/5/9 处理矿工收到的新任务
    logger.debug(" 处理矿工收到的新任务[{}]", Hex.toHexString(msg.getEncoded()));
  }

  protected synchronized void processTaskShare(TaskShareMessage msg) {
    logger.debug(" 处理矿池接收到的任务反馈");

    if (FastByteComparisons.compareTo(
                msg.getEncoded(), 8, 24, channel.getAccountAddressHash(), 8, 24)
            == 0
        && channel.getSharesCounts() <= kernel.getConfig().getMaxShareCountPerChannel()) {

      channel.addShareCounts(1);
      minerManager.onNewShare(channel, msg);

    } else {
      logger.debug("shares的值超过限制，不接受");
    }
  }

  /** 发送任务消息 */
  public void sendMessage(byte[] bytes) {

    // logger.debug("发送消息。。。。。{}",Hex.encodeHexString(bytes));
    ctx.channel().writeAndFlush(bytes);
  }

  public synchronized void dropConnection() {
    disconnect();
  }

  public void disconnect() {
    ctx.close();
    this.channel.setActive(false);
    minerManager.removeUnactivateChannel(this.channel);
  }
}
