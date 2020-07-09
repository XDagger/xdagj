package io.xdag.core;

import static io.xdag.utils.BytesUtils.bytesToBigInteger;

import io.xdag.utils.BytesUtils;
import java.math.BigInteger;
import lombok.Data;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

@Data
public class Address {
  /** 放入字段的数据 正常顺序 */
  protected byte[] data;
  /** 输入or输出or不带amount的输出 */
  protected XdagField.FieldType type;
  /** 转账金额（输入or输出） */
  protected BigInteger amount;
  /** 地址hash低192bit */
  protected byte[] hashLow = new byte[32];

  protected boolean parsed = false;

  /** in mem */
  private Block block;

  public Address(XdagField field) {
    this.type = field.getType();
    this.data = Arrays.reverse(field.getData());
    parse();
  }

  /** 只用于ref 跟 maxdifflink */
  public Address(byte[] hashLow) {
    this.hashLow = hashLow;
    this.amount = BigInteger.valueOf(0);
    parsed = true;
  }
  /** 只用于ref 跟 maxdifflink */
  public Address(Block block) {
    this.block = block;
    this.hashLow = block.getHashLow();
    this.amount = BigInteger.valueOf(0);
    parsed = true;
  }

  public Address(byte[] blockHashlow, XdagField.FieldType type) {
    this.type = type;
    this.data = blockHashlow;
    parse();
  }

  public Address(byte[] blockHashLow, XdagField.FieldType type, long amount) {
    this.type = type;
    this.hashLow = blockHashLow;
    this.amount = BigInteger.valueOf(amount);
    parsed = true;
  }

  public byte[] getData() {
    if (data == null) {
      data = new byte[32];
      System.arraycopy(hashLow, 8, data, 8, 24);
      System.arraycopy(BytesUtils.bigIntegerToBytes(amount, 8), 0, data, 0, 8);
    }
    return data;
  }

  public void parse() {
    if (parsed) {
      return;
    } else {
      System.arraycopy(data, 8, hashLow, 8, 24);
      byte[] amountbyte = new byte[8];
      System.arraycopy(data, 0, amountbyte, 0, 8);
      amount = bytesToBigInteger(amountbyte);
      parsed = true;
    }
  }

  public BigInteger getAmount() {
    parse();
    return this.amount;
  }

  public byte[] getHashLow() {
    parse();
    return this.hashLow;
  }

  @Override
  public String toString() {
    return "Block Hash[" + Hex.toHexString(hashLow) + "]";
  }
}
