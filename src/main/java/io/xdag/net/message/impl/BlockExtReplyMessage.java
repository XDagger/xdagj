package io.xdag.net.message.impl;

import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;

public class BlockExtReplyMessage extends Message {

  public BlockExtReplyMessage(byte[] bytes) {
    super(bytes);
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
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
    return XdagMessageCodes.BLOCKEXT_REPLY;
  }
}
