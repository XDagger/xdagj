package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.NEW_BALANCE;

import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;

public class NewBalanceMessage extends Message {

  private XdagField xdagField;

  public NewBalanceMessage(byte[] encoded) {
    super(encoded);
    this.xdagField = new XdagField(encoded);
  }

  @Override
  public byte[] getEncoded() {
    return xdagField.getData();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  @Override
  public XdagMessageCodes getCommand() {
    return NEW_BALANCE;
  }

  @Override
  public String toString() {

    return null;
  }
}
