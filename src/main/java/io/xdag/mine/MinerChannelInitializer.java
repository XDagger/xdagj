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

package io.xdag.mine;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.xdag.Kernel;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerChannelInitializer extends ChannelInitializer<Channel> {

    private final Kernel kernel;
    private final boolean isServer;

    public MinerChannelInitializer(Kernel kernel, boolean isServer) {
        this.kernel = kernel;
        this.isServer = isServer;
    }

    @Override
    protected void initChannel(Channel ch) {
        AtomicInteger channelsAccount = kernel.getChannelsAccount();
        int channelLimit = kernel.getConfig().getPoolSpec().getGlobalMinerChannelLimit();
        if (channelsAccount.get() >= channelLimit) {
            ch.close();
            log.warn("Pool Miner Channel Limit {}, Too Many Channels In This Pool.", channelLimit);
            return;
        }
        InetSocketAddress channelAddress = isServer
                ? (InetSocketAddress)ch.remoteAddress()
                : new InetSocketAddress(
                        kernel.getConfig().getPoolSpec().getPoolIp(), kernel.getConfig().getPoolSpec().getPoolPort());
        MinerChannel minerChannel = new MinerChannel(kernel, isServer);
        minerChannel.init(ch.pipeline(), channelAddress);
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
        ch.config().setOption(ChannelOption.TCP_NODELAY, true);
        ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
        ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
    }
}
