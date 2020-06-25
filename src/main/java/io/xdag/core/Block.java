package io.xdag.core;

import static io.xdag.config.Config.MainNet;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_NONCE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_PUBLIC_KEY_0;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_PUBLIC_KEY_1;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_SIGN_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_SIGN_OUT;
import static io.xdag.utils.BytesUtils.bytesToBigInteger;
import static io.xdag.utils.FastByteComparisons.equalBytes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import io.xdag.crypto.ECKey;
import io.xdag.crypto.Sha256Hash;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import lombok.Data;

/** @ClassName Block @Description @Author punk @Date 2020/4/19 00:05 @Version V1.0 */
@Data
public class Block implements Cloneable {

  private static final Logger logger = LoggerFactory.getLogger(Block.class);
  public static final int MAX_LINKS = 15;

  /** 区块生成时间 区块手续费 区块字段类型* */
  private long timestamp;

  private long fee = 0;
  private long type;

  /** 连接本区块的区块地址* */
  private Address ref; // 指向引用本区块的区块 fee给该区块

  /** 区块标志* */
  public int flags = 0; // 标志区块类型 1f 主块 1c 接收 18 拒绝

  /** 区块hash* */
  private byte[] hash;
  /** 区块低192bit hash用作地址* */
  private byte[] hashLow;

  /** 区块包含的金额 cheato 用于计算balance* */
  private long amount;
  /** 区块难度* */
  private BigInteger difficulty;
  /** 第一个输出 主块见证块第一个输出为pretop 其他块为自己的上一个地址块* */
  private Address firstOutput;
  /** 区块的links 列表 输入输出* */
  private List<Address> inputs = new CopyOnWriteArrayList<>();
  /** ouput包含pretop */
  private List<Address> outputs = new CopyOnWriteArrayList<>();
  /** 指向最大难度的链接块* */
  private Address maxDifflink;
  /** 记录公钥 前缀+压缩公钥* */
  private List<ECKey> pubKeys = new CopyOnWriteArrayList<>();

  private Map<ECKey.ECDSASignature, Integer> insigs = new LinkedHashMap<>();
  private ECKey.ECDSASignature outsig;

  /** 主块的nonce记录矿工地址跟nonce* */
  private byte[] nonce;

  private XdagBlock xdagBlock;
  private boolean parsed = false;

  /** 区块是否存在于本地* */
  public boolean isSaved = false;

  private long sum;

  private byte[] encoded;

  private int tempLength;

  public Block(
      long timestamp,
      Address pretop,
      List<Address> links,
      List<Address> pendings,
      boolean mining,
      List<ECKey> keys,
      int defKeyIndex) {
    parsed = true;
    this.timestamp = timestamp;
    this.fee = 0;
    this.firstOutput = pretop;
    int lenghth = 0;

    setType(MainNet ? XDAG_FIELD_HEAD : XDAG_FIELD_HEAD_TEST, lenghth++);

    if (pretop != null) {
      setType(XDAG_FIELD_OUT, lenghth++);
    }

    if (links != null && links.size() != 0) {
      for (int i = 0; i < links.size(); i++) {
        XdagField.FieldType type = links.get(i).getType();
        setType(type, lenghth++);
        if (type == XDAG_FIELD_OUT) {
          outputs.add(links.get(i));
        } else {
          inputs.add(links.get(i));
        }
      }
    }

    if (pendings != null && pendings.size() != 0) {
      for (int i = 0; i < pendings.size(); i++) {
        setType(XDAG_FIELD_OUT, lenghth++);
        outputs.add(pendings.get(i));
      }
    }

    if (keys != null && keys.size() != 0) {
      for (int i = 0; i < keys.size(); i++) {
        byte[] keydata = keys.get(i).getPubKeybyCompress();
        boolean yBit = BytesUtils.toByte(BytesUtils.subArray(keydata, 0, 1)) == 0x03;
        XdagField.FieldType type = yBit ? XDAG_FIELD_PUBLIC_KEY_1 : XDAG_FIELD_PUBLIC_KEY_0;
        setType(type, lenghth++);
        pubKeys.add(keys.get(i));
      }
      for (int i = 0; i < keys.size(); i++) {
        if (i != defKeyIndex) {
          setType(XDAG_FIELD_SIGN_IN, lenghth++);
          setType(XDAG_FIELD_SIGN_IN, lenghth++);
        } else {
          setType(XDAG_FIELD_SIGN_OUT, lenghth++);
          setType(XDAG_FIELD_SIGN_OUT, lenghth++);
        }
      }
    }

    if (defKeyIndex < 0) {
      setType(XDAG_FIELD_SIGN_OUT, lenghth++);
      setType(XDAG_FIELD_SIGN_OUT, lenghth);
    }

    if (mining) {
      setType(XDAG_FIELD_SIGN_IN, MAX_LINKS);
    }
  }

