//package io.xdag.libp2p.Handler;
//
//import com.google.common.util.concurrent.*;
//import io.libp2p.core.Connection;
//import io.libp2p.core.P2PChannel;
//import io.libp2p.core.multistream.ProtocolBinding;
//import io.libp2p.core.multistream.ProtocolDescriptor;
//import io.netty.channel.ChannelHandlerContext;
//import io.xdag.Kernel;
//import io.xdag.core.Block;
//import io.xdag.core.BlockWrapper;
//import io.xdag.core.XdagStats;
//import io.xdag.libp2p.Libp2pChannel;
//import io.xdag.libp2p.Libp2pNode;
//import io.xdag.libp2p.manager.Libp2pChannelManager;
//import io.xdag.libp2p.message.MessageQueueLib;
//import io.xdag.net.handler.MessageCodes;
//import io.xdag.net.message.AbstractMessage;
//import io.xdag.net.message.Message;
//import io.xdag.net.message.impl.*;
//import lombok.extern.slf4j.Slf4j;
//import org.jetbrains.annotations.NotNull;
//import org.spongycastle.util.Arrays;
//import org.spongycastle.util.encoders.Hex;
//
//import javax.annotation.Nonnull;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Slf4j
//public class Handler implements ProtocolBinding<Handler.Xdag05> {
//
//    Xdag05 xdag05;
//    Kernel kernel;
//    Libp2pChannel libp2pChannel;
//    XdagBlockHandler xdagBlockHandler;
//    Libp2pChannelManager libp2pChannelManager;
//
//    public Handler(Kernel kernel) {
//        this.kernel = kernel;
//        libp2pChannelManager = kernel.getLibp2pChannelManager();
//    }
//
//    public Handler(Xdag05 xdag05) {
//        this.xdag05 = xdag05;
//    }
//
//    @NotNull
//    @Override
//    public ProtocolDescriptor getProtocolDescriptor() {
//        return  new ProtocolDescriptor("xdagj");
//    }
//
//
//    @NotNull
//    @Override
//    public CompletableFuture<Xdag05> initChannel(@NotNull P2PChannel p2PChannel, @NotNull String s) {
//        log.info("initChannel");
//        final Connection connection = ((io.libp2p.core.Stream) p2PChannel).getConnection();
//        libp2pChannel = new Libp2pChannel(connection,this);
//        libp2pChannel.init();
//        xdag05 = new Xdag05(libp2pChannel);
//        libp2pChannelManager.add(libp2pChannel);
//        xdagBlockHandler = new XdagBlockHandler(libp2pChannel);
//        xdagBlockHandler.setMessageFactory(new Xdag03MessageFactory());
//        xdagBlockHandler.setMsgQueue(new MessageQueueLib(libp2pChannel));
//        if (!p2PChannel.isInitiator()) {
//            log.info("p2PChannel is not Initiator");
//        }
//        MessageCodes messageCodes = new MessageCodes();
//        p2PChannel.pushHandler(xdagBlockHandler);
//        p2PChannel.pushHandler(messageCodes);
//        p2PChannel.pushHandler(xdag05);
//
//        return xdag05.activeFuture;
//
//    }
//
//    public synchronized void dropConnection() throws InterruptedException {
//
//        xdag05.dropConnection();
//        if(libp2pChannel != null){
//            libp2pChannelManager.remove(libp2pChannel);
//        }
//    }
//
//
//
//     public static class Xdag05 extends libXdagHandler {
//         protected final CompletableFuture<Xdag05> activeFuture = new CompletableFuture<>();
//
//         public Xdag05() {
//
//             this.blockchain = kernel.getBlockchain();
//            this.syncMgr = kernel.getSyncMgr();
//        }
//         @Override
//         protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
//             switch (msg.getCommand()) {
//                 case NEW_BLOCK:
//                     processNewBlock((NewBlockMessage) msg);
//                     break;
//                 case BLOCK_REQUEST:
//                     processBlockRequest((BlockRequestMessage) msg);
//                     break;
//                 case BLOCKS_REQUEST:
//                     processBlocksRequest((BlocksRequestMessage) msg);
//                     break;
//                 case BLOCKS_REPLY:
//                     processBlocksReply((BlocksReplyMessage) msg);
//                     break;
//                 case SUMS_REQUEST:
//                     processSumsRequest((SumRequestMessage) msg);
//                     break;
//                 case SUMS_REPLY:
//                     processSumsReply((SumReplyMessage) msg);
//                     break;
//                 case BLOCKEXT_REQUEST:
//                     processBlockExtRequest((BlockExtRequestMessage) msg);
//                     break;
//                 default:
//                     break;
//             }
//         }
//
//         @Override
//         public void handlerAdded(ChannelHandlerContext ctx) {
//             msgQueue.activate(ctx);
//         }
//
//         @Override
//         public void channelInactive(ChannelHandlerContext ctx) {
//             log.debug("channelInactive:[{}] ", ctx.toString());
//             this.killTimers();
//             disconnect();
//         }
//
//         @Override
//         public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//             log.debug("exceptionCaught:[{}]", cause.getMessage(), cause);
//             ctx.close();
//             killTimers();
//             disconnect();
//         }
//
//         @Override
//         public void dropConnection() {
//             log.info("Peer {}: is a bad one, drop", channel.getNode().getHexId());
//             disconnect();
//         }
//
//         public void killTimers() {
//             log.debug("msgQueue stop");
//             msgQueue.close();
//         }
//
//         /** *********************** Message Processing * *********************** */
//         protected void processNewBlock(NewBlockMessage msg) {
//             Block block = msg.getBlock();
//             log.info("processNewBlock:{}", Hex.toHexString(block.getHashLow()));
//             BlockWrapper bw = new BlockWrapper(block, msg.getTtl() - 1, channel.getNode());
//             syncMgr.validateAndAddNewBlock(bw);
//         }
//
//         /** 区块请求响应一个区块 并开启一个线程不断发送一段时间内的区块 * */
//         protected void processBlocksRequest(BlocksRequestMessage msg) {
////        log.debug("processBlocksRequest:" + msg);
//             updateXdagStats(msg);
////        long startTime = msg.getStarttime();
////        long endTime = msg.getEndtime();
////        long random = msg.getRandom();
////
////        List<Block> blocks = blockchain.getBlockByTime(startTime, endTime);
////        for (Block block : blocks) {
////            sendNewBlock(block, 1);
////        }
////        sendMessage(new BlocksReplyMessage(startTime, endTime, random, kernel.getBlockchain().getXdagStats()));
//         }
//
//         protected void processBlocksReply(BlocksReplyMessage msg) {
////        log.debug("processBlocksReply:" + msg);
//             updateXdagStats(msg);
//             long randomSeq = msg.getRandom();
//             SettableFuture<byte[]> sf = kernel.getSync().getBlocksRequestMap().get(randomSeq);
//             if(sf != null) {
//                 sf.set(new byte[]{0});
//             }
//         }
//
//         /** 将sumRequest的后8个字段填充为自己的sum 修改type类型为reply 发送 */
//         protected void processSumsRequest(SumRequestMessage msg) {
//             log.debug("processSumsRequest:" + msg);
//             updateXdagStats(msg);
//             byte[] sums = new byte[256];
//             kernel.getBlockStore().loadSum(msg.getStarttime(), msg.getEndtime(), sums);
//             SumReplyMessage reply = new SumReplyMessage(msg.getEndtime(), msg.getRandom(), kernel.getBlockchain().getXdagStats(), sums);
//             sendMessage(reply);
//             log.debug("processSumsRequest:" + reply);
//         }
//
//         protected void processSumsReply(SumReplyMessage msg) {
////        log.debug("processSumReply:" + msg);
//             updateXdagStats(msg);
//             long randomSeq = msg.getRandom();
//             SettableFuture<byte[]> sf = kernel.getSync().getSumsRequestMap().get(randomSeq);
//             if(sf != null) {
//                 sf.set(msg.getSum());
//             }
//         }
//
//         protected void processBlockExtRequest(BlockExtRequestMessage msg) {
//         }
//
//         protected void processBlockRequest(BlockRequestMessage msg) {
//             log.debug("processBlockRequest:" + msg);
//             byte[] find = new byte[32];
//             byte[] hash = msg.getHash();
//             hash = Arrays.reverse(hash);
//             System.arraycopy(hash, 8, find, 8, 24);
//             Block block = blockchain.getBlockByHash(find, true);
//             if (block != null) {
//                 NewBlockMessage message = new NewBlockMessage(block, kernel.getConfig().getTTL());
//                 sendMessage(message);
//             }
//         }
//
//         /** *********************** Message Sending * *********************** */
//         @Override
//         public void sendNewBlock(Block newBlock, int TTL) {
//             log.debug("sendNewBlock:" + Hex.toHexString(newBlock.getHashLow()));
//             NewBlockMessage msg = new NewBlockMessage(newBlock, TTL);
//             sendMessage(msg);
//         }
//
//         @Override
//         public long sendGetBlocks(long startTime, long endTime) {
////        log.debug("sendGetBlocks:[startTime={} endTime={}]", startTime, endTime);
//             BlocksRequestMessage msg = new BlocksRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats());
//             sendMessage(msg);
//             return msg.getRandom();
//         }
//
//         @Override
//         public boolean isIdle() {
//             return false;
//         }
//
//         @Override
//         public long sendGetBlock(byte[] hash) {
//             log.debug("sendGetBlock:[{}]", Hex.toHexString(hash));
//             BlockRequestMessage msg = new BlockRequestMessage(hash, kernel.getBlockchain().getXdagStats());
//             sendMessage(msg);
//             return msg.getRandom();
//         }
//
//         @Override
//         public long sendGetSums(long startTime, long endTime) {
////        log.debug("sendGetSums:startTime=[{}],endTime=[{}]", startTime, endTime);
//             SumRequestMessage msg = new SumRequestMessage(startTime, endTime, kernel.getBlockchain().getXdagStats());
//             sendMessage(msg);
//             return msg.getRandom();
//         }
//
//         @Override
//         public void sendMessage(Message message) {
//             if (msgQueue.isRunning()) {
//                 msgQueue.sendMessage(message);
//             } else {
//                 log.debug("msgQueue is close");
//             }
//         }
//
//         protected void disconnect() {
//             msgQueue.disconnect();
//         }
//
//         @Override
//         public void activate() {
//             log.debug("Xdag protocol activate");
//             //// xdagListener.trace("Xdag protocol activate");
//         }
//
//         @Override
//         public void disableBlocks() {
//             // TODO Auto-generated method stub
//
//         }
//
//         @Override
//         public void enableBlocks() {
//             // TODO Auto-generated method stub
//
//         }
//
//         @Override
//         public void onSyncDone(boolean done) {
//             // TODO Auto-generated method stub
//
//         }
//
//         public void updateXdagStats(AbstractMessage message) {
//             XdagStats remoteXdagStats = message.getXdagStats();
//             kernel.getBlockchain().getXdagStats().update(remoteXdagStats);
//             kernel.getNetDBMgr().updateNetDB(message.getNetDB());
//         }
//
//
//
//     }
//}
