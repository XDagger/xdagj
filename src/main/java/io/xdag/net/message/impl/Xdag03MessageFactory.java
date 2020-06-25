package io.xdag.net.message.impl;

import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.XdagMessageCodes;

import static io.xdag.net.XdagVersion.V03;

public class Xdag03MessageFactory implements MessageFactory {
  @Override
  public Message create(byte code, byte[] encoded) {
    XdagMessageCodes receivedCommand = XdagMessageCodes.fromByte(code, V03);

    switch (receivedCommand) {
      case BLOCKS_REQUEST:
        return new BlocksRequestMessage(encoded);
      case BLOCKS_REPLY:
        return new BlocksReplyMessage(encoded);
      case SUMS_REQUEST:
        return new SumRequestMessage(encoded);
      case SUMS_REPLY:
        return new SumReplyMessage(encoded);
      case BLOCKEXT_REQUEST:
        return new BlockExtRequestMessage(encoded);
      case BLOCKEXT_REPLY:
        return new BlockExtReplyMessage(encoded);
      case BLOCK_REQUEST:
        return new BlockRequestMessage(encoded);
      default:
        throw new IllegalArgumentException("No such message code" + code);
    }
  }
}
