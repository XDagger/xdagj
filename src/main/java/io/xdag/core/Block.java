/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.core;

import io.xdag.config.Config;
import io.xdag.utils.HashUtils;
import io.xdag.crypto.Keys;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.xdag.core.XdagField.FieldType.*;

@Slf4j
@Getter
@Setter
public class Block implements Cloneable {
    public static final int MAX_LINKS = 15;
    private BlockInfo info;
    private long transportHeader;

    /** 区块的links 列表 输入输出* */
    private List<Address> inputs = new CopyOnWriteArrayList<>();

    /** ouput包含pretop */
    private List<Address> outputs = new CopyOnWriteArrayList<>();

    /** 记录公钥 前缀+压缩公钥* */
    private List<SECP256K1.PublicKey> pubKeys = new CopyOnWriteArrayList<>();
    private Map<SECP256K1.Signature, Integer> insigs = new LinkedHashMap<>();
    private SECP256K1.Signature outsig;

    /** 主块的nonce记录矿工地址跟nonce* */
    private Bytes32 nonce;
    private XdagBlock xdagBlock;
    private boolean parsed;
    private boolean isOurs;
    private byte[] encoded;
    private int tempLength;

    /** 区块是否存在于本地* */
    public boolean isSaved;

    @Getter
    @Setter
    private boolean pretopCandidate;
    @Getter
    @Setter
    private BigInteger pretopCandidateDiff;

