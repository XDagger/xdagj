package io.xdag.net.message.impl;

import static io.xdag.config.Constants.DNET_PKT_XDAG;

import java.util.zip.CRC32;

import org.spongycastle.util.encoders.Hex;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;

public class NewBlockMessage extends Message {

  private XdagBlock xdagBlock;
  private Block block;
  private int ttl;

  // 不处理crc
  public NewBlockMessage(byte[] bytes) {
    super(bytes);
  }

  // 处理crc 创建新的用于发送Block的message
  public NewBlockMessage(Block block, int ttl) {
    this.block = block;
    this.ttl = ttl;
    this.parsed = true;
    encode();
  }

  // 不处理crc
  public NewBlockMessage(XdagBlock xdagBlock, int ttl) {
    super(xdagBlock.getData());
    this.xdagBlock = xdagBlock;
    this.ttl = ttl;
  }

  public Block getBlock() {
    parse();
    return block;
  }

  private synchronized void parse() {
    if (parsed) {
      return;
    }
    block = new Block(xdagBlock);
    parsed = true;
  }

  private void encode() {
    this.encoded = this.block.getXdagBlock().getData().clone();
    long transportheader = (ttl << 8) | DNET_PKT_XDAG | (512 << 16);
    System.arraycopy(BytesUtils.longToBytes(transportheader, true), 0, this.encoded, 0, 8);
    updateCrc();
  }

  public void updateCrc() {
    CRC32 crc32 = new CRC32();
    crc32.update(encoded, 0, 512);
    System.arraycopy(BytesUtils.intToBytes((int) crc32.getValue(), true), 0, encoded, 4, 4);
    // add by myron

  }

  public int getTtl() {
    return ttl;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
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
    return XdagMessageCodes.NEW_BLOCK;
  }

  @Override
  public String toString() {
    return "NewBlock Message:" + Hex.toHexString(encoded);
  }
}
