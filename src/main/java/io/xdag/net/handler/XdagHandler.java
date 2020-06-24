package io.xdag.net.handler;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.common.util.concurrent.SettableFuture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageQueue;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.net.message.impl.SumReplyMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = false)
@Data
@Slf4j
public abstract class XdagHandler extends SimpleChannelInboundHandler<Message> implements Xdag {
	protected Kernel kernel;

	protected Blockchain blockchain;

	protected XdagVersion version = XdagVersion.V03;

	protected XdagChannel channel;

	protected MessageQueue msgQueue;

	protected Block bestKnownBlock;

	protected BigInteger totalDifficulty;

	protected SyncManager syncMgr;

	protected SettableFuture<List<Block>> futureBlocks;
	protected SettableFuture<SumReplyMessage> futureSum;
	protected Queue<SettableFuture<SumReplyMessage>> futureSumSublist = new LinkedList<>();

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
		if (XdagMessageCodes.inRange(msg.getCommand().asByte(), version)) {
			log.trace("XdagHandler invoke: [{}]", msg.getCommand());
		}
		msgQueue.receivedMessage(msg);
	}

}
