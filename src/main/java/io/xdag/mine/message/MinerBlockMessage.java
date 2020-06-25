package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.NEW_BLOCK;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;

public class MinerBlockMessage extends Message {

  private Block block;

  public MinerBlockMessage(byte[] bytes) {
    super(bytes);
    block = new Block(new XdagBlock(bytes));
  }

  @Override
  public byte[] getEncoded() {
    return block.getEncoded();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  @Override
  public XdagMessageCodes getCommand() {
    return NEW_BLOCK;
  }

  @Override
  public String toString() {
    return null;
  }
}
