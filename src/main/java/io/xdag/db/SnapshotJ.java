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
package io.xdag.db;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import io.xdag.core.*;

import io.xdag.crypto.Hash;
import io.xdag.crypto.Sign;
import io.xdag.db.execption.DeserializationException;
import io.xdag.db.execption.SerializationException;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.core.SnapshotInfo;

import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.KeyPair;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.SECPSignature;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.rocksdb.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.db.BlockStore.*;
import static io.xdag.utils.BasicUtils.compareAmountTo;

@Slf4j
public class SnapshotJ extends RocksdbKVSource {

    public static final Kryo kryo = new Kryo();
    private long ourBalance = 0L;
    private long nextTime = 0;
    private long height = 0;

    static {
        kryoRegister();
    }

    public SnapshotJ(String name) {
        super(name);
    }

    public void makeSnapshot(RocksdbKVSource blockSource, RocksdbKVSource snapshotSource, boolean b) {

        try (RocksIterator iter = getDb().newIterator()) {
            for (iter.seek(new byte[]{HASH_BLOCK_INFO}); iter.key()[0] < 0x40; iter.next()) {
                PreBlockInfo preBlockInfo = new PreBlockInfo();
                BlockInfo blockInfo = new BlockInfo();
                if (iter.value() != null) {
                    try {
                        blockInfo = (BlockInfo) deserialize(iter.value(), BlockInfo.class);
                        if (b) {
                            preBlockInfo = (PreBlockInfo) deserialize(iter.value(), PreBlockInfo.class);
                            blockInfo.setAmount(UInt64.valueOf(preBlockInfo.getAmount()));
                        }
                    } catch (DeserializationException e) {
                        log.error("hash low:{}", Hex.toHexString(blockInfo.getHashlow()));
                        log.error("can't deserialize data:{}", Hex.toHexString(iter.value()));
                        log.error(e.getMessage(), e);
                    }
                    //Has public key or block data
                    if (blockInfo.getSnapshotInfo() != null) {
                        int flag = blockInfo.getFlags();
                        flag &= ~BI_OURS;
                        blockInfo.setFlags(flag);
                        blockInfo.setSnapshot(true);
                        save(iter, blockInfo, snapshotSource);
                    } else { //Storage block data without public key and balance
                        if (compareAmountTo(blockInfo.getAmount(), UInt64.ZERO) != 0) {
//                        if (blockInfo.getAmount() != 0) {
                            blockInfo.setSnapshot(true);
                            blockInfo.setSnapshotInfo(new SnapshotInfo(false, blockSource.get(BytesUtils.subArray(iter.key(), 1, 32))));
                            int flag = blockInfo.getFlags();
                            flag &= ~BI_OURS;
                            blockInfo.setFlags(flag);
                            save(iter, blockInfo, snapshotSource);
                        }
                    }
                }
                if (blockInfo.getHeight() >= height) {
                    height = blockInfo.getHeight();
                    nextTime = blockInfo.getTimestamp();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        byte[] preSeed = this.get(new byte[]{SNAPSHOT_PRESEED});
        snapshotSource.put(new byte[]{SNAPSHOT_PRESEED}, preSeed);
    }

    public void saveSnapshotToIndex(BlockStore blockStore, List<KeyPair> keys,long snapshotTime) {
        try (RocksIterator iter = getDb().newIterator()) {
            for (iter.seekToFirst(); iter.isValid(); iter.next()) {
                if (iter.key()[0] == 0x30) {
                    BlockInfo blockInfo = new BlockInfo();
                    if (iter.value() != null) {
                        try {
                            blockInfo = (BlockInfo) deserialize(iter.value(), BlockInfo.class);
                        } catch (DeserializationException e) {
                            log.error("hash low:" + Hex.toHexString(blockInfo.getHashlow()));
                            log.error("can't deserialize data:{}", Hex.toHexString(iter.value()));
                            log.error(e.getMessage(), e);
                        }
                        int flag = blockInfo.getFlags();
                        int keyIndex = -1;
                        //Determine if it is your own address
                        SnapshotInfo snapshotInfo = blockInfo.getSnapshotInfo();
                        if (blockInfo.getSnapshotInfo() != null) {
                            //public key exists
                            if (snapshotInfo.getType()) {
                                byte[] ecKeyPair = snapshotInfo.getData();
                                byte[] pubKey = Hash.sha256hash160(Bytes.wrap(ecKeyPair));
                                for (int i = 0; i < keys.size(); i++) {
                                    KeyPair key = keys.get(i);
                                    if (Bytes.wrap(key.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true)).compareTo(Bytes.wrap(ecKeyPair)) == 0) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance += blockInfo.getAmount().toLong();
                                        break;
                                    }
                                }
                            } else {    //Verify signature
                                Block block = new Block(new XdagBlock(snapshotInfo.getData()));
                                SECPSignature outSig = block.getOutsig();
                                for (int i = 0; i < keys.size(); i++) {
                                    KeyPair keyPair = keys.get(i);
                                    byte[] publicKeyBytes = keyPair.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true);
                                    Bytes digest = Bytes
                                            .wrap(block.getSubRawData(block.getOutsigIndex() - 2), Bytes.wrap(publicKeyBytes));
                                    Bytes32 hash = Hash.hashTwice(Bytes.wrap(digest));
                                    if (Sign.SECP256K1.verify(hash, Sign.toCanonical(outSig), keyPair.getPublicKey())) {
                                        flag |= BI_OURS;
                                        keyIndex = i;
                                        ourBalance += blockInfo.getAmount().toLong();
                                        break;
                                    }
                                }
                            }
                        }
                        blockInfo.setFlags(flag);
                        if ((flag & BI_OURS) != 0 && keyIndex > -1) {
                            blockStore.saveOurBlock(keyIndex, blockInfo.getHashlow());
                        }
                        blockStore.saveTxHistory(Bytes32.wrap(blockInfo.getHashlow()),Bytes32.wrap(blockInfo.getHashlow()),
                                XdagField.FieldType.XDAG_FIELD_SNAPSHOT,blockInfo.getAmount(),
                                snapshotTime,0,
                                blockInfo.getRemark());
                        blockStore.saveBlockInfo(blockInfo);
                    }
                } else if (iter.key()[0] == (byte) 0x90) {
                    blockStore.savePreSeed(iter.value());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void save(RocksIterator iter, BlockInfo blockInfo, RocksdbKVSource snapshotSource) {
        byte[] value = null;
        try {
            value = serialize(blockInfo);
        } catch (SerializationException e) {
            log.error(e.getMessage(), e);
        }
        snapshotSource.put(iter.key(), value);
    }

    public long getOurBalance() {
        return this.ourBalance;
    }

    public long getNextTime() {
        return nextTime;
    }

    public long getHeight() {
        return height;
    }

    public static Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
        synchronized (kryo) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                final Input input = new Input(inputStream);
                return kryo.readObject(input, type);
            } catch (final IllegalArgumentException | KryoException | NullPointerException exception) {
                log.debug("Deserialize data:{}", Hex.toHexString(bytes));
                throw new DeserializationException(exception.getMessage(), exception);
            }
        }
    }

    public static byte[] serialize(final Object obj) throws SerializationException {
        synchronized (kryo) {
            try {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final Output output = new Output(outputStream);
                kryo.writeObject(output, obj);
                output.flush();
                output.close();
                return outputStream.toByteArray();
            } catch (final IllegalArgumentException | KryoException exception) {
                throw new SerializationException(exception.getMessage(), exception);
            }
        }
    }

    public static void kryoRegister() {
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
//        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
        kryo.register(XdagStats.class);
        kryo.register(XdagTopStatus.class);
        kryo.register(SnapshotInfo.class);
        kryo.register(UInt64.class);
        kryo.register(PreBlockInfo.class);
    }
}
