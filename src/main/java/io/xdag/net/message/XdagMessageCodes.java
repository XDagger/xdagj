package io.xdag.net.message;

import io.xdag.net.XdagVersion;

import java.util.HashMap;
import java.util.Map;

import static io.xdag.net.XdagVersion.V03;

/**
 * xdag block.h
 *
 * <p>enum xdag_message_type { XDAG_MESSAGE_BLOCKS_REQUEST, XDAG_MESSAGE_BLOCKS_REPLY,
 * XDAG_MESSAGE_SUMS_REQUEST, XDAG_MESSAGE_SUMS_REPLY, XDAG_MESSAGE_BLOCKEXT_REQUEST,
 * XDAG_MESSAGE_BLOCKEXT_REPLY, XDAG_MESSAGE_BLOCK_REQUEST, };
 */
public enum XdagMessageCodes {
  // add new block type here
  NEW_BLOCK(-1),
  BLOCKS_REQUEST(0x00),
  BLOCKS_REPLY(0x01),
  SUMS_REQUEST(0x02),
  SUMS_REPLY(0x03),
  BLOCKEXT_REQUEST(0x04),
  BLOCKEXT_REPLY(0x05),
  BLOCK_REQUEST(0x06),

  Receive_Block(0x07), // add new block type here
  TASK_SHARE(0x08),
  NEW_TASK(0x09),
  NEW_BALANCE(0x0A);

  private final int cmd;

  private static final Map<XdagVersion, Map<Integer, XdagMessageCodes>> intToTypeMap =
      new HashMap<>();
  private static final Map<XdagVersion, XdagMessageCodes[]> versionToValuesMap = new HashMap<>();

  static {
    versionToValuesMap.put(
        V03,
        new XdagMessageCodes[] {
          NEW_BLOCK,
          BLOCKS_REQUEST,
          BLOCKS_REPLY,
          SUMS_REQUEST,
          SUMS_REPLY,
          BLOCKEXT_REQUEST,
          BLOCKEXT_REPLY,
          BLOCK_REQUEST,
          Receive_Block,
          TASK_SHARE,
          NEW_TASK,
          NEW_BALANCE
        });

    for (XdagVersion v : XdagVersion.values()) {
      Map<Integer, XdagMessageCodes> map = new HashMap<>();
      intToTypeMap.put(v, map);
      for (XdagMessageCodes code : values(v)) {
        map.put(code.cmd, code);
      }
    }
  }

  public static XdagMessageCodes[] values(XdagVersion v) {
    return versionToValuesMap.get(v);
  }

  private XdagMessageCodes(int cmd) {
    this.cmd = cmd;
  }

  public static XdagMessageCodes fromByte(byte i, XdagVersion v) {
    Map<Integer, XdagMessageCodes> map = intToTypeMap.get(v);
    return map.get((int) i);
  }

  public static boolean inRange(byte code, XdagVersion v) {
    XdagMessageCodes[] codes = values(v);
    return code >= codes[0].asByte() && code <= codes[codes.length - 1].asByte();
  }

  public byte asByte() {
    return (byte) (cmd);
  }
}
