package io.xdag.net.handler;

import static io.xdag.config.Config.MainNet;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.utils.BasicUtils.crc32Verify;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.xdag.config.Config;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.crypto.jni.Native;
import io.xdag.net.XdagChannel;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BytesUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = false)
@Slf4j
@Data
public class XdagBlockHandler extends ByteToMessageCodec<XdagBlock> {
    private XdagChannel channel;
    private Config config;
    private MessageFactory messageFactory;

    public XdagBlockHandler(XdagChannel channel) {
        this.channel = channel;
    }

    /** 获取第i个的第n个字节 */
    public static byte getMsgcode(XdagBlock xdagblock, int n) {
        byte[] data = xdagblock.getData();
        long type = BytesUtils.bytesToLong(data, 8, true);

        return (byte) (type >> (n << 2) & 0xf);
    }

    /** T 加解密的过程outbound应该先用上一次结束后的值 发完才加 */
    @Override
    protected void encode(
            ChannelHandlerContext channelHandlerContext, XdagBlock xdagblock, ByteBuf out) {
        byte[] uncryptData = xdagblock.getData();

        byte[] encryptData = Native.dfslib_encrypt_byte_sector(uncryptData, uncryptData.length,
                channel.getNode().getStat().Outbound.get() - 3 + 1);
        out.writeBytes(encryptData);
        channel.getNode().getStat().Outbound.add();
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) {
        log.debug("XdagBlockHandler readableBytes " + in.readableBytes() + " bytes");
        if (in.readableBytes() >= XdagBlock.XDAG_BLOCK_SIZE) {
            log.trace("Decoding packet (" + in.readableBytes() + " bytes)");
            byte[] encryptData = new byte[512];
            in.readBytes(encryptData);
            byte[] uncryptData = Native.dfslib_uncrypt_byte_sector(encryptData, encryptData.length,
                    channel.getNode().getStat().Inbound.get() - 3 + 1);
            // 该通道的输入记录加一
            channel.getNode().getStat().Inbound.add();

            // TODO:处理xdagblock的传输头
            // 8b010002f91eb6eb -> ebb61ef90200018b
            long transportHeader = BytesUtils.bytesToLong(uncryptData, 0, true);
            // 转发次数
            int ttl = (int) ((transportHeader >> 8) & 0xff);
            // 数据长度 应该为512
            long dataLength = (transportHeader >> 16 & 0xffff);
            // crc校验码
            int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
            // 清除transportheader
            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);

            // 验证长度和crc校验
            if (dataLength != 512 || !crc32Verify(uncryptData, crc)) {
                log.debug(dataLength + " length");
                log.debug("receive not block verify error!");
            }

            System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);

            XdagBlock xdagBlock = new XdagBlock(uncryptData);
            byte first_field_type = getMsgcode(xdagBlock, 0);
            Message msg = null;
            // 普通区块
            XdagField.FieldType netType = MainNet ? XdagField.FieldType.XDAG_FIELD_HEAD : XDAG_FIELD_HEAD_TEST;
            if (netType.asByte() == first_field_type) {
                msg = new NewBlockMessage(xdagBlock, ttl);
            }
            // 消息区块
            else if (XdagField.FieldType.XDAG_FIELD_NONCE.asByte() == first_field_type) {
                msg = messageFactory.create(getMsgcode(xdagBlock, 1), xdagBlock.getData());
            }
            if (msg != null) {
                out.add(msg);
            } else {
                log.debug("receive unknown block first_field_type :" + first_field_type);
            }

        } else {
            log.debug("length less than " + XdagBlock.XDAG_BLOCK_SIZE + " bytes");
        }
    }
}