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

import static io.xdag.config.Constants.BLOCK_HEAD_WORD;
import static io.xdag.net.XdagVersion.V03;
import static io.xdag.utils.BasicUtils.crc32Verify;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.xdag.Kernel;
import io.xdag.consensus.SyncManager;
import io.xdag.core.Block;
import io.xdag.core.BlockWrapper;
import io.xdag.core.ImportResult;
import io.xdag.core.XdagBlock;
import io.xdag.crypto.jni.Native;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;

@Slf4j
public class MinerHandShakeHandler extends ByteToMessageDecoder {

    private final MinerChannel channel;
    private final Kernel kernel;
    private final MinerManager minerManager;
    private final SyncManager syncManager;

    public MinerHandShakeHandler(MinerChannel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        minerManager = kernel.getMinerManager();
        syncManager = kernel.getSyncMgr();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= XdagBlock.XDAG_BLOCK_SIZE) {
            log.debug("Receive a address block");
            Native.crypt_start();
            byte[] address = new byte[512];
            in.readBytes(address);

            long sectorNo = channel.getInBound().get();

            /* decrypt data */
            byte[] uncryptData = Native.dfslib_uncrypt_array(address, 16, sectorNo);
//            int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
//            int head = BytesUtils.bytesToInt(uncryptData, 0, true);
//
//            // 清除transportheader
//            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
//            System.out.println(Hex.toHexString(uncryptData));
//            if (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData, crc)) {
//                System.out.println(head != BLOCK_HEAD_WORD);

            if (isDataIllegal(uncryptData.clone())) {
                log.debug(" not a block from miner: {}",channel.getAddressHash());
                ctx.channel().closeFuture();
            } else {
                System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);
                Block addressBlock = new Block(new XdagBlock(uncryptData));

                // TODO:
                checkProtocol(ctx,addressBlock);

//                ImportResult importResult = tryToConnect(addressBlock);
//
//                if (importResult == ImportResult.ERROR) {
//                    log.debug("ErrorInfo:{}", importResult.getErrorInfo());
//                    ctx.close();
//                    return;
//                }
//                //If it is a new address block
//                if (importResult != ImportResult.EXIST) {
//                    log.info("XDAG:new wallet connect. New Address {} with channel {} connect.",
//                            BasicUtils.hash2Address(addressBlock.getHash()), channel.getInetAddress().toString());
//                } else {
//                    log.info("XDAG:old wallet connect. Address {} with channel {} connect.",
//                            BasicUtils.hash2Address(addressBlock.getHash()), channel.getInetAddress().toString());
//                }

                if (!initMiner(addressBlock.getHash())) {
                    log.debug("too many connect for the miner: {}",
                            channel.getAddressHash());
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
                channel.getInBound().add(16L);
                minerManager.addActivateChannel(channel);
                channel.setIsActivate(true);
                channel.setConnectTime(new Date(System.currentTimeMillis()));
                channel.setAccountAddressHash(addressBlock.getHash());
                channel.activateHandler(ctx, V03);
                ctx.pipeline().remove(this);
                // TODO: 2020/5/8 There may be a bug here. If you join infinitely, won't it be created wirelessly?
                log.debug("add a new miner,miner's address: [" + channel.getAddressHash() + "]");
            }
        } else {
            log.debug("length less than " + XdagBlock.XDAG_BLOCK_SIZE + " bytes");
        }
    }

    public void checkProtocol(ChannelHandlerContext ctx,Block addressBlock) {
        if (addressBlock.getXdagBlock().getField(addressBlock.getOutsigIndex()).getData().isZero()) {
            // pseudo block
            log.debug("Pseudo block, addressBlockHashLow: {}",addressBlock.getHashLow());

        } else {
            ImportResult importResult = tryToConnect(addressBlock);

            if (importResult == ImportResult.ERROR) {
                log.debug("Block from address:{} type error ",
                        channel.getAddressHash());
                ctx.close();
                return;
            }
            //If it is a new address block
            if (importResult != ImportResult.EXIST) {
                log.info("XDAG:new wallet connect. New address: {} with channel: {} connect.",
                        channel.getAddressHash(), channel.getInetAddress().toString());
            } else {
                log.info("XDAG:old wallet connect. Address: {} with channel {} connect.",
                        channel.getAddressHash(), channel.getInetAddress().toString());
            }
        }

    }

    public boolean isDataIllegal(byte[] uncryptData) {
        int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
        int head = BytesUtils.bytesToInt(uncryptData, 0, true);
        // clean transport header
        System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);
        return (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData, crc));

    }

    public boolean initMiner(Bytes32 hash) {
        return channel.initMiner(hash);
    }

    public ImportResult tryToConnect(Block addressBlock) {
        return syncManager
                .validateAndAddNewBlock(new BlockWrapper(addressBlock, kernel.getConfig().getNodeSpec().getTTL()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("The miner of the remote host is: {} closed a connection.",
                    channel.getAddressHash());
            ctx.channel().closeFuture();
        } else {
            cause.printStackTrace();
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
