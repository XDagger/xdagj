//package io.xdag.libp2p.Handler;
//
//import com.google.common.util.concurrent.SettableFuture;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.xdag.Kernel;
//import io.xdag.consensus.SyncManager;
//import io.xdag.core.Block;
//import io.xdag.core.Blockchain;
//import io.xdag.libp2p.Libp2pChannel;
//import io.xdag.libp2p.message.MessageQueueLib;
//import io.xdag.net.XdagVersion;
//import io.xdag.net.handler.Xdag;
//import io.xdag.net.message.Message;
//import io.xdag.net.message.XdagMessageCodes;
//import io.xdag.net.message.impl.SumReplyMessage;
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//import lombok.extern.slf4j.Slf4j;
//
//import java.math.BigInteger;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Queue;
//
//@EqualsAndHashCode(callSuper = false)
//@Data
//@Slf4j
//public abstract class libXdagHandler  extends SimpleChannelInboundHandler<Message> implements Xdag {
//    protected Kernel kernel;
//    protected Blockchain blockchain;
//    protected XdagVersion version = XdagVersion.V03;
//    protected Libp2pChannel channel;
//    protected MessageQueueLib msgQueue;
//    protected Block bestKnownBlock;
//    protected BigInteger totalDifficulty;
//    protected SyncManager syncMgr;
//    protected SettableFuture<List<Block>> futureBlocks;
//    protected SettableFuture<SumReplyMessage> futureSum;
//    protected Queue<SettableFuture<SumReplyMessage>> futureSumSublist = new LinkedList<>();
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
//        if (XdagMessageCodes.inRange(msg.getCommand().asByte(), version)) {
//            log.trace("XdagHandler invoke: [{}]", msg.getCommand());
//        }
//    }
//
//}
