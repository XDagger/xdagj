package io.xdag.mine.handler;

import static io.xdag.config.Constants.BLOCK_HEAD_WORD;
import static io.xdag.net.XdagVersion.V03;
import static io.xdag.utils.BasicUtils.crc32Verify;

import java.io.IOException;
import java.util.List;

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
import io.xdag.core.XdagBlock;
import io.xdag.crypto.jni.Native;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.manager.MinerManager;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FormatDateUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerHandShakeHandler extends ByteToMessageDecoder {

    private MinerChannel channel;
    private Kernel kernel;

    private MinerManager minerManager;

    private SyncManager syncManager;

    public MinerHandShakeHandler(MinerChannel channel, Kernel kernel) {
        this.channel = channel;
        this.kernel = kernel;
        minerManager = kernel.getMinerManager();
        syncManager = kernel.getSyncMgr();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        log.debug("Receive a address block");
        Native.crypt_start();
        byte[] address = new byte[512];
        in.readBytes(address);

        long sectorNo = channel.getInBound().get();

        /* 解密数据 */
        byte[] uncryptData = Native.dfslib_uncrypt_array(address, 16, sectorNo);

        int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
        int head = BytesUtils.bytesToInt(uncryptData, 0, true);

        // 清除transportheader
        System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);

        if (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData, crc)) {
            log.debug(" not a block from miner");
            ctx.channel().closeFuture();
        } else {
            // 把区块头置0了
            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);

            Block addressBlock = new Block(new XdagBlock(uncryptData));
            // Todo:加入block_queue
            syncManager.validateAndAddNewBlock(
                    new BlockWrapper(addressBlock, kernel.getConfig().getTTL(), null));

            if (!channel.initMiner(addressBlock.getHash())) {
                log.debug("too many connect for a miner");

                ctx.close();
            }

            channel.getInBound().add(16L);

            minerManager.addActivateChannel(channel);

            channel.setIsActivate(true);
            channel.setConnectTime(FormatDateUtils.getCurrentTime());

            channel.setAccountAddressHash(addressBlock.getHash());

            ctx.pipeline().remove(this);

            channel.activateHadnler(ctx, V03);

            // TODO: 2020/5/8 这里可能还有一点小bug 如果无限加入 岂不是会无线创建了
            log.info("add a new miner,miner address [" + BasicUtils.hash2Address(addressBlock.getHash()) + "]");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            log.debug("远程主机关闭了一个连接");
            ctx.channel().closeFuture();

        } else {
            cause.printStackTrace();
        }

        channel.onDisconnect();
    }

    /** 远程主机强制关闭连接 */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        try {
            Channel nettyChannel = ctx.channel();
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
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
            e.printStackTrace();
        }
    }
}