  // 主块
  public Block(long timestamp, byte[] pretop, List<Address> pendings, boolean mining) {
    this(timestamp, new Address(pretop, XDAG_FIELD_OUT), null, pendings, mining, null, -1);
  }

  /** 从512字节读取* */
  public Block(XdagBlock xdagBlock) {
    this.xdagBlock = xdagBlock;
    parse();
  }

  /** 从rocksdb读取* */
  public Block(
      long timestamp,
      long amount,
      BigInteger diff,
      long fee,
      byte[] ref,
      byte[] maxdiffLink,
      int flags) {
    parsed = true;
    this.timestamp = timestamp;
    this.amount = amount;
    this.difficulty = diff;
    this.fee = fee;
    if (ref != null) {
      this.ref = new Address(ref, XDAG_FIELD_OUT);
    }
    if (maxdiffLink != null) {
      this.maxDifflink = new Address(maxdiffLink, XDAG_FIELD_OUT);
    }
    if (flags != 0) {
      this.flags = flags;
    }
    isSaved = true;
  }

  /** 计算区块hash* */
  public byte[] calcHash() {
    if (xdagBlock == null) {
      xdagBlock = getXdagBlock();
    }
    return Arrays.reverse(Sha256Hash.hashTwice(xdagBlock.getData()));
  }

  /** 解析512字节数据* */
  public synchronized void parse() {
    if (parsed) {
      return;
    }
    setHash(calcHash());
    byte[] header = xdagBlock.getField(0).getData();
    this.fee = BytesUtils.bytesToLong(BytesUtils.subArray(header, 24, 8), 0, true); // 最后8个字节
    this.timestamp = BytesUtils.bytesToLong(header, 16, true);
    this.type = BytesUtils.bytesToLong(header, 8, true);
    BigInteger r = BigInteger.ZERO;
    BigInteger s = BigInteger.ZERO;

    int signatureflag = 0;
    for (int i = 1; i < xdagBlock.getFields().length; i++) {
      XdagField field = xdagBlock.getField(i);
      XdagField.FieldType eachType = field.getType();
      if (eachType == XDAG_FIELD_OUT) {
        if (i == 1) {
          firstOutput = new Address(field);
        } else {
          outputs.add(new Address(field));
        }
      } else if (eachType == XDAG_FIELD_IN) {
        inputs.add(new Address(xdagBlock.getField(i)));
      } else if (eachType == XDAG_FIELD_SIGN_IN || eachType == XDAG_FIELD_SIGN_OUT) {
        // 最后一个字段如果是signIn的类型则作为nonce
        if (i == MAX_LINKS && eachType == XDAG_FIELD_SIGN_IN) {
          this.nonce = BytesUtils.bigIntegerToBytes(r, 32);
          continue;
        }
        if ((++signatureflag) % 2 == 0) {
          s = bytesToBigInteger(xdagBlock.getField(i).getData());
          ECKey.ECDSASignature tmp = new ECKey.ECDSASignature(r, s);
          if (eachType == XDAG_FIELD_SIGN_IN) {
            insigs.put(tmp, i);
          } else {
            outsig = tmp;
          }
          r = BigInteger.ZERO;
          s = BigInteger.ZERO;
        }
        r = bytesToBigInteger(xdagBlock.getField(i).getData());
      } else if (eachType == XDAG_FIELD_PUBLIC_KEY_0 || eachType == XDAG_FIELD_PUBLIC_KEY_1) {
        byte[] key = xdagBlock.getField(i).getData();
        boolean yBit = eachType == XDAG_FIELD_PUBLIC_KEY_1;
        ECPoint point = ECKey.decompressKey(bytesToBigInteger(key), yBit);
        pubKeys.add(ECKey.fromPublicOnly(point));
      } else if (eachType == XDAG_FIELD_NONCE) {
        // do nothing
      } else {
        logger.debug("no match information");
      }
    }
    parsed = true;
  }

