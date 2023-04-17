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

import static io.xdag.net.XdagVersion.V03;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.xdag.Kernel;
import io.xdag.crypto.Base58;
import io.xdag.db.AddressStore;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.utils.BytesUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.xdag.utils.WalletUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;

@Slf4j
public class MinerHandShakeHandler extends ByteToMessageDecoder {

    private final MinerChannel channel;
    private final Kernel kernel;
    private final MinerManager minerManager;
    private final AddressStore addressStore;
    public static final int MESSAGE_SIZE = 24;

    public MinerHandShakeHandler(MinerChannel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        minerManager = kernel.getMinerManager();
        addressStore = kernel.getAddressStore();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= MESSAGE_SIZE) {
            log.debug("Receive a address from ip&port:{}",ctx.channel().remoteAddress());
            byte[] message = new byte[MESSAGE_SIZE];
            in.readBytes(message);
            if(!Base58.checkBytes24(message)){
                log.warn("Address hash is invalid");
                ctx.close();
                return;
            }

            byte[] addressHash = Arrays.copyOfRange(message,0,20);
            checkProtocol(ctx,addressHash);
            if (!initMiner(BytesUtils.arrayToByte32(addressHash))) {
                log.debug("too many connect for the miner: {},ip&port:{}",
                        WalletUtils.toBase58(channel.getAccountAddressHashByte()),channel.getInetAddress().toString());
                ctx.close();
                return;
            }
            AtomicInteger channelsAccount = kernel.getChannelsAccount();
            if (channelsAccount.get() >= kernel.getConfig().getPoolSpec().getGlobalMinerChannelLimit()) {
                ctx.close();
                log.warn("Too many channels in this pool");
                return;
            }

            kernel.getChannelsAccount().getAndIncrement();
            channel.getInBound().add(1L);
            minerManager.addActivateChannel(channel);
            channel.setIsActivate(true);
            channel.setConnectTime(new Date(System.currentTimeMillis()));
            channel.setAccountAddressHash(BytesUtils.arrayToByte32(addressHash));
            channel.setAccountAddressHashByte(addressHash);
            channel.activateHandler(ctx, V03);
            ctx.pipeline().remove(this);
            // TODO: 2020/5/8 There may be a bug here. If you join infinitely, won't it be created wirelessly?
            log.debug("add a new miner from ip&port:{},miner's address: [" + WalletUtils.toBase58(channel.getAccountAddressHashByte()) + "]",channel.getInetAddress());
        } else {
            log.debug("length less than " + MESSAGE_SIZE + " bytes");
        }
    }

    public void checkProtocol(ChannelHandlerContext ctx, byte[] address) {
        boolean importResult = addressStore.addressIsExist(address);
        if (!importResult) {
            addressStore.addAddress(address);
            log.info("XDAG:new miner connect. New address: {} with channel: {} connect.",
                    WalletUtils.toBase58(address), channel.getInetAddress());
        } else {
            log.info("XDAG:old miner connect. Address: {} with channel {} connect.",
                    WalletUtils.toBase58(address), channel.getInetAddress());
        }
    }

    public boolean initMiner(Bytes32 hash) {
        return channel.initMiner(hash);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("The miner of the remote host is: {} closed ip&port:{} connection.",
                    channel.getAddressHash(),channel.getInetAddress().toString());
            ctx.channel().closeFuture();
        } else {
            log.error(cause.getMessage(), cause);
        }
        channel.onDisconnect();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        try {
            Channel nettyChannel = ctx.channel();
            if (evt instanceof IdleStateEvent e) {
                if (e.state() == IdleState.READER_IDLE) {
                    nettyChannel.closeFuture();
                    if (log.isDebugEnabled()) {
                        log.debug(nettyChannel.remoteAddress()
                                + "---No data was received for a while ,read time out... ...");
                    }
                } else if (e.state() == IdleState.WRITER_IDLE) {
                    nettyChannel.closeFuture();
                    if (log.isDebugEnabled()) {
                        log.debug(
                                nettyChannel.remoteAddress() + "---No data was sent for a while.write time out... ...");
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
