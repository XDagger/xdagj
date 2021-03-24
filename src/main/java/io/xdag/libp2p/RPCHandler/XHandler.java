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
package io.xdag.libp2p.RPCHandler;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.libp2p.Libp2pChannel;
import io.xdag.libp2p.message.MessageQueueLib;
import io.xdag.net.XdagVersion;
import io.xdag.net.handler.Xdag;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.net.message.impl.SumReplyMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@EqualsAndHashCode(callSuper = false)
@Data
@Slf4j
public abstract class XHandler  extends SimpleChannelInboundHandler<Message> implements Xdag {
    protected Kernel kernel;
    protected Blockchain blockchain;
    protected XdagVersion version = XdagVersion.V03;
    protected Libp2pChannel channel;
    protected MessageQueueLib msgQueue;
    protected Block bestKnownBlock;
    protected BigInteger totalDifficulty;
    protected SyncManager syncMgr;
    protected SettableFuture<List<Block>> futureBlocks;
    protected SettableFuture<SumReplyMessage> futureSum;
    protected Queue<SettableFuture<SumReplyMessage>> futureSumSublist = new LinkedList<>();
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        System.out.println("sscc");
        if (XdagMessageCodes.inRange(msg.getCommand().asByte(), version)) {
            log.trace("XdagHandler invoke: [{}]", msg.getCommand());
        }
    }

}