  public byte[] toBytes() {
    SimpleEncoder encoder = new SimpleEncoder();
    encoder.write(getEncodedBody());

    for (ECKey.ECDSASignature sig : insigs.keySet()) {
      encoder.writeSignature(BytesUtils.subArray(sig.toByteArray(), 0, 64));
    }
    if (outsig != null) {
      encoder.writeSignature(BytesUtils.subArray(outsig.toByteArray(), 0, 64));
    }
    int length = encoder.getWriteFieldIndex();
    tempLength = length;
    int res = 0;
    if (length == 16) {
      return encoder.toBytes();
    }
    res = 15 - length;
    for (int i = 0; i < res; i++) {
      encoder.writeField(new byte[32]);
    }
    if (nonce != null) {
      encoder.writeField(nonce);
    } else {
      encoder.writeField(new byte[32]);
    }
    return encoder.toBytes();
  }
  // without signature
  private byte[] getEncodedBody() {
    if (encoded != null) {
      return encoded;
    }
    SimpleEncoder encoder = new SimpleEncoder();
    encoder.writeField(getEncodedHeader());
    if (firstOutput != null) {
      encoder.writeField(Arrays.reverse(firstOutput.getHashLow()));
    }
    List<Address> all = new ArrayList<>();
    all.addAll(inputs);
    all.addAll(outputs);
    for (Address link : all) {
      encoder.writeField(Arrays.reverse(link.getData()));
    }
    for (ECKey ecKey : pubKeys) {
      byte[] key = BytesUtils.subArray(ecKey.getPubKeybyCompress(), 1, 32);
      encoder.writeField(key);
    }
    encoded = encoder.toBytes();
    return encoded;
  }

  private byte[] getEncodedHeader() {
    byte[] fee = BytesUtils.longToBytes(getFee(), true);
    byte[] time = BytesUtils.longToBytes(getTimestamp(), true);
    byte[] type = BytesUtils.longToBytes(getType(), true);
    byte[] transport = new byte[8];
    return BytesUtils.merge(transport, type, time, fee);
  }

  public XdagBlock getXdagBlock() {
    if (xdagBlock != null) {
      return xdagBlock;
    }
    xdagBlock = new XdagBlock(toBytes());
    return xdagBlock;
  }

  public void signIn(ECKey ecKey) {
    sign(ecKey, XDAG_FIELD_SIGN_IN);
  }

  public void signOut(ECKey ecKey) {
    sign(ecKey, XDAG_FIELD_SIGN_OUT);
  }

  private void sign(ECKey ecKey, XdagField.FieldType type) {
    byte[] encoded = toBytes();
    byte[] digest = BytesUtils.merge(encoded, ecKey.getPubKeybyCompress());
    byte[] hash = Sha256Hash.hashTwice(digest);
    ECKey.ECDSASignature signature = ecKey.sign(hash);
    if (type == XDAG_FIELD_SIGN_OUT) {
      outsig = signature;
    } else {
      insigs.put(signature, tempLength + 1);
    }
  }

