package io.xdag.net.message.impl;

import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetDB;
import io.xdag.net.message.NetStatus;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;

import java.math.BigInteger;

import static io.xdag.net.message.XdagMessageCodes.SUMS_REPLY;

public class SumReplyMessage extends AbstractMessage {

  byte[] sums;

  public SumReplyMessage(long endtime, long random, NetStatus netStatus, byte[] sums) {
    super(SUMS_REPLY, 1, endtime, random, netStatus);
    this.sums = sums;
    System.arraycopy(BytesUtils.longToBytes(random, true), 0, encoded, 32, 8);
    System.arraycopy(sums, 0, encoded, 256, 256);
    updateCrc();
  }

  public SumReplyMessage(byte[] encoded) {
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
    return XdagMessageCodes.SUMS_REPLY;
  }

  @Override
  public String toString() {
    if (!parsed) {
      parse();
    }
    return "["
        + this.getCommand().name()
        + " starttime="
        + starttime
        + " endtime="
        + this.endtime
        + " netstatus="
        + netStatus;
  }

  public byte[] getSum() {
    parse();
    return sums;
  }

  @Override
  public void parse() {
    if (parsed) {
      return;
    }
    starttime = BytesUtils.bytesToLong(encoded, 16, true);
    endtime = BytesUtils.bytesToLong(encoded, 24, true);
    random = BytesUtils.bytesToLong(encoded, 32, true);
    BigInteger maxdifficulty = BytesUtils.bytesToBigInteger(encoded, 80, true);
    long totalnblocks = BytesUtils.bytesToLong(encoded, 104, true);
    long totalnmains = BytesUtils.bytesToLong(encoded, 120, true);
    int totalnhosts = BytesUtils.bytesToInt(encoded, 132, true);
    long maintime = BytesUtils.bytesToLong(encoded, 136, true);
    netStatus = new NetStatus(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

    // test netdb
    int length = 6;
    // 80 是sizeof(xdag_stats)
    byte[] netdb = new byte[length * 32 - 80];
    System.arraycopy(encoded, 144, netdb, 0, length * 32 - 80);
    netDB = new NetDB(netdb);

    sums = new byte[256];
    System.arraycopy(encoded, 256, sums, 0, 256);
    parsed = true;
  }
}
