package io.xdag.mine.handler;

import static io.xdag.config.Config.MainNet;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.net.handler.XdagBlockHandler.getMsgcode;
import static io.xdag.net.message.XdagMessageCodes.NEW_BALANCE;
import static io.xdag.net.message.XdagMessageCodes.TASK_SHARE;
import static io.xdag.utils.BasicUtils.crc32Verify;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.crypto.jni.Native;
import io.xdag.mine.MinerChannel;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.impl.NewBlockMessage;
import io.xdag.utils.BytesUtils;
import java.io.IOException;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinerMessageHandler extends ByteToMessageCodec<byte[]> {

  public static final Logger logger = LoggerFactory.getLogger(MinerMessageHandler.class);

  private MinerChannel channel;

  private MessageFactory messageFactory;

  /** 每一个字段的长度 */
  private int DATA_SIZE = 32;

  public MinerMessageHandler(MinerChannel channel) {
    this.channel = channel;
  }

  public void setMessageFactory(MessageFactory messageFactory) {
    this.messageFactory = messageFactory;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, byte[] bytes, ByteBuf out) {

    int len = bytes.length;
    long sectorNo = channel.getOutBound().get();

    if (len == DATA_SIZE) {
      logger.debug("发送一个字段的消息");
      BytesUtils.arrayReverse(bytes);
      out.writeBytes(Native.dfslib_encrypt_array(bytes, 1, sectorNo));
      channel.getOutBound().add();

    } else if (len == 2 * DATA_SIZE) {
      logger.debug("发送一个任务消息，消息内容为[{}]", Hex.encodeHexString(bytes));
      out.writeBytes(Native.dfslib_encrypt_array(bytes, 2, sectorNo));
      channel.getOutBound().add(2);

    } else if (len == 16 * DATA_SIZE) {
      out.writeBytes(Native.dfslib_encrypt_array(bytes, 16, sectorNo));
      channel.getOutBound().add(16);
    } else {
      logger.debug("没有该长度字段类型的消息");
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    // 处理接收到的消息

    Message msg = null;

    long sectorNo = channel.getInBound().get();

    int len = in.readableBytes();
    // 接收到的是任务share
    if (len == DATA_SIZE) {
      logger.debug("Received a message from the miner,msg len == 32");
      byte[] encryptData = new byte[DATA_SIZE];
      in.readBytes(encryptData);
      byte[] uncryptData = Native.dfslib_uncrypt_array(encryptData, 1, sectorNo);

      BytesUtils.arrayReverse(uncryptData);

      if (channel.isServer()) {
        // 如果是服务端 那么收到的一个字节的消息只能是task——share
        msg = messageFactory.create(TASK_SHARE.asByte(), uncryptData);
      } else {
        msg = messageFactory.create(NEW_BALANCE.asByte(), uncryptData);
      }

      channel.getInBound().add();

      // 两个字段 说明收到的是一个任务字段 只有可能是矿工收到新的任务
    } else if (len == 2 * DATA_SIZE) {

      logger.debug("Received a message from the miner,msg len == 64");
      byte[] encryptData = new byte[64];
      in.readBytes(encryptData);
      byte[] uncryptData = Native.dfslib_uncrypt_array(encryptData, 2, sectorNo);

      msg = messageFactory.create(TASK_SHARE.asByte(), uncryptData);
      channel.getInBound().add(2);

      // 收到512个字节的消息 那就说明是收到一个区块 矿工发上来的一笔交易
    } else if (len == 16 * DATA_SIZE) {

      byte[] encryptData = new byte[512];
      in.readBytes(encryptData);

      byte[] uncryptData = Native.dfslib_uncrypt_array(encryptData, 16, sectorNo);
      long transportHeader = BytesUtils.bytesToLong(uncryptData, 0, true);

      int ttl = (int) ((transportHeader >> 8) & 0xff);
      int crc = BytesUtils.bytesToInt(uncryptData, 4, true);
      System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 4, 4);

      // 验证长度和crc校验
      if (!crc32Verify(uncryptData, crc)) {
        logger.debug("receive not block");
      }else {

        System.arraycopy(BytesUtils.longToBytes(0, true), 0, uncryptData, 0, 8);

        XdagBlock xdagBlock = new XdagBlock(uncryptData);
        byte first_field_type = getMsgcode(xdagBlock, 0);

        XdagField.FieldType netType = MainNet ? XdagField.FieldType.XDAG_FIELD_HEAD : XDAG_FIELD_HEAD_TEST;
        if (netType.asByte() == first_field_type) {
          msg = new NewBlockMessage(xdagBlock, ttl);
        }

        channel.getInBound().add(16);
      }
    } else {
      logger.debug("There is no type message of corresponding length, please check......");
      throw new IllegalArgumentException(
          "There is no type message of corresponding length, please check");
    }

    if (msg != null) {
      out.add(msg);
    } else {
      throw new IllegalArgumentException("receive unknown block, msg len = [{}]");
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (cause instanceof IOException) {
      logger.debug("远程主机关闭了一个连接");
      ctx.channel().closeFuture();

    } else {
      cause.printStackTrace();
    }

    channel.onDisconnect();
  }
}