  // 只匹配输入签名 并返回有用的key
  public List<ECKey> verifiedKeys() {
    List<ECKey> keys = getPubKeys();
    for (ECKey key : keys) {
      logger.debug("myron 获取到key[{}]", Hex.toHexString(key.getPubKeybyCompress()));
    }
    List<ECKey> res = new ArrayList<>();
    byte[] digest = null;
    byte[] hash = null;
    for (ECKey.ECDSASignature sig : this.getInsigs().keySet()) {
      digest = getSubRawData(this.getInsigs().get(sig) - 2);
      for (ECKey ecKey : keys) {
        hash = Sha256Hash.hashTwice(BytesUtils.merge(digest, ecKey.getPubKeybyCompress()));
        if (ecKey.verify(hash, sig)) {
          res.add(ecKey);
        }
      }
    }
    digest = getSubRawData(getOutsigIndex() - 2);
    for (ECKey ecKey : keys) {
      hash = Sha256Hash.hashTwice(BytesUtils.merge(digest, ecKey.getPubKeybyCompress()));
      logger.debug("验证的块的hash【{}】", Hex.toHexString(this.getHash()));
      logger.debug(Hex.toHexString(hash) + ":hash");
      logger.debug(outsig + ":outsig");
      logger.debug(ecKey + ":eckey");

      if (ecKey.verify(hash, this.getOutsig())) {
        res.add(ecKey);
      }
    }
    return res;
  }

  // 获取输出签名在字段的索引
  public int getOutsigIndex() {
    parse();
    int i = 1;
    long temp = type;
    while ((temp & 0xf) != 5) {
      temp = temp >> 4;
      i++;
    }
    return i;
  }

  public byte[] getHash() {
    if (hash == null) {
      hash = calcHash();
    }
    return hash;
  }

  public byte[] getHashLow() {
    if (hashLow == null) {
      hashLow = new byte[32];
      System.arraycopy(getHash(), 8, hashLow, 8, 24);
    }
    return hashLow;
  }

  public List<Address> getOutputs() {
    parse();
    return outputs;
  }

  public List<Address> getInputs() {
    parse();
    return inputs;
  }

  public List<ECKey> getPubKeys() {
    parse();
    return pubKeys;
  }

  public byte[] getNonce() {
    parse();
    return nonce;
  }

  public ECKey.ECDSASignature getOutsig() {
    parse();
    return outsig;
  }

  public Map<ECKey.ECDSASignature, Integer> getInsigs() {
    parse();
    return insigs;
  }

  @Override
  public String toString() {
    return "Block info:[Hash:"
        + Hex.toHexString(getHashLow())
        + "][Time:"
        + Long.toHexString(getTimestamp())
        + "]";
  }

  /** 获取区块sums* */
  public long getSum() {
    if (sum != 0) {
      return sum;
    }
    if (xdagBlock == null) {
      xdagBlock = getXdagBlock();
    }
    for (int i = 0; i < 16; i++) {
      sum += xdagBlock.getField(i).getSum();
    }
    return sum;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    Block otherB = (Block) o;
    return equalBytes(this.getHashLow(), otherB.getHashLow());
  }

  @Override
  public int hashCode() {
    return new ByteArrayWrapper(this.getHashLow()).hashCode();
  }

  public long getTimestamp() {
    parse();
    return timestamp;
  }

  public Address getFirstOutput() {
    parse();
    return firstOutput;
  }

  public long getType() {
    parse();
    return type;
  }

  public long getFee() {
    parse();
    return fee;
  }

  /** 根据length获取前length个字段的数据 主要用于签名* */
  public byte[] getSubRawData(int length) {
    byte[] data = getXdagBlock().getData();

    byte[] res = new byte[512];
    System.arraycopy(data, 0, res, 0, (length + 1) * 32);

    return res;
  }

  private void setType(XdagField.FieldType type, int n) {
    long typeByte = type.asByte();
    this.type |= typeByte << (n << 2);
  }

  public List<Address> getLinks() {
    parse();
    List<Address> links = new ArrayList<>();
    if (getFirstOutput() != null) {
      links.add(getFirstOutput());
    }
    links.addAll(getInputs());
    links.addAll(getOutputs());
    return links;
  }

  @Override
  public Object clone() {
    Block ano = null;
    try {
      ano = (Block) super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }
    return ano;
  }
}
