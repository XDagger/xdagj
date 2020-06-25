package io.xdag.net.message.impl;

import static io.xdag.net.message.XdagMessageCodes.BLOCKS_REPLY;

import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetStatus;
import io.xdag.net.message.XdagMessageCodes;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class BlocksReplyMessage extends AbstractMessage {
  public BlocksReplyMessage(long starttime, long endtime, long random, NetStatus netStatus) {
    super(BLOCKS_REPLY, starttime, endtime, random, netStatus);
    updateCrc();
  }

  public BlocksReplyMessage(byte[] encoded) {
    super(encoded);
  }

  @Override
  public byte[] getEncoded() {
    return encoded;
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  @Override
  public XdagMessageCodes getCommand() {
    return XdagMessageCodes.BLOCKS_REPLY;
  }

  @Override
  public String toString() {
    if (!parsed) {
      parse();
    }
    return "["
        + this.getCommand().name()
        + " starttime="
        + getStarttime()
        + " endtime="
        + getEndtime()
        + " netstatus"
        + getNetStatus();
  }
}
