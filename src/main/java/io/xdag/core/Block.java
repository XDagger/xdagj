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

import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PublicKey;
import io.xdag.crypto.keys.Signature;
import io.xdag.crypto.keys.Signer;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.SimpleEncoder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.xdag.core.XdagField.FieldType.*;

@Slf4j
@Getter
@Setter
public class Block implements Cloneable {

    public static final int MAX_LINKS = 15;
    /**
     * Whether the block exists locally
     */
    public boolean isSaved;
    private Address coinBase;
    private BlockInfo info;
    private long transportHeader;
    /**
     * List of block links (inputs and outputs)
     */
    private List<Address> inputs = new CopyOnWriteArrayList<>();

    private TxAddress txNonceField;
    /**
     * Outputs including pretop
     */
    private List<Address> outputs = new CopyOnWriteArrayList<>();
    /**
     * Record public keys (prefix + compressed public key)
     */
    private List<PublicKey> pubKeys = new CopyOnWriteArrayList<>();
    private Map<Signature, Integer> insigs = new LinkedHashMap<>();
    private Signature outsig;
    /**
     * Main block nonce records miner address and nonce
     */
    private Bytes32 nonce;
    private XdagBlock xdagBlock;
    private boolean parsed;
    private boolean isOurs;
    private byte[] encoded;
    private int tempLength;
    private boolean pretopCandidate;
    private BigInteger pretopCandidateDiff;

