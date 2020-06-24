package io.xdag.mine.handler;

import static io.xdag.config.Constants.BLOCK_HEAD_WORD;
import static io.xdag.net.XdagVersion.V03;
import static io.xdag.utils.BasicUtils.crc32Verify;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;


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
import io.xdag.utils.BytesUtils;

import io.xdag.utils.DateUtils;

/**
 * @Classname MinerHandShakeHandler
 * @Description 主要是处理矿工加入的一些判断 根据地址块进行判断 miner的地址
 *              握手处理器 服务端收到一个连接的时候会添加这个握手
 * @Date 2020/5/8 15:21
 * @author  by Myron
 */

public class MinerHandShakeHandler extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MinerHandShakeHandler.class);

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
        logger.debug("接受到一个地址块");
        Native.crypt_start();
        byte[] address = new byte[512];
        in.readBytes(address);


        long sectorNo = channel.getInBound().get();

        /*解密数据*/
        byte[] uncryptData = Native.dfslib_uncrypt_array(address,16,sectorNo);

        logger.debug("uncryptData [{}] ", Hex.toHexString(uncryptData));

        int crc = BytesUtils.bytesToInt(uncryptData,4,true);
        logger.debug("crc : {}",crc);
        int head = BytesUtils.bytesToInt(uncryptData,0,true);

        //清除transportheader
        System.arraycopy(BytesUtils.longToBytes(0,true),0,uncryptData,4,4);


        if (head != BLOCK_HEAD_WORD || !crc32Verify(uncryptData,crc) ) {
            logger.debug(" not a block from miner");
            ctx.channel().closeFuture();
        } else {
            //把区块头置0了
            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);

            Block addressBlock = new Block(new XdagBlock(uncryptData));
            //Todo:加入block_queue
            syncManager.validateAndAddNewBlock(new BlockWrapper(addressBlock, kernel.getConfig().getTTL(), null));

            //初始化一个 矿工也会再一次进行判断
            if (!channel.initMiner(addressBlock.getHash())) {
                logger.debug("too many connect for a miner");

                //关闭这个channel
                ctx.close();

            }

            channel.getInBound().add(16L);

            minerManager.addActivateChannel(channel);

            channel.setIsActivate(true);
            channel.setConnectTime(DateUtils.getCurrentTime());


            channel.setAccountAddressHash(addressBlock.getHash());

            ctx.pipeline().remove(this);

            channel.activateHadnler(ctx, V03);

            // TODO: 2020/5/8  这里可能还有一点小bug  如果无限加入 岂不是会无线创建了
            System.out.println("add a new miner,miner address ["+Hex.toHexString(addressBlock.getHash()) +"]");

        }


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.debug("抛出异常 :"+cause.getMessage());
        cause.printStackTrace();
        ctx.close();
        channel.onDisconnect();
    }

    /**
     * 远程主机强制关闭连接
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)  {
        try {
            Channel nettyChannel = ctx.channel();
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.READER_IDLE) {
                    nettyChannel.closeFuture();
                    if (logger.isDebugEnabled()){
                        logger.debug(nettyChannel.remoteAddress() + "---No data was received for a while ,read time out... ...");
                    }
                }else if (e.state() == IdleState.WRITER_IDLE) {
                    nettyChannel.closeFuture();
                    if (logger.isDebugEnabled()){
                        logger.debug(nettyChannel.remoteAddress() + "---No data was sent for a while.write time out... ...");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
