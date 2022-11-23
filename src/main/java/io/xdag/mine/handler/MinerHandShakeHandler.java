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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.*;
import io.xdag.crypto.jni.Native;
import io.xdag.db.AddressStore;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.utils.ByteArrayToByte32;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.PubkeyAddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.xdag.config.Constants.BLOCK_HEAD_WORD;
import static io.xdag.net.XdagVersion.V03;
import static io.xdag.utils.BasicUtils.crc32Verify;

@Slf4j
public class MinerHandShakeHandler extends ByteToMessageDecoder {

    private final MinerChannel channel;
    private final Kernel kernel;
    private final MinerManager minerManager;
    private final AddressStore addressStore;
    private final SyncManager syncManager;
    public static final int MESSAGE_SIZE = 20;

    public MinerHandShakeHandler(MinerChannel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        minerManager = kernel.getMinerManager();
        addressStore = kernel.getAddressStore();
        syncManager = kernel.getSyncMgr();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= MESSAGE_SIZE) {
            log.debug("Receive a address from ip&port:{}",channel.getInetAddress().toString());
            byte[] message = new byte[MESSAGE_SIZE];
            in.readBytes(message);
//            long sectorNo = channel.getInBound().get();

            /* decrypt data */
//            byte[] uncryptData = Native.dfslib_uncrypt_array(message, 1, sectorNo);
//            int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
//            int head = BytesUtils.bytesToInt(uncryptData, 0, true);
//
//            // 清除transportheader
//            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
//            System.out.println(Hex.toHexString(uncryptData));
//            if (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData, crc)) {
//                System.out.println(head != BLOCK_HEAD_WORD);
//                byte[] address = ByteArrayToByte32.byte32ToArray(Bytes32.wrap(message).mutableCopy());
//                System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);
//                Block addressBlock = new Block(new XdagBlock(uncryptData));

                // TODO:
                checkProtocol(ctx,message);

                if (!initMiner(ByteArrayToByte32.arrayToByte32(message))) {
                    log.debug("too many connect for the miner: {},ip&port:{}",
                            PubkeyAddressUtils.toBase58(channel.getAccountAddressHashByte()),channel.getInetAddress().toString());
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
                channel.setAccountAddressHash(ByteArrayToByte32.arrayToByte32(message));
                channel.setAccountAddressHashByte(message);
                channel.activateHandler(ctx, V03);
                ctx.pipeline().remove(this);
                // TODO: 2020/5/8 There may be a bug here. If you join infinitely, won't it be created wirelessly?
                log.debug("add a new miner from ip&port:{},miner's address: [" + PubkeyAddressUtils.toBase58(channel.getAccountAddressHashByte()) + "]",channel.getInetAddress().toString());
        } else {
            log.debug("length less than " + MESSAGE_SIZE + " bytes");
        }
    }

    public void checkProtocol(ChannelHandlerContext ctx, byte[] address) {
//        if (addressBlock.getXdagBlock().getField(addressBlock.getOutsigIndex()).getData().isZero()) {
//            // pseudo block
//            log.debug("Pseudo block, addressBlockHashLow: {},ip&port:{}",addressBlock.getHashLow(),channel.getInetAddress().toString());
//
//        } else {
            boolean importResult = addressStore.addressIsExist(address);

            //If it is a new address block
            if (!importResult) {
                addressStore.addAddress(address);
                log.info("XDAG:new wallet connect. New address: {} with channel: {} connect.",
                        PubkeyAddressUtils.toBase58(address), channel.getInetAddress().toString());
            } else {
                log.info("XDAG:old wallet connect. Address: {} with channel {} connect.",
                        PubkeyAddressUtils.toBase58(address), channel.getInetAddress().toString());
            }
//        }

    }

//    public boolean isDataIllegal(byte[] uncryptData) {
//        int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
//        int head = BytesUtils.bytesToInt(uncryptData, 0, true);
//        // clean transport header
//        System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
//        return (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData, crc));
//
//    }

    public boolean initMiner(Bytes32 hash) {
        return channel.initMiner(hash);
    }

//    public ImportResult tryToConnect(Block addressBlock) {
//        return syncManager
//                .validateAndAddNewBlock(new BlockWrapper(addressBlock, kernel.getConfig().getNodeSpec().getTTL()));
//    }

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
