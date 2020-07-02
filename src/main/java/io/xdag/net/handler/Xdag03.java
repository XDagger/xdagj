package io.xdag.net.handler;

import static io.xdag.config.Constants.REQUEST_BLOCKS_MAX_TIME;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.Kernel;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.Blockchain;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.Message;
import io.xdag.net.message.NetDB;
import io.xdag.net.message.NetStatus;
import io.xdag.net.message.impl.BlockExtRequestMessage;
import io.xdag.net.message.impl.BlockRequestMessage;
import io.xdag.net.message.impl.BlocksReplyMessage;
import io.xdag.net.message.impl.BlocksRequestMessage;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.net.message.impl.SumReplyMessage;
import io.xdag.net.message.impl.SumRequestMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Xdag03 extends XdagHandler {

  private XdagVersion version = XdagVersion.V03;

  private static final ThreadFactory factory =
      new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
          return new Thread(r, "sendThread-" + cnt.getAndIncrement());
        }
      };

  //ExecutorService sendThreads = Executors.newSingleThreadExecutor(factory);
  ExecutorService sendThreads = new ScheduledThreadPoolExecutor(1,factory);
  List<ListenableFuture<Integer>> futures = new ArrayList<>();

  public Xdag03(Kernel kernel, XdagChannel channel) {
    this.kernel = kernel;
    this.channel = channel;
    this.blockchain = kernel.getBlockchain();
    this.syncMgr = kernel.getSyncMgr();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
    log.debug("接收到新消息 in xdag03：" + msg.getCommand());
    msgQueue.receivedMessage(msg);

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
        processSumRequest((SumRequestMessage) msg);
        break;
      case SUMS_REPLY:
        processSumReply((SumReplyMessage) msg);
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
    // 这里的ctx是最后一个handler的
    msgQueue.activate(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.debug("channel inactive:[{}] ", ctx.toString());
    this.killTimers();
    disconnect();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.debug("Xdag handling failed");
    ctx.close();
    killTimers();
    disconnect();
  }

  @Override
  public synchronized void dropConnection() {
    // logger.info("Peer {}: is a bad one, drop", channel.getNode().getAddress());
    System.out.println("Peer {" + channel.getNode().getAddress() + "}: is a bad one, drop");
    disconnect();
  }

  public void killTimers() {
    log.debug("msgqueue stop");
    msgQueue.close();
  }

  /** *********************** Message Processing * *********************** */
  protected synchronized void processNewBlock(NewBlockMessage msg) {
    Block block = msg.getBlock();
    log.debug("New block received: block.index [{}]", block.toString());
    log.debug("Block data:" + Hex.toHexString(block.getXdagBlock().getData()));
    log.debug("ttl:" + msg.getTtl());
    if (!syncMgr.validateAndAddNewBlock(new BlockWrapper(block, msg.getTtl() - 1, channel.getNode()))) {
      dropConnection();
    }
  }

  /** 区块请求响应一个区块 并开启一个线程不断发送一段时间内的区块 * */
  protected synchronized void processBlocksRequest(BlocksRequestMessage msg) {
    log.debug("Process BlocksRequest:" + msg);
    updateNetStatus(msg);
    long starttime = msg.getStarttime();
    long endtime = msg.getEndtime();
    long random = msg.getRandom();
    ListenableFuture<Integer> future =
        MoreExecutors.listeningDecorator(sendThreads)
            .submit(new SendTask(blockchain, starttime, endtime));
    futures.add(future);
    Futures.addCallback(
        future,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(Integer integer) {
            if (integer == 1) {
              sendMessage(new BlocksReplyMessage(integer, endtime, random, kernel.getNetStatus()));
            } else {
              log.debug("出现问题");
            }
            futures.remove(future);
          }

          @Override
          public void onFailure(@Nonnull Throwable throwable) {
            log.debug("发送失败");
          }
        },
        MoreExecutors.directExecutor());
    log.debug("futures size:" + futures.size());
  }

  class SendTask implements Callable<Integer> {
    private Blockchain blockchain;
    private long starttime;
    private long endtime;

    public SendTask(Blockchain blockchain, long starttime, long endtime) {
      this.blockchain = blockchain;
      this.starttime = starttime;
      this.endtime = endtime;
    }

    @Override
    public Integer call()  {
      if (blockchain == null) {
        return 1;
      }
      List<Block> blocks = blockchain.getBlockByTime(starttime, endtime);
      if (blocks == null || blocks.size() == 0) {
        log.debug("Nothing to send");
        return 1;
      }
      for (Block block : blocks) {
        sendNewBlock(block, 1);
      }
      return 1;
    }
  }

  /**
   * Reply 可以不用处理
   *
   */
  protected synchronized void processBlocksReply(BlocksReplyMessage msg) {
    log.debug("Process BlocksReply:" + msg);
    updateNetStatus(msg);
  }

  /** 将sumrequest的后8个字段填充为自己的sum 修改type类型为reply 发送 */
  protected synchronized void processSumRequest(SumRequestMessage msg) {
    updateNetStatus(msg);
    //        byte[] sum = new byte[256];
    byte[] sum =
        kernel.getBlockStore().getSimpleFileStore().loadSum(msg.getStarttime(), msg.getEndtime());
    SumReplyMessage reply =
        new SumReplyMessage(msg.getEndtime(), msg.getRandom(), kernel.getNetStatus(), sum);
    sendMessage(reply);
  }

  protected synchronized void processSumReply(SumReplyMessage msg) {
    log.debug("Process SumReply " + msg);
    updateNetStatus(msg);
    SettableFuture<SumReplyMessage> future = futureSumSublist.poll();
    future.set(msg);
    future = null;
  }

  protected synchronized void processBlockExtRequest(BlockExtRequestMessage msg) {}

  protected synchronized void processBlockRequest(BlockRequestMessage msg) {
    log.debug("Process Blockrequest:" + msg);
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
    log.debug("Send block hash " + Hex.toHexString(newBlock.getHashLow()));
    NewBlockMessage msg = new NewBlockMessage(newBlock, TTL);
    sendMessage(msg);
  }

  @Override
  public void sendGetblocks(long starttime, long endtime) {
    BlocksRequestMessage msg = new BlocksRequestMessage(starttime, endtime, kernel.getNetStatus());

    sendMessage(msg);
  }

  @Override
  public boolean isIdle() {
    return false;
  }

  @Override
  public void sendGetblock(byte[] hash) {
    BlockRequestMessage blockRequestMessage = new BlockRequestMessage(hash, kernel.getNetStatus());

    sendMessage(blockRequestMessage);
  }

  @Override
  public ListenableFuture<SumReplyMessage> sendGetsums(long starttime, long endtime) {
    log.debug("SendGetSums starttime " + starttime + " endtime " + endtime);
    if (endtime - starttime <= REQUEST_BLOCKS_MAX_TIME) {
      // 发送getblock请求
      sendGetblocks(starttime, endtime);
      return null;
    } else {
      // 依旧发送sum请求
      SumRequestMessage msg = new SumRequestMessage(starttime, endtime, kernel.getNetStatus());
      SettableFuture<SumReplyMessage> future = SettableFuture.create();
      futureSumSublist.offer(future);

      sendMessage(msg);
      return future;
    }
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
    if (sendThreads != null) {
      try {
        if (futures.size() != 0) {
          for (ListenableFuture<Integer> future : futures) {
            future.cancel(true);
          }
        }
        sendThreads.shutdown();
        sendThreads.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void activate() {
    log.debug("Xdag protocol activate");
    ////        xdagListener.trace("Xdag protocol activate");
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

  public void updateNetStatus(AbstractMessage message) {
    NetStatus remoteNetStatus = message.getNetStatus();
    log.debug("Remote netstatus:" + remoteNetStatus);
    synchronized (kernel.getNetStatus()) {
      kernel.getNetStatus().updateNetStatus(remoteNetStatus);
    }
    synchronized (kernel.getNetDBMgr()) {
      log.debug("update netdb");
      kernel.getNetDBMgr().updateNetDB(message.getNetDB());
    }
  }

  public void updateNetDB(AbstractMessage message) {
    NetDB remoteNetDB = message.getNetDB();
    log.debug("Remote netdb:" + remoteNetDB);
    synchronized (kernel.getNetDB()) {
      kernel.getNetDB().updateNetDB(remoteNetDB);
    }
  }
}