    public Block(
            Config config,
            long timestamp,
            List<Address> links,
            List<Address> pendings,
            boolean mining,
            List<SECP256K1.KeyPair> keys,
            String remark,
            int defKeyIndex) {
        parsed = true;
        info = new BlockInfo();
        this.info.setTimestamp(timestamp);
        this.info.setFee(0);
        int lenghth = 0;

        setType(config.getXdagFieldHeader(), lenghth++);

        if (CollectionUtils.isNotEmpty(links)) {
            for (Address link : links) {
                XdagField.FieldType type = link.getType();
                setType(type, lenghth++);
                if (type == XDAG_FIELD_OUT) {
                    outputs.add(link);
                } else {
                    inputs.add(link);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(pendings)) {
            for (Address pending : pendings) {
                setType(XDAG_FIELD_OUT, lenghth++);
                outputs.add(pending);
            }
        }

        if(StringUtils.isAsciiPrintable(remark)) {
            setType(XDAG_FIELD_REMARK, lenghth++);
            byte[] data = remark.getBytes();
            byte[] safeRemark = new byte[32];
            Arrays.fill(safeRemark, (byte)0);
            System.arraycopy(data, 0, safeRemark, 0, Math.min(data.length, 32));
            this.info.setRemark(safeRemark);
        }

        if (CollectionUtils.isNotEmpty(keys)) {
            for (SECP256K1.KeyPair key : keys) {
                SECP256K1.PublicKey pub = SECP256K1.PublicKey.fromSecretKey(key.secretKey());
                byte[] keydata = pub.asEcPoint().getEncoded(true);
                boolean yBit = BytesUtils.toByte(BytesUtils.subArray(keydata, 0, 1)) == 0x03;
                XdagField.FieldType type = yBit ? XDAG_FIELD_PUBLIC_KEY_1 : XDAG_FIELD_PUBLIC_KEY_0;
                setType(type, lenghth++);
                pubKeys.add(pub);
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

    /** main block */
    public Block(Config config,long timestamp,
                 List<Address> pendings,
                 boolean mining)
    {
        this(config, timestamp, null, pendings, mining, null, null, -1);
    }

    /** 从512字节读取* */
    public Block(XdagBlock xdagBlock) {
        this.xdagBlock = xdagBlock;
        this.info = new BlockInfo();
        parse();
    }

    public Block(BlockInfo blockInfo) {
        this.info = blockInfo;
        this.isSaved = true;
        this.parsed = true;

    }

    /** 计算区块hash* */
    private byte[] calcHash() {
        if (xdagBlock == null) {
            xdagBlock = getXdagBlock();
        }
        return Arrays.reverse(HashUtils.hashTwice(Bytes.wrap(xdagBlock.getData())).toArray());
    }

    /** 重计算 避免矿工挖矿发送share时直接更新 hash **/
    public Bytes32 recalcHash() {
        xdagBlock = new XdagBlock(toBytes());
        return Bytes32.wrap(HashUtils.hashTwice(Bytes.wrap(xdagBlock.getData())).reverse());
    }

    /** 解析512字节数据* */
    public void parse() {
        if (this.parsed) {
            return;
        }
        if(this.info == null) {
            this.info = new BlockInfo();
        }
        this.info.setHash(calcHash());
        Bytes32 header = Bytes32.wrap(xdagBlock.getField(0).getData());
//        this.transportHeader = BytesUtils.bytesToLong(header, 0, true);
        this.transportHeader = header.getLong(0, ByteOrder.LITTLE_ENDIAN);
//        this.info.type = BytesUtils.bytesToLong(header, 8, true);
        this.info.type = header.getLong(8, ByteOrder.LITTLE_ENDIAN);
//        this.info.setTimestamp(BytesUtils.bytesToLong(header, 16, true));
        this.info.setTimestamp(header.getLong(16,ByteOrder.LITTLE_ENDIAN));
//        this.info.setFee(BytesUtils.bytesToLong(BytesUtils.subArray(header, 24, 8), 0, true));
        this.info.setFee(header.getLong(24, ByteOrder.LITTLE_ENDIAN));
        for (int i = 1; i < XdagBlock.XDAG_BLOCK_FIELDS; i++) {
            XdagField field = xdagBlock.getField(i);
            if(field == null) {
                throw new IllegalArgumentException("xdagBlock field:" + i + " is null");
            }
            switch (field.getType()) {
                case XDAG_FIELD_IN:
                    inputs.add(new Address(xdagBlock.getField(i)));
                    break;
                case XDAG_FIELD_OUT:
                    outputs.add(new Address(field));
                    break;
                case XDAG_FIELD_REMARK:
                    this.info.setRemark(field.getData().toArray());
                    break;
                case XDAG_FIELD_SIGN_IN:
                case XDAG_FIELD_SIGN_OUT:
                    BigInteger r;
                    BigInteger s;
                    int j, signo_s = -1;
                    XdagField ixf;
                    for(j = i; j < XdagBlock.XDAG_BLOCK_FIELDS; ++j) {
                        ixf = xdagBlock.getField(j);
                        if(ixf.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal() || ixf.getType() == XDAG_FIELD_SIGN_OUT) {
                            if(j > i && signo_s < 0 && ixf.getType().ordinal() == xdagBlock.getField(i).getType().ordinal()) {
                                signo_s = j;
                                r = xdagBlock.getField(i).getData().toUnsignedBigInteger();
                                s = xdagBlock.getField(signo_s).getData().toUnsignedBigInteger();
                                SECP256K1.Signature sig = SECP256K1.Signature.create((byte)0, r, s);
                                if (ixf.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal()) {
                                    insigs.put(sig, i);
                                } else {
                                    outsig = sig;
                                }
                            }
                        }
                    }
                    if (i == MAX_LINKS && field.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal()) {
                        this.nonce = Bytes32.wrap(xdagBlock.getField(i).getData());
                        continue;
                    }
                    break;
                case XDAG_FIELD_PUBLIC_KEY_0:
                case XDAG_FIELD_PUBLIC_KEY_1:
                    Bytes noPrefixPubkey = xdagBlock.getField(i).getData();
                    boolean yBit = (field.getType().ordinal() == XDAG_FIELD_PUBLIC_KEY_1.ordinal());
                    ECPoint point = Keys.decompressKey(noPrefixPubkey.toUnsignedBigInteger(), yBit);
                    Bytes encodePub = Bytes.wrap(point.getEncoded(false));
                    SECP256K1.PublicKey pub = SECP256K1.PublicKey.fromBytes(encodePub.slice(1, 64));
                    pubKeys.add(pub);
                    break;
                default:
//                    log.debug("no match xdagBlock field type:" + field.getType());
            }
        }
        this.parsed = true;
    }

    public byte[] toBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.write(getEncodedBody());

        for (SECP256K1.Signature sig : insigs.keySet()) {
            encoder.writeSignature(BytesUtils.subArray(sig.bytes().toArray(), 0, 64));
        }
        if (outsig != null) {
            encoder.writeSignature(BytesUtils.subArray(outsig.bytes().toArray(), 0, 64));
        }
        int length = encoder.getWriteFieldIndex();
        tempLength = length;
        int res;
        if (length == 16) {
            return encoder.toBytes();
        }
        res = 15 - length;
        for (int i = 0; i < res; i++) {
            encoder.writeField(new byte[32]);
        }
        Bytes32 nonceNotNull = Objects.requireNonNullElse(nonce, Bytes32.ZERO);
        encoder.writeField(nonceNotNull.toArray());
        return encoder.toBytes();
    }

    /** without signature */
    private byte[] getEncodedBody() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeField(getEncodedHeader());
        List<Address> all = new ArrayList<>();
        all.addAll(inputs);
        all.addAll(outputs);
        for (Address link : all) {
            encoder.writeField(link.getData().reverse().toArray());
        }
        if(info.getRemark() != null) {
            encoder.write(info.getRemark());
        }
        for (SECP256K1.PublicKey pub : pubKeys) {
            byte[] pubkeyBytes = pub.asEcPoint().getEncoded(true);
            // remove 03/02 header
            byte[] key = Bytes.wrap(pubkeyBytes).slice(1, 32).toArray();
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

    public void signIn(SECP256K1.KeyPair ecKey) {
        sign(ecKey, XDAG_FIELD_SIGN_IN);
    }

    public void signOut(SECP256K1.KeyPair ecKey) {
        sign(ecKey, XDAG_FIELD_SIGN_OUT);
    }

    private void sign(SECP256K1.KeyPair key, XdagField.FieldType type) {
        byte[] encoded = toBytes();
//        log.debug("sign encoded:{}", Hex.toHexString(encoded));
        byte[] pubkeyBytes = key.publicKey().asEcPoint().getEncoded(true);
        // remove 03/02 prefix
        byte[] noprefixPubkey = Bytes.wrap(pubkeyBytes).slice(1, 32).toArray();

        byte[] digest = BytesUtils.merge(encoded, noprefixPubkey);
//        log.debug("sign digest:{}", Hex.toHexString(digest));
        Bytes32 hash = HashUtils.hashTwice(Bytes.wrap(digest));
//        log.debug("sign hash:{}", Hex.toHexString(hash));
        SECP256K1.Signature signature = SECP256K1.sign(hash, key);
        if (type == XDAG_FIELD_SIGN_OUT) {
            outsig = signature;
        } else {
            insigs.put(signature, tempLength);
        }
    }

    /** 只匹配输入签名 并返回有用的key */
    public List<SECP256K1.PublicKey> verifiedKeys() {
        List<SECP256K1.PublicKey> keys = getPubKeys();
        List<SECP256K1.PublicKey> res = new ArrayList<>();
        Bytes subdata;
        Bytes32 hash;
        for (SECP256K1.Signature sig : this.getInsigs().keySet()) {
            subdata = getSubRawData(this.getInsigs().get(sig) - 1);
            for (SECP256K1.PublicKey pub : keys) {
                byte[] pubkeyBytes = pub.asEcPoint().getEncoded(true);
                byte[] noPrefixPubkey = Bytes.wrap(pubkeyBytes).slice(1, 32).toArray();
                hash = HashUtils.hashTwice(Bytes.concatenate(subdata, Bytes.wrap(noPrefixPubkey)));
                if(SECP256K1.verify(hash, sig, pub)) {
                    res.add(pub);
                }
            }
        }

        subdata = getSubRawData(getOutsigIndex() - 2);
        for (SECP256K1.PublicKey pub : keys) {
            byte[] pubkeyBytes = pub.asEcPoint().getEncoded(true);
            byte[] noPrefixPubkey = Bytes.wrap(pubkeyBytes).slice(1, 32).toArray();
            hash = HashUtils.hashTwice(Bytes.concatenate(subdata, Bytes.wrap(noPrefixPubkey)));

            if(SECP256K1.verify(hash, this.getOutsig(), pub)) {
                res.add(pub);
            }
        }
        return res;
    }

    /** 取输出签名在字段的索引 */
    public int getOutsigIndex() {
        int i = 1;
        long temp = this.info.type;
        while (i < XdagBlock.XDAG_BLOCK_FIELDS && (temp & 0xf) != 5) {
            temp = temp >> 4;
            i++;
        }
        return i;
    }

    public Bytes32 getHash() {
        if (this.info.getHash() == null) {
            this.info.setHash(calcHash());
        }
        return Bytes32.wrap(this.info.getHash());
    }

    public MutableBytes32 getHashLow() {
        if (info.getHashlow() == null) {
            MutableBytes32 hashLow = MutableBytes32.create();
//            System.arraycopy(getHash(), 8, hashLow, 8, 24);
            hashLow.set(8, getHash().slice(8, 24));
            info.setHashlow(hashLow.toArray());
        }
        return MutableBytes32.wrap(info.getHashlow());
    }

    public List<Address> getOutputs() {
        return outputs;
    }

    public List<Address> getInputs() {
        return inputs;
    }

    public List<SECP256K1.PublicKey> getPubKeys() {
        return pubKeys;
    }

    public Bytes32 getNonce() {
        return nonce;
    }

    public SECP256K1.Signature getOutsig() {
        return outsig == null?null: Keys.toCanonicalised(outsig);
    }

    public Map<SECP256K1.Signature, Integer> getInsigs() {
        return insigs;
    }

    @Override
    public String toString() {
        return String.format("Block info:[Hash:{%s}][Time:{%s}]", getHashLow().toHexString(), Long.toHexString(getTimestamp()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Block block = (Block) o;
        return Objects.equals(getHashLow(), block.getHashLow());
    }

    @Override
    public int hashCode() {
        return new ByteArrayWrapper(this.getHashLow().toArray()).hashCode();
    }

    public long getTimestamp() {
        return this.info.getTimestamp();
    }

    public long getType() {
        return this.info.type;
    }

    public long getFee() {
        return this.info.getFee();
    }

    /** 根据length获取前length个字段的数据 主要用于签名* */
    public MutableBytes getSubRawData(int length) {
        Bytes data = getXdagBlock().getData();
//        byte[] res = new byte[512];
        MutableBytes res = MutableBytes.create(512);
//        System.arraycopy(data, 0, res, 0, (length + 1) * 32);
        res.set(0, data.slice(0, (length + 1) * 32));
        for (int i = length+1; i < 16; i++) {
//            long type = BytesUtils.bytesToLong(data, 8, true);
            long type = data.getLong(8, ByteOrder.LITTLE_ENDIAN);
            byte typeB = (byte) (type >> (i << 2) & 0xf);
            if (XDAG_FIELD_SIGN_IN.asByte() == typeB || XDAG_FIELD_SIGN_OUT.asByte() == typeB) {
                continue;
            }
//            System.arraycopy(data, (i) * 32, res, (i) * 32, 32);
            res.set((i) * 32, data.slice((i) * 32, 32));
        }
        return res;
    }

    private void setType(XdagField.FieldType type, int n) {
        long typeByte = type.asByte();
        this.info.type |= typeByte << (n << 2);
    }

    public List<Address> getLinks() {
        List<Address> links = new ArrayList<>();
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
