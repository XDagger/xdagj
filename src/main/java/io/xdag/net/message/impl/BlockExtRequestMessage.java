package io.xdag.net.message.impl;

import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;

public class BlockExtRequestMessage extends Message {

  public BlockExtRequestMessage(byte[] bytes) {
    super(bytes);
    // TODO Auto-generated constructor stub
  }

  @Override
  public Class<?> getAnswerMessage() {
    // TODO Auto-generated method stub
    return BlocksReplyMessage.class;
  }

  @Override
  public byte[] getEncoded() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public XdagMessageCodes getCommand() {
    return XdagMessageCodes.BLOCKEXT_REQUEST;
  }
}
