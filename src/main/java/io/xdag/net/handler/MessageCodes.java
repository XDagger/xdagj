package io.xdag.net.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.xdag.core.XdagBlock;
import io.xdag.net.XdagChannel;
import io.xdag.net.message.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Data
@Slf4j
public class MessageCodes extends MessageToMessageCodec<Message, Message> {

  private XdagChannel channel;

  public MessageCodes(XdagChannel channel) {
    this.channel = channel;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
    log.debug("接收到消息：" + msg.getCommand());
    out.add(msg);
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
    XdagBlock xdagblock = convertMessage(msg);
    out.add(xdagblock);
  }

  public static XdagBlock convertMessage(Message message) {
    XdagBlock block = new XdagBlock(message.getEncoded());
    return block;
  }
}