    public Block(
            Config config,
            long timestamp,
            List<Address> links,
            List<Address> pendings,
            boolean mining,
            List<ECKeyPair> keys,
            String remark,
            int defKeyIndex,
            XAmount fee,
            UInt64 txNonce) {
        parsed = true;
        info = new BlockInfo();
        this.info.setTimestamp(timestamp);
        this.info.setFee(fee);
        int lenghth = 0;

        setType(config.getXdagFieldHeader(), lenghth++);

        if (txNonce != null) {
            txNonceField = new TxAddress(txNonce);
            setType(XDAG_FIELD_TRANSACTION_NONCE, lenghth++);
        }

        if (CollectionUtils.isNotEmpty(links)) {
            for (Address link : links) {
                XdagField.FieldType type = link.getType();
                setType(type, lenghth++);
                if (type == XDAG_FIELD_OUT || type == XDAG_FIELD_OUTPUT) {
                    outputs.add(link);
                } else if (type == XDAG_FIELD_IN || type == XDAG_FIELD_INPUT) {
                    inputs.add(link);
                } else if (type == XDAG_FIELD_COINBASE) {
                    this.coinBase = link;
                    outputs.add(link);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(pendings)) {
            for (Address pending : pendings) {
                XdagField.FieldType type = pending.getType();
                setType(type, lenghth++);
                if (type == XDAG_FIELD_OUT || type == XDAG_FIELD_OUTPUT) {
                    outputs.add(pending);
                } else if (type == XDAG_FIELD_IN || type == XDAG_FIELD_INPUT) {
                    inputs.add(pending);
                } else if (type == XDAG_FIELD_COINBASE) {
                    this.coinBase = pending;
                    outputs.add(pending);
                }
            }
        }

        if (StringUtils.isAsciiPrintable(remark)) {
            setType(XDAG_FIELD_REMARK, lenghth++);
            byte[] data = remark.getBytes(StandardCharsets.UTF_8);
            byte[] safeRemark = new byte[32];
            Arrays.fill(safeRemark, (byte) 0);
            System.arraycopy(data, 0, safeRemark, 0, Math.min(data.length, 32));
            this.info.setRemark(safeRemark);
        }

        if (CollectionUtils.isNotEmpty(keys)) {
            for (ECKeyPair key : keys) {
                PublicKey publicKey = key.getPublicKey();
                byte[] keydata = publicKey.toBytes().toArray();
                //byte[] keydata = Sign.publicKeyBytesFromPrivate(key.getPrivateKey().getEncodedBytes().toUnsignedBigInteger(), true); //poor performance
//                byte[] keydata = key.getCompressPubKeyBytes(); //good performance
                boolean yBit = BytesUtils.toByte(BytesUtils.subArray(keydata, 0, 1)) == 0x03;
                XdagField.FieldType type = yBit ? XDAG_FIELD_PUBLIC_KEY_1 : XDAG_FIELD_PUBLIC_KEY_0;
                setType(type, lenghth++);
                pubKeys.add(key.getPublicKey());
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

    /**
     * main block
     */
    public Block(Config config, long timestamp,
                 List<Address> pendings,
                 boolean mining) {
        this(config, timestamp, null, pendings, mining, null, null, -1, XAmount.ZERO, null);
    }

    /**
     * Read from raw block of 512 bytes
     */
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

    /**
     * Calculate block hash
     */
    private byte[] calcHash() {
        if (xdagBlock == null) {
            xdagBlock = getXdagBlock();
        }
        return Bytes32.wrap(HashUtils.doubleSha256(Bytes.wrap(xdagBlock.getData())).reverse()).toArray();
    }

    /**
     * Recalculate to avoid directly updating hash when miner sends share
     */
    public Bytes32 recalcHash() {
        xdagBlock = new XdagBlock(toBytes());
        return Bytes32.wrap(HashUtils.doubleSha256(Bytes.wrap(xdagBlock.getData())).reverse());
    }

    /**
     * Parse 512 bytes data
     */
    public void parse() {
        if (this.parsed) {
            return;
        }
        if (this.info == null) {
            this.info = new BlockInfo();
        }
        this.info.setHash(calcHash());
        Bytes32 header = Bytes32.wrap(xdagBlock.getField(0).getData());
        this.transportHeader = header.getLong(0, ByteOrder.LITTLE_ENDIAN);
        this.info.type = header.getLong(8, ByteOrder.LITTLE_ENDIAN);
        this.info.setTimestamp(header.getLong(16, ByteOrder.LITTLE_ENDIAN));
        this.info.setFee(XAmount.of(header.getLong(24, ByteOrder.LITTLE_ENDIAN), XUnit.NANO_XDAG));
        for (int i = 1; i < XdagBlock.XDAG_BLOCK_FIELDS; i++) {
            XdagField field = xdagBlock.getField(i);
            if (field == null) {
                throw new IllegalArgumentException("xdagBlock field:" + i + " is null");
            }
            switch (field.getType()) {
                case XDAG_FIELD_TRANSACTION_NONCE -> txNonceField = new TxAddress(field);
                case XDAG_FIELD_IN -> inputs.add(new Address(field, false));
                case XDAG_FIELD_INPUT -> inputs.add(new Address(field, true));
                case XDAG_FIELD_OUT -> outputs.add(new Address(field, false));
                case XDAG_FIELD_OUTPUT -> outputs.add(new Address(field, true));
                case XDAG_FIELD_REMARK -> this.info.setRemark(field.getData().toArray());
                case XDAG_FIELD_COINBASE -> {
                    this.coinBase = new Address(field, true);
                    outputs.add(new Address(field, true));
                }
                case XDAG_FIELD_SIGN_IN, XDAG_FIELD_SIGN_OUT -> {
                    BigInteger r;
                    BigInteger s;
                    int j, signo_s = -1;
                    XdagField ixf;
                    for (j = i; j < XdagBlock.XDAG_BLOCK_FIELDS; ++j) {
                        ixf = xdagBlock.getField(j);
                        if (ixf.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal()
                                || ixf.getType() == XDAG_FIELD_SIGN_OUT) {
                            if (j > i && signo_s < 0 && ixf.getType().ordinal() == xdagBlock.getField(i).getType()
                                    .ordinal()) {
                                signo_s = j;
                                r = xdagBlock.getField(i).getData().toUnsignedBigInteger();
                                s = xdagBlock.getField(signo_s).getData().toUnsignedBigInteger();

                                // r and s are 0, the signature is illegal, or it is a pseudo block sent by the miner
                                if (r.compareTo(BigInteger.ZERO) == 0 && s.compareTo(BigInteger.ZERO) == 0) {
                                    r = BigInteger.ONE;
                                    s = BigInteger.ONE;
                                }

                                Signature tmp = Signature.create(r, s, (byte) 0);
                                if (ixf.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal()) {
                                    insigs.put(tmp, i);
                                } else {
                                    outsig = tmp;
                                }
                            }
                        }
                    }
                    if (i == MAX_LINKS && field.getType().ordinal() == XDAG_FIELD_SIGN_IN.ordinal()) {
                        this.nonce = Bytes32.wrap(xdagBlock.getField(i).getData());
                    }
                }
                case XDAG_FIELD_PUBLIC_KEY_0, XDAG_FIELD_PUBLIC_KEY_1 -> {
                    Bytes key = xdagBlock.getField(i).getData();
                    boolean yBit = (field.getType().ordinal() == XDAG_FIELD_PUBLIC_KEY_1.ordinal());
                    PublicKey publicKey = PublicKey.fromXCoordinate(key, yBit);
                    //TODO
                    //FixME
                    //ECPoint point = Sign.decompressKey(key.toUnsignedBigInteger(), yBit);
                    // Parse to uncompressed public key without prefix
//                    byte[] encodePub = publicKey.toBytes().toArray();
//                    byte[] encodePub = point.getEncoded(false);
//                    SECPPublicKey publicKey = SECPPublicKey.create(
//                            new BigInteger(1, Arrays.copyOfRange(encodePub, 1, encodePub.length)), Sign.CURVE_NAME);
                    pubKeys.add(publicKey);
                }
                default -> {
                }
                //                    log.debug("no match xdagBlock field type:" + field.getType());
            }
        }
        this.parsed = true;
    }

    public byte[] toBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.write(getEncodedBody());

        for (Signature sig : insigs.keySet()) {
            encoder.writeSignature(BytesUtils.subArray(sig.encodedBytes().toArray(), 0, 64));
        }
        if (outsig != null) {
            encoder.writeSignature(BytesUtils.subArray(outsig.encodedBytes().toArray(), 0, 64));
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

    /**
     * without signature
     */
    private byte[] getEncodedBody() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeField(getEncodedHeader());
        List<Address> all = Lists.newArrayList();
        all.addAll(inputs);
        all.addAll(outputs);
        if(txNonceField != null) {
            encoder.writeField(txNonceField.getData().reverse().toArray());
        }
        for (Address link : all) {
            encoder.writeField(link.getData().reverse().toArray());
        }
        if (info.getRemark() != null) {
            encoder.write(info.getRemark());
        }
        for (PublicKey publicKey : pubKeys) {
            byte[] pubkeyBytes = publicKey.toBytes().toArray();
            byte[] key = BytesUtils.subArray(pubkeyBytes, 1, 32);
            encoder.writeField(key);
        }
        encoded = encoder.toBytes();
        return encoded;
    }

    private byte[] getEncodedHeader() {
        //byte[] fee = BytesUtils.longToBytes(getFee(), true);
        byte[] fee = BytesUtils.longToBytes(Long.parseLong(getFee().toString()), true);
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

    public void signIn(ECKeyPair ecKey) {
        sign(ecKey, XDAG_FIELD_SIGN_IN);
    }

    public void signOut(ECKeyPair ecKey) {
        sign(ecKey, XDAG_FIELD_SIGN_OUT);
    }

    private void sign(ECKeyPair ecKey, XdagField.FieldType type) {
        byte[] encoded = toBytes();
        // log.debug("sign encoded:{}", Hex.toHexString(encoded));
        byte[] pubkeyBytes = ecKey.getPublicKey().toBytes().toArray();
        byte[] digest = BytesUtils.merge(encoded, pubkeyBytes);
        //log.debug("sign digest:{}", Hex.toHexString(digest));
        Bytes32 hash = HashUtils.doubleSha256(Bytes.wrap(digest));
        //log.debug("sign hash:{}", Hex.toHexString(hash.toArray()));
        Signature signature = Signer.sign(hash, ecKey);
        if (type == XDAG_FIELD_SIGN_OUT) {
            outsig = signature;
        } else {
            insigs.put(signature, tempLength);
        }
    }

    /**
     * Only match input signatures and return useful keys
     */
    public List<PublicKey> verifiedKeys() {
        List<PublicKey> keys = getPubKeys();
        List<PublicKey> res = Lists.newArrayList();
        Bytes digest;
        Bytes32 hash;
        for (Signature sig : this.getInsigs().keySet()) {
            digest = getSubRawData(this.getInsigs().get(sig) - 1);
            for (PublicKey publicKey : keys) {
                // TODO: paulochen can this be replaced?
                byte[] pubkeyBytes = publicKey.toBytes().toArray();
                hash = HashUtils.doubleSha256(Bytes.wrap(digest, Bytes.wrap(pubkeyBytes)));
                if (Signer.verify(hash, sig, publicKey)) {
                    res.add(publicKey);
                }
            }
        }
        digest = getSubRawData(getOutsigIndex() - 2);
        for (PublicKey publicKey : keys) {
            // TODO: paulochen can this be replaced?
            byte[] pubkeyBytes = publicKey.toBytes().toArray();
            hash = HashUtils.doubleSha256(Bytes.wrap(digest, Bytes.wrap(pubkeyBytes)));
            if (Signer.verify(hash, this.getOutsig(), publicKey)) {
                res.add(publicKey);
            }
        }
        return res;
    }

    /**
     * Get the field index of output signature
     */
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
            hashLow.set(8, getHash().slice(8, 24));
            info.setHashlow(hashLow.toArray());
        }
        return MutableBytes32.wrap(info.getHashlow());
    }

    public Signature getOutsig() {
        return outsig == null ? null : outsig;
    }

    @Override
    public String toString() {
        return String.format("Block info:[Hash:{%s}][Time:{%s}]", getHashLow().toHexString(),
                Long.toHexString(getTimestamp()));
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
        return Bytes.of(this.getHashLow().toArray()).hashCode();
    }

    public long getTimestamp() {
        return this.info.getTimestamp();
    }

    public long getType() {
        return this.info.type;
    }

    public XAmount getFee() {
        return this.info.getFee();
    }

    /**
     * Get data of first length fields for signing
     */
    public MutableBytes getSubRawData(int length) {
        Bytes data = getXdagBlock().getData();
        MutableBytes res = MutableBytes.create(512);
        res.set(0, data.slice(0, (length + 1) * 32));
        for (int i = length + 1; i < 16; i++) {
            long type = data.getLong(8, ByteOrder.LITTLE_ENDIAN);
            byte typeB = (byte) (type >> (i << 2) & 0xf);
            if (XDAG_FIELD_SIGN_IN.asByte() == typeB || XDAG_FIELD_SIGN_OUT.asByte() == typeB) {
                continue;
            }
            res.set((i) * 32, data.slice((i) * 32, 32));
        }
        return res;
    }

    private void setType(XdagField.FieldType type, int n) {
        long typeByte = type.asByte();
        this.info.type |= typeByte << (n << 2);
    }

    public List<Address> getLinks() {
        List<Address> links = Lists.newArrayList();
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
            log.error(e.getMessage(), e);
        }
        return ano;
    }
}
